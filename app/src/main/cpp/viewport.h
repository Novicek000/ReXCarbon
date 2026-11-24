#pragma once
#include "game_api.h"
#include <algorithm>
#include <cmath>
inline RexViewport viewport(uint32_t w,uint32_t h,const RexVideo& video) {
  if (!w||!h||!video.width||!video.height) return {0,0,0,0};
  if (video.scaling==RexScale::Stretch) return {0,0,w,h};
  double factor=std::min(double(w)/video.width,double(h)/video.height);
  if(video.scaling==RexScale::Integer&&factor>=1)factor=std::floor(factor);
  uint32_t vw=std::min(w,std::max(1u,uint32_t(std::floor(video.width*factor))));
  uint32_t vh=std::min(h,std::max(1u,uint32_t(std::floor(video.height*factor))));
  return {int32_t((w-vw)/2),int32_t((h-vh)/2),vw,vh};
}
