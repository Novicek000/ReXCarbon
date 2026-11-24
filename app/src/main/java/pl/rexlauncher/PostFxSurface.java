package pl.rexlauncher;

import android.content.Context;
import android.content.Intent;
import android.view.Surface;
import android.view.SurfaceHolder;
import org.libsdl.app.SDLSurface;
import java.util.function.Consumer;

/** Keeps SDL lifecycle on the UI thread and exposes only the filtered producer to Vulkan. */
public final class PostFxSurface extends SDLSurface {
    private final Intent settings;
    private final Consumer<String> failure;
    private final int[] inputSize;
    private SpatialRenderer renderer;
    private boolean nativeReady,changed;
    private int format,width,height,generation;
    public PostFxSurface(Context context,Intent settings,Consumer<String> failure){
        super(context);this.settings=settings;this.failure=failure;inputSize=GraphicsOptions.inputSize(settings);
    }
    @Override protected Surface getNativeSurface(){return renderer==null?null:renderer.producer();}
    @Override public void surfaceCreated(SurfaceHolder holder){
        final int token=++generation;changed=false;nativeReady=false;
        renderer=new SpatialRenderer(getContext(),inputSize[0],inputSize[1],GraphicsOptions.effect(settings),settings.getIntExtra("imageSharpness",50),()->{
            if(token!=generation||renderer==null)return;
            nativeReady=true;PostFxSurface.super.surfaceCreated(holder);
            if(changed)PostFxSurface.super.surfaceChanged(holder,format,inputSize[0],inputSize[1]);
        },failure);
        renderer.start(holder.getSurface());
    }
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){
        this.format=format;this.width=width;this.height=height;changed=true;
        if(renderer!=null)renderer.resize(width,height);
        if(nativeReady)super.surfaceChanged(holder,format,inputSize[0],inputSize[1]);
    }
    @Override public void surfaceDestroyed(SurfaceHolder holder){
        ++generation;
        // Keep consuming Vulkan buffers while SDL pauses the producer; otherwise
        // a full BufferQueue could block shutdown when the display disappears.
        if(renderer!=null)renderer.drain();
        if(nativeReady)super.surfaceDestroyed(holder);
        nativeReady=false;mIsSurfaceReady=false;
        if(renderer!=null){renderer.close();renderer=null;}
    }
    public void shutdown(){if(renderer!=null)renderer.close();}
}
