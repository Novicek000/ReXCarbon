#include "game_api.h"
#include "gpu.h"
#include "viewport.h"
#include "xdvdfs.h"
#include <android/log.h>
#include <android/native_window_jni.h>
#include <jni.h>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdio>
#include <cstring>
#include <fcntl.h>
#include <map>
#include <memory>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <thread>
#include <unistd.h>

#ifndef REX_HAS_GAME
#define REX_HAS_GAME 0
#endif
namespace {
std::string log_root,native_libs,cache_root;
std::mutex log_mutex;
int log_fd=-1;
void log_message(int priority,const char* tag,const char* text) {
  if(!tag)tag="ReX";if(!text)text="";
  __android_log_write(priority,tag,text);
  std::lock_guard lock(log_mutex);
  if(log_fd>=0) {
    std::string line=std::to_string(std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::system_clock::now().time_since_epoch()).count())+" ["+tag+"] "+text+"\n";
    const char* p=line.data();size_t left=line.size();
    while(left){ssize_t n=write(log_fd,p,left);if(n<0&&errno==EINTR)continue;if(n<=0)break;p+=n;left-=n;}
  }
}
std::string utf(JNIEnv* env,jstring s) {
  if(!s)return {};
  const char* chars=env->GetStringUTFChars(s,nullptr);
  if(!chars)throw std::runtime_error("JNI: text allocation failed");
  std::string copy(chars);env->ReleaseStringUTFChars(s,chars);return copy;
}
// NewStringUTF uses Modified UTF-8. Convert UTF-8 explicitly for Polish text
// and filenames from ISO rather than handing arbitrary bytes to CheckJNI.
jstring string(JNIEnv* env,const std::string& text) {
  std::u16string out;
  for(size_t i=0;i<text.size();) {
    uint32_t c=static_cast<unsigned char>(text[i++]);
    if(c>=0x80) {
      unsigned extra=c>=0xf0?3:c>=0xe0?2:c>=0xc2?1:0;
      uint32_t cp=extra?c&((1u<<(6-extra))-1):0xfffd;
      if(!extra||i+extra>text.size()){out.push_back(u'\ufffd');continue;}
      bool valid=true;
      for(unsigned n=0;n<extra;n++) {
        unsigned char b=text[i+n];if((b&0xc0)!=0x80){valid=false;break;}cp=(cp<<6)|(b&63);
      }
      if(!valid){out.push_back(u'\ufffd');continue;}
      i+=extra;c=cp;
      if((extra==1&&c<0x80)||(extra==2&&c<0x800)||(extra==3&&c<0x10000)||c>0x10ffff||(c>=0xd800&&c<=0xdfff))c=0xfffd;
    }
    if(c<=0xffff)out.push_back(char16_t(c));
    else{c-=0x10000;out.push_back(char16_t(0xd800+(c>>10)));out.push_back(char16_t(0xdc00+(c&1023)));}
  }
  return env->NewString(reinterpret_cast<const jchar*>(out.data()),jsize(out.size()));
}
const RexGameV1* game_api() {
#if REX_HAS_GAME
  const RexGameV1* api=rex_game_api_v1();
  if(api&&api->version==1&&api->size>=sizeof(RexGameV1)&&api->create&&api->surface&&api->pause&&api->pump&&api->destroy)return api;
#endif
  return nullptr;
}
struct Session {
  int fd;
  std::string driver_dir,driver_name,user_root;
  RexVideo video;
  bool diagnostic;
  std::mutex mutex;
  std::condition_variable wake;
  bool stopping=false,paused=true,pause_dirty=true,surface_dirty=false;
  ANativeWindow* pending_window=nullptr;
  uint32_t pending_w=0,pending_h=0;
  RexPad pad{};
  std::string message="Opening XDVDFS…";
  std::thread thread;
  rexlauncher::DiscImage* disc=nullptr; // worker only; callbacks valid while game lives

