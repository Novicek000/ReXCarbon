#pragma once
#include "rex_android_input.h"
#include "rex_saf_disc.h"
#include <rex/runtime.h>
#include <rex/system/xthread.h>
#include <stdexcept>

namespace rexlauncher {
// Call from RexGameV1::create in the GAME PORT, after its Vulkan backend was
// configured with host.vk_get_instance_proc_addr, video dimensions and window
// lifecycle callbacks. kernel_init/audio_factory/graphics come from that port.
// graphics==nullptr is only valid for a title with a complete native renderer.
inline std::unique_ptr<rex::Runtime> boot_runtime(const RexHostV1& host,
    const rex::PPCImageInfo& generated_image, rex::RuntimeConfig config) {
  auto root=std::filesystem::path(host.user_root);
  std::filesystem::create_directories(root/"cache");
  std::filesystem::create_directories(root/"metadata");
  // Empty game_data_root: mount the ISO ourselves, without a HostPathDevice.
  auto runtime=std::make_unique<rex::Runtime>(std::filesystem::path{},root,
      std::filesystem::path{},root/"cache",root/"metadata");
  config.input_factory=[&host](bool){return make_android_input(&host);};
  if(runtime->Setup(generated_image,std::move(config))!=X_STATUS_SUCCESS)
    throw std::runtime_error("ReXGlue Runtime::Setup failed");
  auto* vfs=runtime->file_system();
  constexpr const char* mount="\\Device\\Cdrom0";
  // Link rex_saf_disc.cpp and xdvdfs.cpp into the port. Duplicate the SAF FD;
  // no path reopening or whole-image mapping is required by the VFS.
  auto device=make_saf_disc(mount,host.iso_fd);
  if(!device->Initialize()||!vfs->RegisterDevice(std::move(device))||
     !vfs->RegisterSymbolicLink("game:",mount)||!vfs->RegisterSymbolicLink("d:",mount))
    throw std::runtime_error("ReXGlue ISO mount failed");
  if(runtime->LoadXexImage("game:\\default.xex")!=X_STATUS_SUCCESS)
    throw std::runtime_error("ReXGlue Runtime::LoadXexImage failed");
  // Game port must apply its patches/rexcrt setup and PrepareModuleLaunch(),
  // retain the guest thread object and resume it at the correct phase.
  // Do not run a desktop REX_DEFINE_APP entrypoint from an Android Activity.
  return runtime;
}
// Invoke after the port's image patches/rexcrt initialization and its first
// usable surface. Retain this ref until the guest has stopped and been joined.
inline rex::system::object_ref<rex::system::XThread> launch_runtime(rex::Runtime& runtime) {
  auto thread=runtime.PrepareModuleLaunch();
  if(!thread)throw std::runtime_error("ReXGlue PrepareModuleLaunch failed");
  if(thread->Resume()!=X_STATUS_SUCCESS)throw std::runtime_error("ReXGlue guest Resume failed");
  return thread;
}
}
