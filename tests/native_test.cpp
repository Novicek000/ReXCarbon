#include "xdvdfs.h"
#include "viewport.h"
#include <cassert>
#include <cstring>
#include <fcntl.h>
#include <iostream>
#include <stdexcept>
#include <thread>
#include <unistd.h>
#include <vector>

namespace {
void put16(std::vector<uint8_t>& b,size_t p,uint16_t v){b[p]=v&255;b[p+1]=v>>8;}
void put32(std::vector<uint8_t>& b,size_t p,uint32_t v){put16(b,p,v&65535);put16(b,p+2,v>>16);}
std::vector<uint8_t> fixture(uint64_t base=0) {
  std::vector<uint8_t> b(base+100*2048,0);
  uint64_t h=base+32*2048,root=base+40*2048;
  std::memcpy(b.data()+h,"MICROSOFT*XBOX*MEDIA",20);
  std::memcpy(b.data()+h+2028,"MICROSOFT*XBOX*MEDIA",20);
  put32(b,h+20,40);put32(b,h+24,2048);
  put32(b,root+4,50);put32(b,root+8,8);b[root+13]=11;
  std::memcpy(b.data()+root+14,"default.xex",11);
  std::memcpy(b.data()+base+50*2048,"XEX2test",8);
  return b;
}
struct Temp {
  int fd=-1;
  explicit Temp(const std::vector<uint8_t>& b) {
    char name[]="/tmp/rex-xdvdfs-XXXXXX";fd=mkstemp(name);assert(fd>=0);unlink(name);
    size_t pos=0;while(pos<b.size()){ssize_t n=write(fd,b.data()+pos,b.size()-pos);assert(n>0);pos+=n;}
  }
  ~Temp(){if(fd>=0)close(fd);}
};
bool rejects(const std::vector<uint8_t>& b) {
  Temp t(b);try{rexlauncher::DiscImage disc(t.fd);return false;}catch(const std::runtime_error&){return true;}
}
}
int main(int argc,char** argv) {
  {
    Temp t(fixture());rexlauncher::DiscImage disc(t.fd);
    close(t.fd);t.fd=-1; // DiscImage must own its duplicate.
    assert(disc.entries().size()==1);assert(disc.file_size("DEFAULT.XEX")==8);
    assert(disc.file_size("missing")==-1);
    char b[16]{};assert(disc.read("default.xex",4,b,16)==4);assert(std::string(b,4)=="test");
    assert(disc.read("default.xex",8,b,16)==0);assert(disc.read("missing",0,b,1)==-1);
    assert(disc.read("default.xex",0,nullptr,4)==-1);
    std::thread reader([&]{for(int n=0;n<100;n++){char x[4];assert(disc.read("default.xex",0,x,4)==4);assert(!std::memcmp(x,"XEX2",4));}});
    for(int n=0;n<100;n++){char x[4];assert(disc.read("default.xex",4,x,4)==4);assert(!std::memcmp(x,"test",4));}reader.join();
  }
  {Temp t(fixture(0xfb20));rexlauncher::DiscImage disc(t.fd);assert(disc.partition()==0xfb20);}
  {auto b=fixture();b[32*2048]=0;assert(rejects(b));}
  {auto b=fixture();put32(b,40*2048+4,0xffffffff);assert(rejects(b));}
  {auto b=fixture();put16(b,40*2048+2,511);assert(rejects(b));}
  {auto b=fixture();put16(b,40*2048+2,8);put16(b,40*2048+32+2,8);
   put32(b,40*2048+32+4,50);b[40*2048+32+13]=1;b[40*2048+32+14]='x';assert(rejects(b));}
  {auto b=fixture();b[40*2048+13]=2;std::memcpy(b.data()+40*2048+14,"..",2);assert(rejects(b));}
  {auto b=fixture();b[40*2048+12]=0x10;put32(b,40*2048+4,40);put32(b,40*2048+8,2048);assert(rejects(b));}
  {auto b=fixture();b[50*2048]='D';assert(rejects(b));}
  {auto b=fixture();b.resize(100);assert(rejects(b));}
  int pipefd[2];assert(pipe(pipefd)==0);close(pipefd[1]);
  bool pipe_rejected=false;try{rexlauncher::DiscImage d(pipefd[0]);}catch(const std::runtime_error&){pipe_rejected=true;}close(pipefd[0]);assert(pipe_rejected);
  RexVideo v{1280,720,RexScale::Aspect};auto p=viewport(1920,1200,v);
  assert(p.width==1920&&p.height==1080&&p.y==60);
  v.scaling=RexScale::Stretch;p=viewport(1920,1200,v);assert(p.width==1920&&p.height==1200);
  v.scaling=RexScale::Integer;p=viewport(1920,1200,v);assert(p.width==1280&&p.height==720&&p.x==320);
  p=viewport(640,360,v);assert(p.width==640&&p.height==360); // no crop when smaller
  p=viewport(0,0,v);assert(p.width==0&&p.height==0);
  std::cout<<"PASS: XDVDFS validation, ownership, concurrent reads, EOF, viewport modes\n";
  if(argc>1) {
    int fd=open(argv[1],O_RDONLY);if(fd<0)throw std::runtime_error("real ISO open");
    rexlauncher::DiscImage disc(fd);close(fd);std::cout<<disc.describe();
  }
}
