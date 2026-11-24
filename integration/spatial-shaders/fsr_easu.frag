#version 310 es
precision highp float;
precision highp int;
#define A_GPU 1
#define A_GLSL 1
#define FSR_EASU_F 1
#include "ffx_a.h"

uniform highp sampler2D source;
layout(location=0) out vec4 color;
#include "ffx_fsr1.h"

AF4 FsrEasuRF(AF2 p){return textureGather(source,p,0);}
AF4 FsrEasuGF(AF2 p){return textureGather(source,p,1);}
AF4 FsrEasuBF(AF2 p){return textureGather(source,p,2);}
uniform vec2 inputSize,outputSize;
void main(){
 AU4 c0,c1,c2,c3;
 FsrEasuCon(c0,c1,c2,c3,inputSize.x,inputSize.y,inputSize.x,inputSize.y,outputSize.x,outputSize.y);
 AF3 rgb;FsrEasuF(rgb,AU2(gl_FragCoord.xy),c0,c1,c2,c3);
 color=vec4(clamp(rgb,0.0,1.0),1.0);
}
