#version 310 es
precision highp float;
precision highp int;
#define A_GPU 1
#define A_GLSL 1
#define FSR_RCAS_F 1
#include "ffx_a.h"

uniform highp sampler2D source;
layout(location=0) out vec4 color;
#include "ffx_fsr1.h"

AF4 FsrRcasLoadF(ASU2 p){return texelFetch(source,clamp(p,ivec2(0),textureSize(source,0)-1),0);}
void FsrRcasInputF(inout AF1 r,inout AF1 g,inout AF1 b){}
uniform float sharpness;
void main(){
 AU4 c;FsrRcasCon(c,2.0*(1.0-sharpness));
 AF3 rgb;FsrRcasF(rgb.r,rgb.g,rgb.b,AU2(gl_FragCoord.xy),c);
 color=vec4(clamp(rgb,0.0,1.0),1.0);
}
