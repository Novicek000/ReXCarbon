// Execute the supplemental generated thunk with a checked replacement tail target.
#include <rex/ppc.h>
#include <cstdio>
#include <cstdlib>
#include <sys/mman.h>

extern "C" void sub_824DAC58(PPCContext&,uint8_t*);
namespace {
bool called=false;
void check(bool value,const char* message){if(!value){std::fprintf(stderr,"FAIL: %s\n",message);std::abort();}}
}
extern "C" void sub_822FDE60(PPCContext& ctx,uint8_t*){
    called=true;
    check(ctx.r3.u64==0xf3215678ULL,"lwz must zero extend the big-endian global");
    check(ctx.r4.s64==int64_t(int32_t(0x82080000u))-18668,"lis/addi sign extension");
    check(ctx.r5.u64==0,"third argument must be zero");
    check(ctx.r11.s64==int64_t(int32_t(0x82c60000u)),"second lis sign extension");
    check(ctx.lr==0x12345678,"tail branch must preserve LR");
    check(ctx.ctr.u64==0x87654321,"tail branch must preserve CTR");
    check(ctx.r6.u64==0xabcdef,"unmodified argument register");
    ctx.r3.u64=0x1234abcd;
}
int main(){
    auto* base=static_cast<uint8_t*>(mmap(nullptr,0x100000000ULL,PROT_READ|PROT_WRITE,
        MAP_PRIVATE|MAP_ANONYMOUS|MAP_NORESERVE,-1,0));
    check(base!=MAP_FAILED,"allocate sparse guest address space");
    base[0x82c64acc]=0xf3;base[0x82c64acd]=0x21;base[0x82c64ace]=0x56;base[0x82c64acf]=0x78;
    PPCContext context{};context.lr=0x12345678;context.ctr.u64=0x87654321;
    context.r5.u64=0xff;context.r6.u64=0xabcdef;
    sub_824DAC58(context,base);
    check(called,"tail target reached");check(context.r3.u64==0x1234abcd,"tail result returned");
    munmap(base,0x100000000ULL);
    std::puts("PASS: generated 0x824DAC58: sign extension, global load, arguments, LR/CTR, tail call and return");
}
