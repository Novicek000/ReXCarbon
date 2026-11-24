#include "rex_saf_disc.h"
#include "xdvdfs.h"
#include <rex/filesystem/file.h>
#include <cassert>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fcntl.h>
#include <iostream>
#include <thread>
#include <sys/mman.h>
#include <sys/resource.h>
#include <unistd.h>
#include <vector>

#undef assert
#define assert(condition) do { if(!(condition)) { \
  std::fprintf(stderr,"FAIL line %d: %s\n",__LINE__,#condition);std::abort(); \
} } while(0)

using rex::X_STATUS;
namespace fs = rex::filesystem;
namespace {
void put16(std::vector<uint8_t>& b,size_t p,uint16_t v){b[p]=v&255;b[p+1]=v>>8;}
void put32(std::vector<uint8_t>& b,size_t p,uint32_t v){put16(b,p,v&65535);put16(b,p+2,v>>16);}
int fixture(){
  std::vector<uint8_t> b(100*2048);
  size_t h=32*2048,root=40*2048,dir=50*2048;
  std::memcpy(b.data()+h,"MICROSOFT*XBOX*MEDIA",20);
  std::memcpy(b.data()+h+2028,"MICROSOFT*XBOX*MEDIA",20);
  put32(b,h+20,40);put32(b,h+24,2048);
  put16(b,root+2,8);put32(b,root+4,60);put32(b,root+8,8);b[root+13]=11;
  std::memcpy(b.data()+root+14,"default.xex",11);
  size_t node=root+32;
  put16(b,node+2,16);put32(b,node+4,50);put32(b,node+8,2048);b[node+12]=0x10;b[node+13]=6;
  std::memcpy(b.data()+node+14,"ASSETS",6);
  node=root+64;b[node+12]=0x10;b[node+13]=5;std::memcpy(b.data()+node+14,"EMPTY",5);
  put32(b,dir+4,70);put32(b,dir+8,8);b[dir+13]=8;std::memcpy(b.data()+dir+14,"Data.BIN",8);
  std::memcpy(b.data()+60*2048,"XEX2test",8);std::memcpy(b.data()+70*2048,"asset123",8);
  char name[]="/tmp/rex-saf-XXXXXX";int fd=mkstemp(name);assert(fd>=0);unlink(name);
  size_t pos=0;while(pos<b.size()){ssize_t n=write(fd,b.data()+pos,b.size()-pos);assert(n>0);pos+=n;}
  return fd;
}
void read_entry(fs::Entry* entry,const char* expected){
  fs::File* file=nullptr;assert(entry);
  assert(entry->Open(fs::FileAccess::kFileReadData,&file)==X_STATUS_SUCCESS);
  uint8_t bytes[16]{};size_t n=99;
  assert(file->ReadSync(bytes,0,&n)==X_STATUS_SUCCESS&&n==8);
  assert(!std::memcmp(bytes,expected,8));
  assert(file->ReadSync(bytes,8,&n)==X_STATUS_END_OF_FILE&&n==0);
  assert(file->ReadSync(std::span<uint8_t>(bytes,0),8,&n)==X_STATUS_SUCCESS&&n==0);
  assert(file->ReadSync(bytes,0,nullptr)==X_STATUS_INVALID_PARAMETER);
  assert(file->WriteSync(bytes,0,&n)==X_STATUS_ACCESS_DENIED&&n==0);
  file->Destroy();
  assert(entry->Open(fs::FileAccess::kFileWriteData,&file)==X_STATUS_ACCESS_DENIED&&!file);
  auto mapped=entry->OpenMapped(rex::memory::MappedMemory::Mode::kRead,4,100);
  assert(mapped&&mapped->size()==4&&!std::memcmp(mapped->data(),expected+4,4));
  assert(!entry->OpenMapped(rex::memory::MappedMemory::Mode::kRead,8,1));
  assert(!entry->OpenMapped(rex::memory::MappedMemory::Mode::kReadWrite,0,8));
  mapped->Close();assert(mapped->data()==nullptr&&mapped->size()==0);
}
size_t sample_files(fs::Entry* entry,const rexlauncher::DiscImage& source){
  if(entry->attributes()&fs::kFileAttributeDirectory){
    size_t files=0;for(const auto& child:entry->children())files+=sample_files(child.get(),source);
    return files;
  }
  assert(source.file_size(entry->path())==int64_t(entry->size()));
  fs::File* file=nullptr;assert(entry->Open(fs::FileAccess::kFileReadData,&file)==X_STATUS_SUCCESS);
  for(size_t offset:{size_t(0),entry->size()>32?entry->size()-32:size_t(0)}){
    uint8_t expected[32]{},actual[32]{};size_t n=0;
    int64_t count=source.read(entry->path(),offset,expected,sizeof(expected));assert(count>=0);
    X_STATUS status=file->ReadSync(actual,offset,&n);
    assert(status==(count?X_STATUS_SUCCESS:X_STATUS_END_OF_FILE));
    assert(n==size_t(count)&&!std::memcmp(actual,expected,n));
    if(count){
      auto mapped=entry->OpenMapped(rex::memory::MappedMemory::Mode::kRead,offset,32);
      assert(mapped&&mapped->size()==size_t(count)&&!std::memcmp(mapped->data(),expected,n));
    }
  }
  file->Destroy();return 1;
}
}
int main(int argc,char** argv){
  int fd=fixture();auto device=rexlauncher::make_saf_disc("\\Device\\Cdrom0",fd);close(fd);
  // No pathname exists and the original provider descriptor is already closed.
  assert(device->Initialize());
  auto* root=device->ResolvePath("");assert(root&&root->child_count()==3);
  assert(device->ResolvePath("EMPTY")->child_count()==0);
  assert(!device->ResolvePath("missing"));
  read_entry(device->ResolvePath("DEFAULT.XEX"),"XEX2test");
  read_entry(device->ResolvePath("assets\\DATA.bin"),"asset123");
  std::thread worker([&]{for(int i=0;i<100;++i)read_entry(device->ResolvePath("assets/data.bin"),"asset123");});
  for(int i=0;i<100;++i)read_entry(device->ResolvePath("default.xex"),"XEX2test");worker.join();
  // A mapped file must remain valid after the VFS device has gone away.
  auto mapped=device->ResolvePath("default.xex")->OpenMapped(rex::memory::MappedMemory::Mode::kRead,0,0);
  device.reset();assert(mapped&&mapped->size()==8&&!std::memcmp(mapped->data(),"XEX2test",8));
  std::cout<<"PASS: real SDK VFS, pathless FD, closed original FD, nested/empty directories, case folding, concurrent reads, EOF, read-only, bounded mappings\n";
  if(argc>1){
    fd=open(argv[1],O_RDONLY);assert(fd>=0);
    // Regression: mounting must succeed even when a whole-disc mmap cannot fit.
    struct rlimit limit{};assert(getrlimit(RLIMIT_AS,&limit)==0);
    // Android's allocator reserves a large virtual arena before main().
    FILE* statm=std::fopen("/proc/self/statm","r");assert(statm);
    unsigned long long pages=0;assert(std::fscanf(statm,"%llu",&pages)==1);std::fclose(statm);
    limit.rlim_cur=pages*sysconf(_SC_PAGESIZE)+256*1024*1024;assert(setrlimit(RLIMIT_AS,&limit)==0);
    rexlauncher::DiscImage source(fd);
    void* whole=mmap(nullptr,source.size(),PROT_READ,MAP_PRIVATE,fd,0);assert(whole==MAP_FAILED);
    device=rexlauncher::make_saf_disc("\\Device\\Cdrom0",fd);close(fd);assert(device->Initialize());
    auto* xex=device->ResolvePath("DEFAULT.XEX");assert(xex&&xex->size()==4857856);
    mapped=xex->OpenMapped(rex::memory::MappedMemory::Mode::kRead,0,0);
    assert(mapped&&mapped->size()==xex->size()&&!std::memcmp(mapped->data(),"XEX2",4));
    std::cout<<"PASS: Carbon ISO -> SDK VFS -> default.xex (4857856 bytes)\n";
    assert(sample_files(device->ResolvePath(""),source)==67);
    std::cout<<"PASS: all 67 Carbon files: head/tail pread + unaligned mapped ranges; whole-ISO mapping denied with 256 MiB spare address space\n";
  }
}
