#version 310 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
uniform samplerExternalOES source;
uniform mat4 transform;
in vec2 uv;
layout(location=0) out vec4 color;
void main(){vec2 at=(transform*vec4(uv,0.0,1.0)).xy;color=vec4(texture(source,at).rgb,1.0);}
