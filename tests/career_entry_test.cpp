// Execute the real SDK-generated career callback, checking its tail-call ABI.
#include <rex/ppc.h>
#include <array>
#include <cstdio>
#include <cstdlib>
#include <sys/mman.h>

extern "C" void sub_825BF858(PPCContext&,uint8_t*);
namespace {
unsigned calls=0;
uint32_t object=0;
constexpr uint32_t global=0x82FEA040;
void check(bool value,const char* message){
    if(!value){std::fprintf(stderr,"FAIL: %s\n",message);std::abort();}
}
void put(uint8_t* base,uint32_t address,uint32_t value){
    for(unsigned i=0;i<4;++i)base[address+i]=uint8_t(value>>(24-8*i));
}
uint32_t get(const uint8_t* base,uint32_t address){
    uint32_t value=0;for(unsigned i=0;i<4;++i)value=(value<<8)|base[address+i];return value;
}
}
extern "C" void sub_825BF1F8(PPCContext& ctx,uint8_t* base){
    ++calls;
    check(ctx.r3.s64==int64_t(int32_t(global)),"global argument sign extension");
    check(ctx.r10.s64==int64_t(int32_t(0x82FF0000u)),"lis sign extension");
    check(ctx.r9.u64==2&&ctx.r11.u64==0,"callback register values");
    check(base[object+0x12]==2,"object flag written before tail call");
    check(base[object+0x11]==0xA5&&base[object+0x13]==0x5A,"stb preserves adjacent bytes");
    check(get(base,global+0x14)==0,"global field cleared before tail call");
    check(get(base,global+0x10)==0x89ABCDEF,"stw preserves preceding word");
    check(get(base,global+0x18)==0x76543210,"stw preserves following word");
    check(ctx.lr==0x12345678,"tail branch preserves LR");
    check(ctx.ctr.u64==0x825BF858,"tail branch preserves CTR");
    check(ctx.r1.u64==0x70000000,"no guest stack frame created");
    check(ctx.r4.u64==0xF123456789ABCDEFULL,"untouched argument preserved");
    check(ctx.r31.u64==0xCAFEBABE,"nonvolatile register preserved");
    ctx.r3.u64=0x1234ABCD;
}
int main(){
    auto* base=static_cast<uint8_t*>(mmap(nullptr,0x100000000ULL,PROT_READ|PROT_WRITE,
        MAP_PRIVATE|MAP_ANONYMOUS|MAP_NORESERVE,-1,0));
    check(base!=MAP_FAILED,"sparse guest address space");
    // Exercise both low and signed-high guest pointers, and initially nonzero state.
    for(uint32_t address:std::array{0x2000u,0x90001000u}){
        object=address;
        base[object+0x11]=0xA5;base[object+0x12]=0xFF;base[object+0x13]=0x5A;
        put(base,global+0x10,0x89ABCDEF);
        put(base,global+0x14,0xDEADBEEF);
        put(base,global+0x18,0x76543210);
        PPCContext context{};
        context.r3.s64=int64_t(int32_t(object));context.r1.u64=0x70000000;
        context.r4.u64=0xF123456789ABCDEFULL;context.r31.u64=0xCAFEBABE;
        context.r9.u64=context.r10.u64=context.r11.u64=~0ULL;
        context.lr=0x12345678;context.ctr.u64=0x825BF858;
        sub_825BF858(context,base);
        check(context.r3.u64==0x1234ABCD,"tail return value propagated");
    }
    check(calls==2,"exactly one tail call per invocation");
    munmap(base,0x100000000ULL);
    std::puts("PASS: career 0x825BF858: two pointers, byte/global stores, sign extension, LR/CTR/stack, tail ABI/result");
}
