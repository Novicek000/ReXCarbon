#pragma once
// Launcher-owned ABI. These are NOT invented ReXGlue SDK functions.
// A game-specific static target implements rex_game_api_v1().
#include <android/native_window.h>
#include <vulkan/vulkan.h>
#include <cstddef>
#include <cstdint>

struct RexPad {
  uint32_t packet;
  uint16_t buttons;
  uint8_t lt,rt;
  int16_t lx,ly,rx,ry;
};
static_assert(sizeof(RexPad)==16);
enum class RexScale : uint32_t { Aspect=0, Stretch=1, Integer=2 };
struct RexVideo { uint32_t width,height; RexScale scaling; };
struct RexViewport { int32_t x,y; uint32_t width,height; };
struct RexHostV1 {
  uint32_t size,version;
  void* context;
  int iso_fd; // borrowed, seekable, valid until game destroy() returns
  const char* user_root;
  const char* log_root;
  PFN_vkGetInstanceProcAddr vk_get_instance_proc_addr;
  int64_t (*asset_size)(void*,const char*);
  int64_t (*read_asset)(void*,const char*,uint64_t,void*,uint64_t);
  void (*read_pad)(void*,RexPad*);
  void (*log)(int,const char*,const char*);
};
struct RexGameV1 {
  uint32_t size,version;
  const char* name;
  // Validate XEX build/hash, setup Runtime and spawn guest. Do not block here.
  // Copy video; host and its strings live until destroy returns.
  void* (*create)(const RexHostV1*,const RexVideo*,char* error,size_t capacity);
  // Called on ONE runtime worker. Drain old swapchain before returning.
  // The window is borrowed until the next surface() call or destroy().
  void (*surface)(void*,ANativeWindow*,uint32_t,uint32_t,RexViewport);
  void (*pause)(void*,bool);
  int (*pump)(void*); // >0 running, 0 guest exited, <0 failure. Nonblocking.
  void (*destroy)(void*); // stop/join ALL guest/audio/GPU threads before return
};
extern "C" const RexGameV1* rex_game_api_v1();
