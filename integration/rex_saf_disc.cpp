#include "rex_saf_disc.h"
#include "xdvdfs.h"
#include <rex/filesystem/file.h>
#include <rex/memory/mapped_memory.h>
#include <algorithm>
#include <cerrno>
#include <cstdio>
#include <cstring>
#include <limits>
#include <stdexcept>
#include <sys/mman.h>
#include <unistd.h>
#include <vector>

namespace rexlauncher {
namespace {
namespace fs = rex::filesystem;
using rex::X_STATUS;

// Map only the requested file range, directly from the granted descriptor.
// Providers that reject mmap can still supply the small bootstrap through pread.
class SafMappedFile final : public rex::memory::MappedMemory {
 public:
  SafMappedFile(const DiscImage& disc, const std::string& path, uint64_t file_offset,
                size_t offset, size_t length) {
    const long page = sysconf(_SC_PAGESIZE);
    if(page <= 0) throw std::runtime_error("SAF: nieznany rozmiar strony pamieci");
    const uint64_t absolute = file_offset + offset;
    const size_t delta = absolute % uint64_t(page);
    if(length > std::numeric_limits<size_t>::max() - delta)
      throw std::runtime_error("SAF: zbyt duzy zakres mapowania");
    mapped_size_ = length + delta;
    mapping_ = mmap(nullptr, mapped_size_, PROT_READ, MAP_PRIVATE, disc.fd(), off_t(absolute - delta));
    if(mapping_ != MAP_FAILED) {
      data_ = static_cast<uint8_t*>(mapping_) + delta; size_ = length; return;
    }
    const int map_error = errno;
    if(length > 64 * 1024 * 1024)
      throw std::runtime_error("SAF: mmap " + path + ": " + std::strerror(map_error));
    std::fprintf(stderr, "SAF mmap %s: %s; fallback to pread (%zu bytes)\n",
        path.c_str(), std::strerror(map_error), length);
    bytes_.resize(length);
    if (disc.read(path, offset, bytes_.data(), length) != int64_t(length))
      throw std::runtime_error("SAF: niepelny odczyt mapowanego pliku " + path);
    data_ = bytes_.data(); size_ = length;
  }
  ~SafMappedFile() override { Close(); }
  void Close(uint64_t = 0) override {
    if(mapping_ != MAP_FAILED) { munmap(mapping_, mapped_size_); mapping_ = MAP_FAILED; }
    data_ = nullptr; size_ = 0; std::vector<uint8_t>().swap(bytes_);
  }
 private:
  void* mapping_ = MAP_FAILED;
  size_t mapped_size_ = 0;
  std::vector<uint8_t> bytes_;
};

class SafFile final : public fs::File {
 public:
  SafFile(uint32_t access, fs::Entry* entry, std::shared_ptr<DiscImage> disc,
          std::string path) : File(access, entry), disc_(std::move(disc)), path_(std::move(path)) {}
  void Destroy() override { delete this; }
  X_STATUS ReadSync(std::span<uint8_t> buffer, size_t offset, size_t* read) override {
    if (!read) return X_STATUS_INVALID_PARAMETER;
    *read = 0;
    if (buffer.empty()) return X_STATUS_SUCCESS;
    try {
      const int64_t n = disc_->read(path_, offset, buffer.data(), buffer.size());
      if (n < 0) return X_STATUS_UNSUCCESSFUL;
      *read = size_t(n);
      return n ? X_STATUS_SUCCESS : X_STATUS_END_OF_FILE;
    } catch (const std::exception& e) {
      std::fprintf(stderr, "SAF ReadSync %s at %zu: %s\n", path_.c_str(), offset, e.what());
      return X_STATUS_UNSUCCESSFUL;
    }
  }
  X_STATUS WriteSync(std::span<const uint8_t>, size_t, size_t* written) override {
    if (written) *written = 0;
    return X_STATUS_ACCESS_DENIED;
  }
 private:
  std::shared_ptr<DiscImage> disc_;
  std::string path_;
};

class SafEntry final : public fs::Entry {
 public:
  SafEntry(fs::Device* device, fs::Entry* parent, const std::string& path,
           std::shared_ptr<DiscImage> disc, bool directory, uint64_t size = 0, uint64_t disc_offset = 0)
      : Entry(device, parent, path), disc_(std::move(disc)), disc_offset_(disc_offset) {
    attributes_ = fs::kFileAttributeReadOnly |
        (directory ? fs::kFileAttributeDirectory : fs::kFileAttributeNormal);
    size_ = size; allocation_size_ = (size + 2047) & ~size_t(2047);
    create_timestamp_ = access_timestamp_ = write_timestamp_ = 116444736000000000ULL;
  }
  void Append(std::unique_ptr<SafEntry> entry) { children_.push_back(std::move(entry)); }
  X_STATUS Open(uint32_t access, fs::File** file) override {
    if (!file) return X_STATUS_INVALID_PARAMETER;
    *file = nullptr;
    constexpr uint32_t writes = fs::FileAccess::kGenericWrite | fs::FileAccess::kGenericAll |
        fs::FileAccess::kFileWriteData | fs::FileAccess::kFileAppendData;
    if (access & writes) return X_STATUS_ACCESS_DENIED;
    *file = new SafFile(access, this, disc_, path_);
    return X_STATUS_SUCCESS;
  }
  bool can_map() const override { return !(attributes_ & fs::kFileAttributeDirectory); }
  std::unique_ptr<rex::memory::MappedMemory> OpenMapped(
      rex::memory::MappedMemory::Mode mode, size_t offset, size_t length) override {
    if (mode != rex::memory::MappedMemory::Mode::kRead || !can_map() || offset >= size_)
      return nullptr;
    const size_t count = length ? std::min(length, size_ - offset) : size_ - offset;
    try { return std::make_unique<SafMappedFile>(*disc_, path_, disc_offset_, offset, count); }
    catch (const std::exception& e) {
      std::fprintf(stderr, "SAF OpenMapped %s: %s\n", path_.c_str(), e.what());
      return nullptr;
    }
  }
 private:
  std::shared_ptr<DiscImage> disc_;
  uint64_t disc_offset_;
};

class SafDiscDevice final : public fs::Device {
 public:
  SafDiscDevice(std::string_view mount, int fd)
      : Device(mount), disc_(std::make_shared<DiscImage>(fd)) {}
  bool Initialize() override {
    auto root = std::make_unique<SafEntry>(this, nullptr, "", disc_, true);
    // The parsed names are ASCII-folded; SDK lookup remains case insensitive.
    auto append = [&](const std::string& path, bool directory, uint64_t size, uint64_t disc_offset) {
      SafEntry* parent = root.get();
      size_t begin = 0;
      for (;;) {
        const size_t end = path.find('/', begin);
        if (end == std::string::npos) {
          std::string guest = path;
          std::replace(guest.begin(), guest.end(), '/', '\\');
          parent->Append(std::make_unique<SafEntry>(this, parent, guest, disc_, directory, size, disc_offset));
          break;
        }
        auto* child = parent->GetChild(std::string_view(path).substr(begin, end - begin));
        if (!child) {
          std::string guest = path.substr(0, end);
          std::replace(guest.begin(), guest.end(), '/', '\\');
          auto entry = std::make_unique<SafEntry>(this, parent, guest, disc_, true);
          child = entry.get(); parent->Append(std::move(entry));
        }
        parent = static_cast<SafEntry*>(child); begin = end + 1;
      }
    };
    for (const auto& path : disc_->directories()) append(path, true, 0, 0);
    for (const auto& [path, info] : disc_->entries()) append(path, false, info.size, info.offset);
    root_ = std::move(root);
    std::fprintf(stderr, "SAF ISO mounted with pread: %zu files, %llu bytes, partition 0x%llx\n",
        disc_->entries().size(), (unsigned long long)disc_->size(),
        (unsigned long long)disc_->partition());
    return true;
  }
  fs::Entry* ResolvePath(std::string_view path) override {
    return root_ ? root_->ResolvePath(path) : nullptr;
  }
  void Dump(rex::string::StringBuffer* buffer) override {
    buffer->Append("SAF/pread ISO: "); buffer->Append(mount_path_); buffer->Append('\n');
  }
  const std::string& name() const override { return name_; }
  uint32_t attributes() const override { return 0; }
  uint32_t component_name_max_length() const override { return 255; }
  uint32_t total_allocation_units() const override { return uint32_t(disc_->size() / 2048); }
  uint32_t available_allocation_units() const override { return 0; }
  uint32_t sectors_per_allocation_unit() const override { return 1; }
  uint32_t bytes_per_sector() const override { return 2048; }
 private:
  std::string name_ = "GDFX";
  std::shared_ptr<DiscImage> disc_;
  std::unique_ptr<SafEntry> root_;
};
}
std::unique_ptr<fs::Device> make_saf_disc(std::string_view mount, int fd) {
  return std::make_unique<SafDiscDevice>(mount, fd);
}
}
