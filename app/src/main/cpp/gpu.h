#pragma once
#include <string>
#include <vulkan/vulkan.h>
PFN_vkGetInstanceProcAddr open_vulkan(const std::string& driver_dir,const std::string& driver_name,
                                     const std::string& native_libs,const std::string& cache);
std::string probe_vulkan(PFN_vkGetInstanceProcAddr get);
