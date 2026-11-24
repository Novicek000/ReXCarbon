#pragma once
#include <cstdint>
#include <map>
#include <set>
#include <string>
#include <vector>

namespace rexlauncher {
struct DiscEntry { uint64_t offset, size; };
class DiscImage {
 public:
  explicit DiscImage(int borrowed_fd);
  ~DiscImage();
  DiscImage(const DiscImage&) = delete;
  DiscImage& operator=(const DiscImage&) = delete;
  int fd() const { return fd_; }
  uint64_t size() const { return size_; }
  uint64_t partition() const { return partition_; }
  const std::map<std::string, DiscEntry>& entries() const { return files_; }
  const std::set<std::string>& directories() const { return directories_; }
  int64_t file_size(const std::string& path) const;
  int64_t read(const std::string& path, uint64_t offset, void* out, uint64_t count) const;
  std::string describe() const;
 private:
  int fd_ = -1;
  uint64_t size_ = 0, partition_ = 0, table_bytes_ = 0;
  std::map<std::string, DiscEntry> files_;
  std::set<std::string> directories_;
  void exact(uint64_t offset, void* out, size_t count) const;
  void directory(uint32_t sector, uint32_t size, const std::string& prefix, unsigned depth,
                 std::vector<uint32_t> ancestors);
};
}