  Session(int borrowed,std::string dir,std::string name,std::string user,RexVideo v,bool diag)
      :fd(dup(borrowed)),driver_dir(std::move(dir)),driver_name(std::move(name)),
       user_root(std::move(user)),video(v),diagnostic(diag) {
    if(fd<0)throw std::runtime_error("ISO descriptor duplication failed");
  }
  ~Session(){stop();if(pending_window)ANativeWindow_release(pending_window);close(fd);}
  void stop(){{std::lock_guard lock(mutex);stopping=true;}wake.notify_all();if(thread.joinable())thread.join();}
  void set_message(std::string text) {
    log_message(ANDROID_LOG_INFO,"ReXSession",text.c_str());
    std::lock_guard lock(mutex);message=std::move(text);
  }
  static int64_t asset_size(void* ctx,const char* path) {
    try {auto* self=static_cast<Session*>(ctx);return path?self->disc->file_size(path):-1;}
    catch(...){return -1;}
  }
  static int64_t asset_read(void* ctx,const char* path,uint64_t offset,void* out,uint64_t size) {
    try {auto* self=static_cast<Session*>(ctx);return path?self->disc->read(path,offset,out,size):-1;}
    catch(const std::exception& e){log_message(ANDROID_LOG_ERROR,"ReXVFS",e.what());return -1;}
  }
  static void read_pad(void* ctx,RexPad* out) {
    if(!out)return;auto* self=static_cast<Session*>(ctx);std::lock_guard lock(self->mutex);*out=self->pad;
  }
  void run() noexcept {
    const RexGameV1* api=nullptr;void* game=nullptr;ANativeWindow* window=nullptr;
    // The host and strings must outlive destroy(), including exception paths.
    std::unique_ptr<rexlauncher::DiscImage> image;
    RexHostV1 host{};
    try {
      image=std::make_unique<rexlauncher::DiscImage>(fd);disc=image.get();
      set_message(image->describe());
      {std::lock_guard lock(mutex);if(stopping)return;}
      auto get=open_vulkan(driver_dir,driver_name,native_libs,cache_root);
      std::string gpu=probe_vulkan(get);
      log_message(ANDROID_LOG_INFO,"ReXVulkan",gpu.c_str());
      if(!diagnostic) {
        api=game_api();
        if(!api)throw std::runtime_error("No compiled ARM64 game module and Android runtime in APK");
        host={sizeof(RexHostV1),1,this,image->fd(),user_root.c_str(),log_root.c_str(),get,
              asset_size,asset_read,read_pad,log_message};
        char error[1024]{};
        game=api->create(&host,&video,error,sizeof(error));
        error[sizeof(error)-1]=0;
        if(!game)throw std::runtime_error(error[0]?error:"Game module initialization failed");
        set_message(std::string(api->name?api->name:"ReXGlue")+"\n"+gpu);
      } else {
        set_message("DIAGNOSTICS · "+std::to_string(image->entries().size())+" XDVDFS files\n"+gpu+
                    "Input test below. Game is not running.");
      }
      while(true) {
        ANativeWindow* next=nullptr;bool changed=false,change_pause=false,is_paused=true;
        uint32_t w=0,h=0;
        {
          std::unique_lock lock(mutex);
          wake.wait_for(lock,std::chrono::milliseconds(16),[this]{return stopping||surface_dirty||pause_dirty;});
          if(stopping)break;
          changed=surface_dirty;surface_dirty=false;
          if(changed){next=pending_window;pending_window=nullptr;w=pending_w;h=pending_h;}
          change_pause=pause_dirty;pause_dirty=false;is_paused=paused;
        }
        if(changed) {
          // Transfer ownership before invoking game code so exceptions cannot leak next.
          ANativeWindow* old=window;window=next;
          try{if(game)api->surface(game,window,w,h,viewport(w,h,video));}
          catch(...){if(old)ANativeWindow_release(old);throw;}
          if(old)ANativeWindow_release(old);
        }
        if(game&&change_pause)api->pause(game,is_paused);
        if(game) {
          int result=api->pump(game);
          if(result<=0){set_message(result==0?"Game session ended":"Game module error; export logs");break;}
        }
      }
    } catch(const std::exception& e){set_message(std::string("Error: ")+e.what());}
      catch(...){set_message("Unknown native error; export logs");}
    // No exception may cross JNI or escape std::thread.
    if(game) {
      try{api->destroy(game);}
      catch(...){log_message(ANDROID_LOG_FATAL,"ReXSession","game destroy threw; module violated teardown contract");std::terminate();}
    }
    if(window)ANativeWindow_release(window);
    disc=nullptr;
  }
};
std::mutex sessions_mutex;
std::map<jlong,std::shared_ptr<Session>> sessions;
jlong next_id=1;
std::shared_ptr<Session> find(jlong id){std::lock_guard lock(sessions_mutex);auto it=sessions.find(id);return it==sessions.end()?nullptr:it->second;}
}

