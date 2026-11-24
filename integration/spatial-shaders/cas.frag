#version 310 es
precision highp float;
precision highp int;
#define A_GPU 1
#define A_GLSL 1
#include "ffx_a.h"

uniform highp sampler2D source;
layout(location=0) out vec4 color;


AF3 CasLoad(ASU2 p){return texelFetch(source,clamp(p,ivec2(0),textureSize(source,0)-1),0).rgb;}
void CasInput(inout AF1 r,inout AF1 g,inout AF1 b){}
#include "ffx_cas.h"
uniform float sharpness;
uniform vec2 inputSize,outputSize;
void main(){
 AU4 c0,c1;CasSetup(c0,c1,sharpness,inputSize.x,inputSize.y,outputSize.x,outputSize.y);
 AF3 rgb;
 if(all(equal(inputSize,outputSize)))CasFilter(rgb.r,rgb.g,rgb.b,AU2(gl_FragCoord.xy),c0,c1,true);
 else CasFilter(rgb.r,rgb.g,rgb.b,AU2(gl_FragCoord.xy),c0,c1,false);
 color=vec4(clamp(rgb,0.0,1.0),1.0);
}
