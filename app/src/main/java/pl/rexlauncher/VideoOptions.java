package pl.rexlauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.SurfaceView;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

/** Session configuration comes from the launch Intent, also in child processes. */
public final class VideoOptions {
    private VideoOptions() {}
    public static int hz(Intent intent) { return intent.getIntExtra("refreshRate",60)==30?30:60; }
    public static int orientation(Intent intent) {
        int side=intent.getIntExtra("orientation",2);
        return side==2?ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE:
                side==1?ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    }
    public static void window(Activity activity) {
        WindowManager.LayoutParams attributes=activity.getWindow().getAttributes();
        attributes.preferredRefreshRate=hz(activity.getIntent());
        activity.getWindow().setAttributes(attributes);
        android.util.Log.i("ReXVideo","Guest "+hz(activity.getIntent())+" Hz, orientation="+orientation(activity.getIntent()));
    }
    public static void surface(Activity activity,Surface surface) {
        if(Build.VERSION.SDK_INT>=30&&surface.isValid()){
            // A hint: Android may choose a supported multiple such as 60/120 Hz.
            try{surface.setFrameRate(hz(activity.getIntent()),Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);}
            catch(IllegalArgumentException|IllegalStateException e){android.util.Log.w("ReXVideo","Frame rate hint",e);}
        }
    }
    public static void configureSurface(Activity activity,SurfaceView surface) {
        int width=activity.getIntent().getIntExtra("renderWidth",1280);
        int height=activity.getIntent().getIntExtra("renderHeight",720);
        if(!ResolutionPolicy.valid(width,height))return;
        surface.getHolder().setFixedSize(width,height);
        View parent=(View)surface.getParent();
        parent.setBackgroundColor(android.graphics.Color.BLACK);
        Runnable resize=()->{
            if(parent.getWidth()==0||parent.getHeight()==0)return;
            int[] fitted=ResolutionPolicy.fit(width,height,parent.getWidth(),parent.getHeight(),activity.getIntent().getIntExtra("scale",0));
            ViewGroup.LayoutParams old=surface.getLayoutParams();
            if(old.width==fitted[0]&&old.height==fitted[1])return;
            if(parent instanceof RelativeLayout){
                RelativeLayout.LayoutParams params=new RelativeLayout.LayoutParams(fitted[0],fitted[1]);
                params.addRule(RelativeLayout.CENTER_IN_PARENT);surface.setLayoutParams(params);
            }else if(parent instanceof FrameLayout){
                surface.setLayoutParams(new FrameLayout.LayoutParams(fitted[0],fitted[1],Gravity.CENTER));
            }
        };
        parent.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->resize.run());
        parent.post(resize);
    }
    public static void observe(Activity activity,SurfaceHolder holder) {
        holder.addCallback(new SurfaceHolder.Callback(){
            public void surfaceCreated(SurfaceHolder h){surface(activity,h.getSurface());}
            public void surfaceChanged(SurfaceHolder h,int format,int width,int height){surface(activity,h.getSurface());}
            public void surfaceDestroyed(SurfaceHolder h){}
        });
        surface(activity,holder.getSurface());
    }
}
