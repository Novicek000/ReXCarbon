#include <EGL/egl.h>
#include <GLES3/gl31.h>
#include <cstdio>
int main(){
 EGLDisplay d=eglGetDisplay(EGL_DEFAULT_DISPLAY);EGLint major,minor;
 if(!eglInitialize(d,&major,&minor)){printf("eglInitialize error %x\n",eglGetError());return 1;}
 const EGLint attrs[]={EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_RENDERABLE_TYPE,EGL_OPENGL_ES3_BIT,EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_NONE};
 EGLConfig cfg;EGLint n;eglChooseConfig(d,attrs,&cfg,1,&n);if(!n)return 2;
 const EGLint ca[]={EGL_CONTEXT_CLIENT_VERSION,3,EGL_NONE},pa[]={EGL_WIDTH,64,EGL_HEIGHT,64,EGL_NONE};
 EGLContext c=eglCreateContext(d,cfg,EGL_NO_CONTEXT,ca);EGLSurface s=eglCreatePbufferSurface(d,cfg,pa);
 if(!eglMakeCurrent(d,s,s,c)){printf("eglMakeCurrent error %x\n",eglGetError());return 3;}
 printf("EGL %d.%d; %s; %s\n",major,minor,glGetString(GL_VERSION),glGetString(GL_RENDERER));
 eglMakeCurrent(d,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);eglDestroySurface(d,s);eglDestroyContext(d,c);eglTerminate(d);return 0;
}
