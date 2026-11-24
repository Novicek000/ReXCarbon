package pl.rexlauncher;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.*;
import android.util.Log;
import android.view.Surface;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Vulkan producer -> SurfaceTexture -> GPU copy -> AMD EASU/RCAS or CAS -> display. */
public final class SpatialRenderer {
    private final Context context;
    private final int inputWidth,inputHeight,effect;
    private final float sharpness;
    private final Runnable ready;
    private final Consumer<String> error;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final HandlerThread thread=new HandlerThread("Carbon image filters");
    private final AtomicBoolean closed=new AtomicBoolean(),failed=new AtomicBoolean(),queued=new AtomicBoolean();
    private Handler gl;
    private volatile Surface producer;
    private SurfaceTexture stream;
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext=EGL14.EGL_NO_CONTEXT;
    private EGLSurface window=EGL14.EGL_NO_SURFACE;
    private EGLSurface drainSurface=EGL14.EGL_NO_SURFACE;
    private volatile boolean draining;
    private boolean drainBound;
    private final int[] textures=new int[3],framebuffers=new int[2],vao=new int[1];
    private final float[] transform=new float[16];
    private Program copy,easu,rcas,cas;
    private int outputWidth,outputHeight;
    public SpatialRenderer(Context context,int width,int height,int effect,int sharpness,Runnable ready,Consumer<String> error){
        this.context=context.getApplicationContext();inputWidth=width;inputHeight=height;
        this.effect=effect;this.sharpness=GraphicsPolicy.clamp(sharpness,100)/100f;
        this.ready=ready;this.error=error;
    }
    public Surface producer(){return producer;}
    public void start(Surface target){
        thread.start();gl=new Handler(thread.getLooper());gl.post(()->{
            try{initialize(target);ui.post(()->{if(!closed.get()&&!failed.get())ready.run();});}
            catch(Exception e){fail(e);}
        });
    }
    public void resize(int width,int height){
        if(gl==null||closed.get())return;
        gl.post(()->{if(closed.get()||failed.get())return;try{
            if(width<1||height<1)return;
            int[] max={0};GLES31.glGetIntegerv(GLES31.GL_MAX_TEXTURE_SIZE,max,0);
            if(width>max[0]||height>max[0])throw new IOException("Output exceeds GPU texture size "+max[0]);
            if(width==outputWidth&&height==outputHeight)return;
            outputWidth=width;outputHeight=height;
            if(effect==1)allocate(textures[2],framebuffers[1],width,height);
        }catch(Exception e){fail(e);}});
    }
    private void initialize(Surface target)throws Exception{
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version=new int[2];
        if(!EGL14.eglInitialize(display,version,0,version,1))throw egl("Initialize");
        int[] attrs={EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT|EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RENDERABLE_TYPE,0x40,
            EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE};
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
        if(!EGL14.eglChooseConfig(display,attrs,0,configs,0,1,count,0)||count[0]==0)throw egl("Choose ES3 config");
        eglContext=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
        window=EGL14.eglCreateWindowSurface(display,configs[0],target,new int[]{EGL14.EGL_NONE},0);
        if(eglContext==EGL14.EGL_NO_CONTEXT||window==EGL14.EGL_NO_SURFACE||!EGL14.eglMakeCurrent(display,window,window,eglContext))throw egl("Make current");
        drainSurface=EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);
        if(drainSurface==EGL14.EGL_NO_SURFACE)throw egl("Create drain surface");
        int[] major={0},minor={0},max={0};GLES31.glGetIntegerv(GLES31.GL_MAJOR_VERSION,major,0);GLES31.glGetIntegerv(GLES31.GL_MINOR_VERSION,minor,0);
        if(major[0]<3||(major[0]==3&&minor[0]<1))throw new IOException("Image filters require OpenGL ES 3.1");
        GLES31.glGetIntegerv(GLES31.GL_MAX_TEXTURE_SIZE,max,0);
        if(inputWidth>max[0]||inputHeight>max[0])throw new IOException("Input exceeds GPU texture size "+max[0]);
        EGL14.eglSwapInterval(display,1);
        GLES31.glGenVertexArrays(1,vao,0);GLES31.glBindVertexArray(vao[0]);
        String vertex=read("fullscreen.vert");copy=new Program(vertex,read("external.frag"));
        if(effect==1){easu=new Program(vertex,read("fsr_easu.frag"));rcas=new Program(vertex,read("fsr_rcas.frag"));}
        else cas=new Program(vertex,read("cas.frag"));
        GLES31.glGenTextures(3,textures,0);GLES31.glGenFramebuffers(2,framebuffers,0);
        GLES31.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textures[0]);
        params(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        allocate(textures[1],framebuffers[0],inputWidth,inputHeight);
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER,0);
        stream=new SurfaceTexture(textures[0]);stream.setDefaultBufferSize(inputWidth,inputHeight);
        stream.setOnFrameAvailableListener(s->{
            if(!closed.get()&&(!failed.get()||draining)&&queued.compareAndSet(false,true))gl.post(()->{
                queued.set(false);if(closed.get()||(failed.get()&&!draining))return;
                try{draw();}catch(Exception e){fail(e);}
            });
        },gl);
        producer=new Surface(stream);
        Log.i("ReXFilters",(effect==1?"AMD FSR1 EASU + RCAS":"AMD FidelityFX CAS")+" ready; input="+inputWidth+"x"+inputHeight+"; "+GLES31.glGetString(GLES31.GL_RENDERER));
    }
    private String read(String name)throws IOException{
        try(InputStream in=context.getAssets().open("graphics/"+name);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString(StandardCharsets.UTF_8.name());
        }
    }
    private static void params(int target){
        GLES31.glTexParameteri(target,GLES31.GL_TEXTURE_MIN_FILTER,GLES31.GL_LINEAR);GLES31.glTexParameteri(target,GLES31.GL_TEXTURE_MAG_FILTER,GLES31.GL_LINEAR);
        GLES31.glTexParameteri(target,GLES31.GL_TEXTURE_WRAP_S,GLES31.GL_CLAMP_TO_EDGE);GLES31.glTexParameteri(target,GLES31.GL_TEXTURE_WRAP_T,GLES31.GL_CLAMP_TO_EDGE);
    }
    private static void allocate(int texture,int framebuffer,int width,int height)throws IOException{
        GLES31.glBindTexture(GLES31.GL_TEXTURE_2D,texture);params(GLES31.GL_TEXTURE_2D);
        GLES31.glTexImage2D(GLES31.GL_TEXTURE_2D,0,GLES31.GL_RGBA8,width,height,0,GLES31.GL_RGBA,GLES31.GL_UNSIGNED_BYTE,null);
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER,framebuffer);
        GLES31.glFramebufferTexture2D(GLES31.GL_FRAMEBUFFER,GLES31.GL_COLOR_ATTACHMENT0,GLES31.GL_TEXTURE_2D,texture,0);
        if(GLES31.glCheckFramebufferStatus(GLES31.GL_FRAMEBUFFER)!=GLES31.GL_FRAMEBUFFER_COMPLETE)throw new IOException("Filter framebuffer allocation failed");
    }
    private void draw()throws Exception{
        if(draining)bindDrain();
        stream.updateTexImage();stream.getTransformMatrix(transform);
        if(draining)return;
        if(outputWidth<1||outputHeight<1)return;
        GLES31.glDisable(GLES31.GL_BLEND);GLES31.glDisable(GLES31.GL_DEPTH_TEST);GLES31.glDisable(GLES31.GL_SCISSOR_TEST);
        pass(copy,framebuffers[0],textures[0],GLES11Ext.GL_TEXTURE_EXTERNAL_OES,inputWidth,inputHeight);
        if(effect==1){
            pass(easu,framebuffers[1],textures[1],GLES31.GL_TEXTURE_2D,outputWidth,outputHeight);
            pass(rcas,0,textures[2],GLES31.GL_TEXTURE_2D,outputWidth,outputHeight);
        }else pass(cas,0,textures[1],GLES31.GL_TEXTURE_2D,outputWidth,outputHeight);
        if(draining){bindDrain();return;}
        if(!EGL14.eglSwapBuffers(display,window)&&!draining)throw egl("Swap filtered frame");
    }
    private void pass(Program p,int framebuffer,int texture,int target,int width,int height){
        GLES31.glBindFramebuffer(GLES31.GL_FRAMEBUFFER,framebuffer);GLES31.glViewport(0,0,width,height);
        GLES31.glUseProgram(p.id);GLES31.glActiveTexture(GLES31.GL_TEXTURE0);GLES31.glBindTexture(target,texture);
        GLES31.glUniform1i(p.source,0);GLES31.glUniformMatrix4fv(p.transform,1,false,transform,0);
        GLES31.glUniform2f(p.inputSize,inputWidth,inputHeight);GLES31.glUniform2f(p.outputSize,width,height);GLES31.glUniform1f(p.sharpness,sharpness);
        GLES31.glDrawArrays(GLES31.GL_TRIANGLES,0,3);
    }
    private IOException egl(String operation){return new IOException("EGL "+operation+": 0x"+Integer.toHexString(EGL14.eglGetError()));}
    private void bindDrain()throws IOException{
        if(!drainBound&&drainSurface!=EGL14.EGL_NO_SURFACE){
            if(!EGL14.eglMakeCurrent(display,drainSurface,drainSurface,eglContext))throw egl("Drain producer");
            drainBound=true;
        }
    }
    public void drain(){
        draining=true;
        if(gl!=null&&!closed.get())gl.post(()->{try{bindDrain();}catch(Exception e){fail(e);}});
    }
    private void fail(Exception e){if(!closed.get()&&failed.compareAndSet(false,true)){
        Log.e("ReXFilters","Image filter failed",e);draining=true;
        try{bindDrain();}catch(Exception ignored){if(stream!=null)stream.release();}
        ui.post(()->{if(!closed.get())error.accept(e.getMessage());});
    }}
    public void close(){
        if(!closed.compareAndSet(false,true)||gl==null)return;
        gl.post(()->{try{
            if(stream!=null)stream.setOnFrameAvailableListener(null);
            if(producer!=null){producer.release();producer=null;}if(stream!=null)stream.release();
            if(eglContext!=EGL14.EGL_NO_CONTEXT&&window!=EGL14.EGL_NO_SURFACE){
                EGLSurface cleanup=drainSurface!=EGL14.EGL_NO_SURFACE?drainSurface:window;
                EGL14.eglMakeCurrent(display,cleanup,cleanup,eglContext);
                for(Program p:new Program[]{copy,easu,rcas,cas})if(p!=null)GLES31.glDeleteProgram(p.id);
                GLES31.glDeleteTextures(3,textures,0);GLES31.glDeleteFramebuffers(2,framebuffers,0);GLES31.glDeleteVertexArrays(1,vao,0);
            }
            if(display!=EGL14.EGL_NO_DISPLAY){EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
                if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,window);
                if(drainSurface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,drainSurface);
                if(eglContext!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,eglContext);EGL14.eglTerminate(display);}
        }finally{thread.quitSafely();}});
    }
    private static final class Program {
        final int id,source,transform,inputSize,outputSize,sharpness;
        Program(String vertex,String fragment)throws IOException{
            int v=shader(GLES31.GL_VERTEX_SHADER,vertex),f=0,program=0;
            try{f=shader(GLES31.GL_FRAGMENT_SHADER,fragment);program=GLES31.glCreateProgram();GLES31.glAttachShader(program,v);GLES31.glAttachShader(program,f);GLES31.glLinkProgram(program);
                int[] ok={0};GLES31.glGetProgramiv(program,GLES31.GL_LINK_STATUS,ok,0);if(ok[0]==0)throw new IOException("Filter link: "+GLES31.glGetProgramInfoLog(program));
            }catch(IOException e){if(program!=0)GLES31.glDeleteProgram(program);throw e;}finally{GLES31.glDeleteShader(v);if(f!=0)GLES31.glDeleteShader(f);}
            id=program;source=loc("source");transform=loc("transform");inputSize=loc("inputSize");outputSize=loc("outputSize");sharpness=loc("sharpness");
        }
        private int loc(String name){return GLES31.glGetUniformLocation(id,name);}
        private static int shader(int type,String code)throws IOException{
            int id=GLES31.glCreateShader(type);GLES31.glShaderSource(id,code);GLES31.glCompileShader(id);int[] ok={0};GLES31.glGetShaderiv(id,GLES31.GL_COMPILE_STATUS,ok,0);
            if(ok[0]==0){String error=GLES31.glGetShaderInfoLog(id);GLES31.glDeleteShader(id);throw new IOException("Filter shader: "+error);}return id;
        }
    }
}
