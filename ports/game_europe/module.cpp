// Local Carbon Europe module. Guest functions are generated from user's XEX.
#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <mutex>
#include <stdexcept>
#include <algorithm>
#include <map>
#include <rex/rex_app.h>
#include <rex/cvar.h>
#include <rex/logging.h>
#include <cstdio>
#include <rex/ui/windowed_app_context_sdl.h>
#include <SDL3/SDL_main.h>
#include "rex_android_input.h"
#include "rex_saf_disc.h"
#include "supplemental_indirect.h"
#include "generated/carbon_europe_pch.h"
#include <rex/system/function_dispatcher.h>
#include <array>

extern const rex::PPCImageInfo PPCImageConfig;
extern "C" void sub_824DAC58(PPCContext&,uint8_t*);
extern "C" void sub_825BF858(PPCContext&,uint8_t*);
namespace {
int iso_fd=-1;
std::string failure_path;
void report_failure(const char* message) {
  __android_log_print(ANDROID_LOG_ERROR,"CarbonEurope","%s",message);
  if(!failure_path.empty()) {
    if(FILE* file=std::fopen(failure_path.c_str(),"w")) {
      std::fprintf(file,"%s\n",message);std::fclose(file);
    }
  }
}
std::mutex pad_mutex;
RexPad pad{};
RexHostV1 host{};
// Region-independent recovery used by the upstream Carbon port: register only
// entries whose ORIGINAL instructions prove these exact leaf semantics.
void verified_return(PPCContext&,uint8_t*){}
void verified_member_adapter(PPCContext& ctx,uint8_t* base){
  ctx.r11.u64=ctx.r4.u64;ctx.r4.u64=ctx.r3.u64;
  ctx.r10.u64=REX_LOAD_U32(ctx.r11.u32+8);
  ctx.r9.u64=REX_LOAD_U32(ctx.r11.u32+4);
  ctx.r11.u64=REX_LOAD_U32(ctx.r11.u32);
  ctx.r3.u64=ctx.r10.u64+ctx.r9.u64;ctx.ctr.u64=ctx.r11.u64;
  REX_CALL_INDIRECT_FUNC(ctx.ctr.u32);
}
class CarbonEuropeApp final:public rex::ReXApp {
 public:
  explicit CarbonEuropeApp(rex::ui::WindowedAppContext& ctx):ReXApp(ctx,"carbon_europe",PPCImageConfig){}
  void OnPreSetup(rex::RuntimeConfig& config) override {
    config.gpu_plugin="xenos";
    host.size=sizeof(host);host.version=1;
    host.read_pad=[](void*,RexPad* out){std::lock_guard lock(pad_mutex);*out=pad;};
    config.input_factory=[](bool){return rexlauncher::make_android_input(&host);};
  }
  void OnLoadXexImage(std::string&) override {
    if(iso_fd<0)throw std::runtime_error("Missing SAF ISO descriptor");
    auto* vfs=runtime()->file_system();
    constexpr const char* mount="\\Device\\Cdrom0";
    auto device=rexlauncher::make_saf_disc(mount,iso_fd);
    if(!device->Initialize()||!vfs->RegisterDevice(std::move(device)))throw std::runtime_error("Could not mount Carbon ISO");
    vfs->UnregisterSymbolicLink("game:");vfs->UnregisterSymbolicLink("d:");
    if(!vfs->RegisterSymbolicLink("game:",mount)||!vfs->RegisterSymbolicLink("d:",mount))
      throw std::runtime_error("Alias game:/d:");
    REXLOG_INFO("Carbon Europe 573FDB37: assets mounted from SAF ISO; generated code 0.0.0.4");
  }
  void OnPreLaunchModule() override {
    auto* input=dynamic_cast<rex::input::InputSystem*>(runtime()->input_system());
    if(input){
      rex::input::X_INPUT_STATE state{};
      const auto status=input->GetState(0,&state);
      REXLOG_INFO("Android XInput user 0: status=0x{:08X}, packet={}, buttons=0x{:04X}",
          status,uint32_t(state.packet_number),uint16_t(state.gamepad.buttons));
      __android_log_print(ANDROID_LOG_INFO,"ReXCarbon","Android XInput user 0: status=0x%08X",unsigned(status));
    }else{
      REXLOG_ERROR("Android XInput: unexpected input system");
    }
    auto* dispatcher=runtime()->function_dispatcher();auto* base=runtime()->virtual_membase();
    // Exact original instructions for the indirect entry reached in the crash log.
    constexpr std::array<uint32_t,6> loading_entry={
      0x3d608208,0x38a00000,0x388bb714,0x3d6082c6,0x806b4acc,0x4be231f4};
    for(size_t n=0;n<loading_entry.size();++n)
      if(REX_LOAD_U32(0x824DAC58+uint32_t(n*4))!=loading_entry[n])
        throw std::runtime_error("XEX mismatch at 0x824DAC58");
    if(!dispatcher->GetFunction(0x824DAC58)&&!dispatcher->SetFunction(0x824DAC58,sub_824DAC58))
      throw std::runtime_error("Could not register 0x824DAC58");
    REXLOG_INFO("Verified supplemental PPC entry 0x824DAC58 -> tail 0x822FDE60");
    const auto indirect_entries=RegisterSupplementalIndirectEntries(dispatcher,base);
    REXLOG_INFO("Verified {} supplemental static indirect entries from Europe XEX tables",indirect_entries);
    // Career callback: its pointer is constructed by lis/addi at 0x825BC79C
    // and 0x825BC7AC, rather than stored in a static pointer table.
    constexpr std::array<uint32_t,8> career_entry={
      0x7c6b1b78,0x39200002,0x3d4082ff,0x386aa040,
      0x992b0012,0x39600000,0x91630014,0x4bfff984};
    for(size_t n=0;n<career_entry.size();++n)
      if(REX_LOAD_U32(0x825BF858+uint32_t(n*4))!=career_entry[n])
        throw std::runtime_error("XEX mismatch at career callback 0x825BF858");
    if(!dispatcher->GetFunction(0x825BF858)&&!dispatcher->SetFunction(0x825BF858,sub_825BF858))
      throw std::runtime_error("Could not register career callback 0x825BF858");
    REXLOG_INFO("Verified career PPC entry 0x825BF858 -> tail 0x825BF1F8");
    constexpr std::array<uint32_t,8> adapter={0x7c8b2378,0x7c641b78,0x814b0008,0x812b0004,
                                            0x816b0000,0x7c6a4a14,0x7d6903a6,0x4e800420};
    const uint32_t end=PPCImageConfig.code_base+PPCImageConfig.code_size;
    unsigned returns=0,adapters=0;
    for(uint32_t pc=PPCImageConfig.code_base;pc<end;pc+=4){
      if(dispatcher->GetFunction(pc))continue;
      uint32_t first=REX_LOAD_U32(pc);
      if(first==0x4e800020){
        if(!dispatcher->SetFunction(pc,verified_return))throw std::runtime_error("Register original blr failed");
        ++returns;
      }else if(first==adapter[0]&&uint64_t(pc)+32<=end){
        bool match=true;for(unsigned n=1;n<adapter.size();n++)if(REX_LOAD_U32(pc+n*4)!=adapter[n]){match=false;break;}
        if(match){
          if(!dispatcher->SetFunction(pc,verified_member_adapter))throw std::runtime_error("Register original adapter failed");
          ++adapters;
        }
      }
    }
    REXLOG_INFO("Verified original PPC leaves: {} return entries, {} member adapters",returns,adapters);
    __android_log_print(ANDROID_LOG_INFO,"ReXCarbon",
        "Europe 573FDB37: verified guest dispatch, 340 table entries and career callback 0x825BF858; CPU -O2");
  }
};
}
extern "C" JNIEXPORT void JNICALL Java_pl_rexlauncher_CarbonNative_setLaunchReport(JNIEnv* env,jclass,jstring path){
  const char* text=env->GetStringUTFChars(path,nullptr);if(!text)return;
  failure_path=text;env->ReleaseStringUTFChars(path,text);
}
extern "C" JNIEXPORT void JNICALL Java_pl_rexlauncher_CarbonNative_setIso(JNIEnv* env,jclass,jint fd){
  int next=dup(fd);if(next<0){env->ThrowNew(env->FindClass("java/io/IOException"),"dup ISO");return;}
  if(iso_fd>=0)close(iso_fd);iso_fd=next;
}
extern "C" JNIEXPORT void JNICALL Java_pl_rexlauncher_CarbonNative_setPad(JNIEnv*,jclass,jint b,jint lt,jint rt,jint lx,jint ly,jint rx,jint ry){
  std::lock_guard lock(pad_mutex);
  RexPad next{pad.packet,uint16_t(b&0xf7ff),uint8_t(std::clamp(lt,0,255)),uint8_t(std::clamp(rt,0,255)),
      int16_t(std::clamp(lx,-32768,32767)),int16_t(std::clamp(ly,-32768,32767)),int16_t(std::clamp(rx,-32768,32767)),int16_t(std::clamp(ry,-32768,32767))};
  if(std::memcmp(reinterpret_cast<const char*>(&next)+4,reinterpret_cast<const char*>(&pad)+4,12))++next.packet;
  pad=next;
}
// SDL_main is exported by SDL_main.h for Android.
int main(int argc,char** argv){
  try {
    auto remaining=rex::cvar::Init(argc,argv);rex::cvar::ApplyEnvironment();rex::InitLoggingEarly();
    rex::ui::SDLWindowedAppContext context;if(!context.Initialize()){
      report_failure("Could not initialize SDL window");return 1;
    }
    std::unique_ptr<rex::ui::WindowedApp> app=std::make_unique<CarbonEuropeApp>(context);
    std::map<std::string,std::string> positional;const auto& options=app->GetPositionalOptions();
    for(size_t n=0;n<std::min(remaining.size(),options.size());n++)positional[options[n]]=remaining[n];
    app->SetParsedArguments(std::move(positional));
    if(!app->OnInitialize()){
      report_failure("ReXGlue initialization failed. See Carbon logs for details.");
      app->InvokeOnDestroy();return 1;
    }
    int result=context.RunMainMessageLoop();
    app->InvokeOnDestroy();return result;
  }catch(const std::exception& e){report_failure(e.what());return 1;}
}
