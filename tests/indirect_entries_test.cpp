#include <rex/ppc.h>
#include <cstdio>
#include <cstdlib>
#include <sys/mman.h>

extern "C" void sub_82322188(PPCContext&,uint8_t*);
extern "C" void sub_822965A8(PPCContext&,uint8_t*);
extern "C" void sub_822A95D0(PPCContext&,uint8_t*);
extern "C" void sub_827A919C(PPCContext&,uint8_t*);
extern "C" void sub_827A9214(PPCContext&,uint8_t*);
namespace {
uint32_t expected_target,expected_object;bool called=false;
int loop_cleanup=0,block_cleanup=0;bool restored=false;
void check(bool value,const char* message){if(!value){std::fprintf(stderr,"FAIL: %s\n",message);std::abort();}}
void put(uint8_t* base,uint32_t address,uint32_t value){for(int i=0;i<4;++i)base[address+i]=uint8_t(value>>(24-8*i));}
uint32_t get(const uint8_t* base,uint32_t address){uint32_t value=0;for(int i=0;i<4;++i)value=(value<<8)|base[address+i];return value;}
void target(PPCContext& ctx,uint8_t*){
  check(ctx.r3.u32==expected_object,"virtual dispatch object");
  check(ctx.ctr.u32==expected_target,"virtual dispatch CTR");
  check(ctx.lr==0x12345678,"tail dispatch LR");
  check(ctx.r4.u32==0x8765,"unmodified argument");called=true;ctx.r3.u64=0xaabbccdd;
}
}
extern "C" void sub_827995E8(PPCContext&,uint8_t*){check(false,"unexpected loop body");}
extern "C" void sub_8279A540(PPCContext&,uint8_t*){check(false,"unexpected resource call");}
extern "C" void sub_827A9294(PPCContext& ctx,uint8_t*){
  check(ctx.r12.u32==0x7080,"loop cleanup frame");++loop_cleanup;
}
extern "C" void sub_827A9244(PPCContext& ctx,uint8_t*){
  check(ctx.r12.u32==0x7080,"block cleanup frame");++block_cleanup;
}
extern "C" void __restgprlr_28(PPCContext& ctx,uint8_t*){
  check(ctx.r1.u32==0x7080&&ctx.r31.u32==0x7000,"block entry skips guest prologue and restores original frame");
  restored=true;
}
namespace rex::runtime {
PPCFunc* ResolveIndirectFunction(uint32_t address){
  check(address==expected_target,"virtual dispatch destination");return target;
}
}
int main(){
  auto* base=static_cast<uint8_t*>(mmap(nullptr,0x100000000ULL,PROT_READ|PROT_WRITE,
      MAP_PRIVATE|MAP_ANONYMOUS|MAP_NORESERVE,-1,0));check(base!=MAP_FAILED,"guest memory");
  constexpr uint32_t object=0x1000,child=0x2000,table=0x3000,child_table=0x4000;
  put(base,object,table);put(base,child,child_table);
  put(base,table+0x68,0x82200000);put(base,child_table+4,0x82200004);
  for(int variant=0;variant<4;++variant){
    put(base,object+0xb0,variant?child:0);
    put(base,child+0x18,variant==1?0xffffffff:variant==2?0:3);
    expected_target=variant<2?0x82200000:0x82200004;
    expected_object=variant<2?object:child;called=false;
    PPCContext ctx{};ctx.r3.u64=object;ctx.r4.u64=0x8765;ctx.lr=0x12345678;
    sub_82322188(ctx,base);check(called,"virtual tail called");check(ctx.r3.u64==0xaabbccdd,"virtual result");
  }
  for(uint32_t state:{0u,1u,2u,3u,0xffffffffu}){
    PPCContext ctx{};ctx.r4.u64=state;sub_822965A8(ctx,base);
    const int64_t expected=state==0?1:state==1?12:state==2?13:-1;
    check(ctx.r3.s64==expected,"resource state return");
  }
  for(uint32_t state:{0u,2u,3u,4u,5u,12u,99u})for(uint32_t resource_flag:{0u,1u}){
    put(base,child+0x14,state);put(base,child+0x10,resource_flag);
    put(base,object+0x14,0xffffffff);put(base,0x82ba200c,0x11223344);put(base,0x82ba2010,0x55667788);
    PPCContext ctx{};ctx.r3.u64=object;ctx.r4.u64=child;sub_822A95D0(ctx,base);
    bool accepted=state==2||state==3||state==4||state==5||state==12;
    check(get(base,object+0x14)==(accepted?child:0),"resource type filter");
    check(get(base,object+0x10)==child,"resource pointer");
    check(get(base,0x82ba200c)==(resource_flag?0x11223344:0),"first global resource slot");
    check(get(base,0x82ba2010)==(resource_flag?0x55667788:0),"second global resource slot");
  }
  for(bool through_tail:{false,true}){
    loop_cleanup=block_cleanup=0;restored=false;
    put(base,0x8000,5);put(base,0x7054,42);
    PPCContext ctx{};ctx.r1.u64=0xb000;ctx.r31.u64=0x7000;ctx.r10.u64=0x8000;
    ctx.r28.u64=through_tail?4:5;ctx.lr=0x12345678;
    if(through_tail)sub_827A9214(ctx,base);else sub_827A919C(ctx,base);
    check(restored&&block_cleanup==1,"block exit cleanup");
    check(loop_cleanup==(through_tail?1:0),"computed-entry cleanup");
    check(ctx.r3.u32==42&&get(base,0x7050)==5,"block-entry result and saved induction variable");
  }
  munmap(base,0x100000000ULL);
  std::puts("PASS: generated 0x82322188 virtual dispatch (4 paths), 0x822965A8 (5 states), 0x822A95D0 (14 resource cases)");
  std::puts("PASS: original generated block entry 0x827A919C, direct and via tail 0x827A9214; guest prologue skipped, frame and result preserved");
}
