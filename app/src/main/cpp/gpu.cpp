#include "gpu.h"
#include <adrenotools/driver.h>
#include <dlfcn.h>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <vector>

PFN_vkGetInstanceProcAddr open_vulkan(const std::string& dir,const std::string& name,
                                     const std::string& libraries,const std::string& cache) {
  static std::mutex mutex;
  static void* handle=nullptr;
  static std::string loaded;
  static PFN_vkGetInstanceProcAddr get=nullptr;
  std::lock_guard lock(mutex);
  const std::string requested=dir.empty()?"system":dir+"/"+name;
  if(handle) {
    if(loaded!=requested)throw std::runtime_error("Changing driver requires restarting the app process");
    return get;
  }
  void* next;
  if(dir.empty())next=dlopen("libvulkan.so",RTLD_NOW|RTLD_LOCAL);
  else next=adrenotools_open_libvulkan(RTLD_NOW|RTLD_LOCAL,ADRENOTOOLS_DRIVER_CUSTOM,
      cache.c_str(),libraries.c_str(),(dir+"/").c_str(),name.c_str(),nullptr,nullptr);
  if(!next){const char* e=dlerror();throw std::runtime_error(std::string("Could not open Vulkan/Turnip: ")+(e?e:"Adreno Tools returned nullptr"));}
  auto candidate=reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(next,"vkGetInstanceProcAddr"));
  if(!candidate) {
    // Keep the opened loader alive: Adreno hooks may already be installed.
    handle=next;loaded=requested;
    throw std::runtime_error("Biblioteka Vulkan nie eksportuje vkGetInstanceProcAddr; uruchom proces ponownie");
  }
  handle=next;loaded=requested;get=candidate;
  // Never dlclose a Vulkan loader/hook while runtime or driver threads can use it.
  return get;
}
std::string probe_vulkan(PFN_vkGetInstanceProcAddr get) {
  if(!get)throw std::runtime_error("Invalid Vulkan loader; restart the process");
  auto create=reinterpret_cast<PFN_vkCreateInstance>(get(VK_NULL_HANDLE,"vkCreateInstance"));
  if(!create)throw std::runtime_error("vkCreateInstance missing");
  VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
  app.pApplicationName="ReX Launcher diagnostics";app.apiVersion=VK_API_VERSION_1_0;
  VkInstanceCreateInfo ci{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};ci.pApplicationInfo=&app;
  VkInstance instance=VK_NULL_HANDLE;
  VkResult status=create(&ci,nullptr,&instance);
  if(status!=VK_SUCCESS)throw std::runtime_error("vkCreateInstance: "+std::to_string(status));
  auto destroy=reinterpret_cast<PFN_vkDestroyInstance>(get(instance,"vkDestroyInstance"));
  auto enumerate=reinterpret_cast<PFN_vkEnumeratePhysicalDevices>(get(instance,"vkEnumeratePhysicalDevices"));
  auto properties=reinterpret_cast<PFN_vkGetPhysicalDeviceProperties>(get(instance,"vkGetPhysicalDeviceProperties"));
  if(!destroy||!enumerate||!properties) {
    if(destroy)destroy(instance,nullptr);
    throw std::runtime_error("Required Vulkan instance functions missing");
  }
  struct Guard { VkInstance i;PFN_vkDestroyInstance d;~Guard(){d(i,nullptr);} } guard{instance,destroy};
  uint32_t count=0;status=enumerate(instance,&count,nullptr);
  if(status!=VK_SUCCESS||!count||count>256)throw std::runtime_error("Vulkan: no GPU devices, code "+std::to_string(status));
  std::vector<VkPhysicalDevice> devices(count);
  status=enumerate(instance,&count,devices.data());
  if(status!=VK_SUCCESS&&status!=VK_INCOMPLETE)throw std::runtime_error("Vulkan: GPU enumeration failed");
  std::ostringstream text;
  for(uint32_t n=0;n<count;n++){
    VkPhysicalDeviceProperties p{};properties(devices[n],&p);
    text<<p.deviceName<<" · Vulkan "<<VK_VERSION_MAJOR(p.apiVersion)<<"."<<VK_VERSION_MINOR(p.apiVersion)
        <<" · vendor=0x"<<std::hex<<p.vendorID<<" driver=0x"<<p.driverVersion<<std::dec<<"\n";
  }
  return text.str();
}
