#include "xdvdfs.h"
#include <algorithm>
#include <cerrno>
#include <cstring>
#include <limits>
#include <set>
#include <sstream>
#include <stdexcept>
#include <sys/stat.h>
#include <unistd.h>

namespace rexlauncher {
namespace {
uint16_t le16(const uint8_t* p) { return uint16_t(p[0]) | (uint16_t(p[1]) << 8); }
uint32_t le32(const uint8_t* p) { return uint32_t(le16(p)) | (uint32_t(le16(p+2)) << 16); }
std::string key(std::string path) {
  for (char& c : path) {
    if (c == '\\') c = '/';
    else if (c >= 'A' && c <= 'Z') c = char(c + ('a'-'A'));
  }
  return path;
}
}
void DiscImage::exact(uint64_t offset, void* out, size_t count) const {
  if (offset > size_ || count > size_ - offset) throw std::runtime_error("XDVDFS: zakres poza obrazem ISO");
  auto* cursor = static_cast<uint8_t*>(out);
  while (count) {
    ssize_t n = pread(fd_, cursor, count, static_cast<off_t>(offset));
    if (n < 0 && errno == EINTR) continue;
    if (n <= 0) throw std::runtime_error("XDVDFS: odczyt ISO @" + std::to_string(offset) +
        " (" + (n < 0 ? std::string(std::strerror(errno)) : std::string("end of file")) + ")");
    cursor += n; count -= size_t(n); offset += uint64_t(n);
  }
}
DiscImage::DiscImage(int borrowed_fd) {
  fd_ = dup(borrowed_fd);
  if (fd_ < 0) throw std::runtime_error("SAF: descriptor duplication failed");
  try {
    struct stat st{};
    if (fstat(fd_, &st) != 0) throw std::runtime_error("SAF: fstat ISO");
    off_t end = st.st_size > 0 ? st.st_size : lseek(fd_, 0, SEEK_END);
    if (end <= 0) throw std::runtime_error("SAF: a local seekable ISO is required; select the image in Downloads");
    size_ = static_cast<uint64_t>(end);
    const uint64_t offsets[] = {0, 0xfb20, 0x20600, 0x2080000, 0xfd90000};
    uint8_t header[2048]{};
    bool found = false;
    for (auto base : offsets) {
      if (base + 32*2048 + sizeof(header) > size_) continue;
      exact(base + 32*2048, header, sizeof(header));
      if (!std::memcmp(header, "MICROSOFT*XBOX*MEDIA", 20) &&
          !std::memcmp(header+2028, "MICROSOFT*XBOX*MEDIA", 20)) {
        partition_ = base; found = true; break;
      }
    }
    if (!found) throw std::runtime_error("XDVDFS not found (expected a decrypted XGD/Xbox 360 dump)");
    directory(le32(header+20), le32(header+24), "", 0, {});
    auto xex = files_.find("default.xex");
    if (xex == files_.end() || xex->second.size < 4) throw std::runtime_error("default.xex missing from disc");
    uint8_t magic[4]; exact(xex->second.offset, magic, 4);
    if (std::memcmp(magic, "XEX2", 4)) throw std::runtime_error("default.xex does not have an Xbox 360 XEX2 header");
  } catch (...) { close(fd_); fd_ = -1; throw; }
}
DiscImage::~DiscImage() { if (fd_ >= 0) close(fd_); }
void DiscImage::directory(uint32_t sector, uint32_t bytes, const std::string& prefix,
                          unsigned depth, std::vector<uint32_t> ancestors) {
  if (!bytes) return;
  if (depth > 64 || bytes > 32*1024*1024 || bytes < 14 ||
      table_bytes_ + bytes > 256*1024*1024ULL ||
      std::find(ancestors.begin(), ancestors.end(), sector) != ancestors.end())
    throw std::runtime_error("XDVDFS: corrupt or overly complex directory table");
  ancestors.push_back(sector); table_bytes_ += bytes;
  std::vector<uint8_t> table(bytes);
  exact(partition_ + uint64_t(sector)*2048, table.data(), table.size());
  std::vector<uint32_t> todo{0}; std::set<uint32_t> visited;
  while (!todo.empty()) {
    uint32_t pos = todo.back(); todo.pop_back();
    if (!visited.insert(pos).second || pos > bytes-14 || files_.size() >= 500000)
      throw std::runtime_error("XDVDFS: directory cycle or invalid node");
    const uint8_t* p = table.data() + pos;
    uint32_t len = p[13];
    if (!len || len > bytes-pos-14) throw std::runtime_error("XDVDFS: invalid name");
    std::string name(reinterpret_cast<const char*>(p+14), len);
    if (name == "." || name == ".." || name.find_first_of("/\\:") != std::string::npos ||
        name.find('\0') != std::string::npos) throw std::runtime_error("XDVDFS: invalid path");
    std::string path = prefix + name;
    uint32_t child_sector = le32(p+4), child_size = le32(p+8);
    if (p[12] & 0x10) {
      if (files_.contains(key(path)) || !directories_.insert(key(path)).second)
        throw std::runtime_error("XDVDFS: duplicate directory");
      directory(child_sector, child_size, path+"/", depth+1, ancestors);
    } else {
      uint64_t offset = partition_ + uint64_t(child_sector)*2048;
      if (offset > size_ || child_size > size_-offset || directories_.contains(key(path)) ||
          !files_.emplace(key(path), DiscEntry{offset,child_size}).second)
        throw std::runtime_error("XDVDFS: invalid range or duplicate file");
    }
    if (le16(p)) todo.push_back(uint32_t(le16(p))*4);
    if (le16(p+2)) todo.push_back(uint32_t(le16(p+2))*4);
  }
}
int64_t DiscImage::file_size(const std::string& path) const {
  auto it = files_.find(key(path));
  return it == files_.end() ? -1 : int64_t(it->second.size);
}
int64_t DiscImage::read(const std::string& path, uint64_t offset, void* out, uint64_t count) const {
  auto it = files_.find(key(path));
  if (it == files_.end()) return -1;
  if (offset >= it->second.size) return 0;
  uint64_t size = std::min(count, it->second.size-offset);
  if (size > size_t(std::numeric_limits<ssize_t>::max()) || (!out && size)) return -1;
  exact(it->second.offset+offset,out,static_cast<size_t>(size));return int64_t(size);
}
std::string DiscImage::describe() const {
  std::ostringstream out;
  out << "XDVDFS OK · " << files_.size() << " files\nISO: " << size_
      << " B · partition 0x" << std::hex << partition_ << std::dec << "\n";
  unsigned count=0;
  for (const auto& [name,entry] : files_) {
    if (++count > 12) {out << "…\n"; break;}
    out << name << " (" << entry.size << " B)\n";
  }
  return out.str();
}
}