extern "C" JNIEXPORT void JNICALL Java_pl_rexlauncher_NativeBridge_init(JNIEnv* env,jclass,jstring logs,jstring libs,jstring cache) {
  try {
    log_root=utf(env,logs);native_libs=utf(env,libs);cache_root=utf(env,cache);
    std::string path=log_root+"/native-"+std::to_string(getpid())+".log";
    log_fd=open(path.c_str(),O_CREAT|O_WRONLY|O_APPEND|O_CLOEXEC,0600);
    if(log_fd<0)throw std::runtime_error("Cannot open native log file");
    if(dup2(log_fd,STDOUT_FILENO)<0||dup2(log_fd,STDERR_FILENO)<0)throw std::runtime_error("Could not redirect stdout/stderr");
    setvbuf(stdout,nullptr,_IONBF,0);setvbuf(stderr,nullptr,_IONBF,0);
    log_message(ANDROID_LOG_INFO,"ReXLauncher",REX_HAS_GAME?"Native bridge with linked game":"Native bridge; no linked game; diagnostics available");
  }catch(const std::exception& e){env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());}
}
extern "C" JNIEXPORT jboolean JNICALL Java_pl_rexlauncher_NativeBridge_hasGame(JNIEnv*,jclass){return game_api()!=nullptr;}
extern "C" JNIEXPORT jstring JNICALL Java_pl_rexlauncher_NativeBridge_inspectIso(JNIEnv* env,jclass,jint fd) {
  try{rexlauncher::DiscImage disc(fd);auto text=disc.describe();log_message(ANDROID_LOG_INFO,"ReXVFS",text.c_str());return string(env,text);}
  catch(const std::exception& e){log_message(ANDROID_LOG_ERROR,"ReXVFS",e.what());return string(env,std::string("ISO error: ")+e.what());}
}
extern "C" JNIEXPORT jbyteArray JNICALL Java_pl_rexlauncher_NativeBridge_readXex(JNIEnv* env,jclass,jint fd) {
  try {
    rexlauncher::DiscImage image(fd);int64_t size=image.file_size("default.xex");
    if(size<24||size>64*1024*1024)throw std::runtime_error("Invalid XEX size");
    std::vector<uint8_t> bytes(static_cast<size_t>(size));
    if(image.read("default.xex",0,bytes.data(),bytes.size())!=size)throw std::runtime_error("Odczyt XEX");
    jbyteArray result=env->NewByteArray(jsize(size));
    if(result)env->SetByteArrayRegion(result,0,jsize(size),reinterpret_cast<const jbyte*>(bytes.data()));
    return result;
  }catch(const std::exception& e){env->ThrowNew(env->FindClass("java/io/IOException"),e.what());return nullptr;}
}
extern "C" JNIEXPORT jlong JNICALL Java_pl_rexlauncher_NativeBridge_create(JNIEnv* env,jclass,jint fd,jstring dir,jstring name,
    jstring user,jint width,jint height,jint scale,jboolean diagnostic) {
  try {
    if(width<1||height<1||width>8192||height>8192||scale<0||scale>2)throw std::runtime_error("Invalid video parameters");
    auto session=std::make_shared<Session>(fd,utf(env,dir),utf(env,name),utf(env,user),
        RexVideo{uint32_t(width),uint32_t(height),RexScale(scale)},diagnostic);
    std::lock_guard lock(sessions_mutex);
    // ReXGlue Runtime has a process singleton. Never permit overlapping runtimes.
    if(!sessions.empty())throw std::runtime_error("Previous session is closing; try again");
    jlong id=next_id++;
    session->thread=std::thread([ptr=session.get()]{ptr->run();});
    try{sessions.emplace(id,session);}catch(...){session->stop();throw;}
    return id;
  } catch(const std::exception& e){env->ThrowNew(env->FindClass("java/lang/IllegalStateException"),e.what());return 0;}
}
extern "C" JNIEXPORT void JNICALL Java_pl_rexlauncher_NativeBridge_surface(JNIEnv* env,jclass,jlong id,jobject surface,jint width,jint height) {
  auto session=find(id);if(!session)return;
  ANativeWindow* window=surface?ANativeWindow_fromSurface(env,surface):nullptr;
  {std::lock_guard lock(session->mutex);if(session->pending_window)ANativeWindow_release(session->pending_window);
   session->pending_window=window;session->pending_w=uint32_t(std::max(0,width));session->pending_h=uint32_t(std::max(0,height));session->surface_dirty=true;}
  session->wake.notify_all();
}
extern "C" JNIEXPORT void JNICALL Java_pl_rexlauncher_NativeBridge_pause(JNIEnv*,jclass,jlong id,jboolean paused) {
  auto session=find(id);if(!session)return;
  {std::lock_guard lock(session->mutex);session->paused=paused;session->pause_dirty=true;}
  session->wake.notify_all();
}
extern "C" JNIEXPORT void JNICALL Java_pl_rexlauncher_NativeBridge_input(JNIEnv*,jclass,jlong id,jint buttons,jint lt,jint rt,jint lx,jint ly,jint rx,jint ry) {
  auto session=find(id);if(!session)return;
  std::lock_guard lock(session->mutex);
  RexPad next{session->pad.packet,uint16_t(buttons&0xf7ff),uint8_t(std::clamp(lt,0,255)),uint8_t(std::clamp(rt,0,255)),
      int16_t(std::clamp(lx,-32768,32767)),int16_t(std::clamp(ly,-32768,32767)),int16_t(std::clamp(rx,-32768,32767)),int16_t(std::clamp(ry,-32768,32767))};
  // Packet number changes ONLY when state changes, including release/cancel.
  if(std::memcmp(reinterpret_cast<const char*>(&next)+4,reinterpret_cast<const char*>(&session->pad)+4,12))++next.packet;
  session->pad=next;
}
extern "C" JNIEXPORT jstring JNICALL Java_pl_rexlauncher_NativeBridge_status(JNIEnv* env,jclass,jlong id) {
  auto session=find(id);if(!session)return string(env,"Session closed");
  std::lock_guard lock(session->mutex);std::ostringstream out;out<<session->message;
  if(session->diagnostic){const auto& p=session->pad;out<<"\nXInput #"<<p.packet<<" buttons=0x"<<std::hex<<p.buttons<<std::dec
      <<" LT="<<unsigned(p.lt)<<" RT="<<unsigned(p.rt)<<"\nLS="<<p.lx<<","<<p.ly<<" RS="<<p.rx<<","<<p.ry;}
  return string(env,out.str());
}
extern "C" JNIEXPORT void JNICALL Java_pl_rexlauncher_NativeBridge_destroy(JNIEnv*,jclass,jlong id) {
  auto session=find(id);if(!session)return;
  // Keep it registered until threads have stopped, blocking concurrent create().
  session->stop();
  {std::lock_guard lock(sessions_mutex);sessions.erase(id);}
}
