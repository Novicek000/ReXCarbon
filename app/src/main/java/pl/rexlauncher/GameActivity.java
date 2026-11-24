package pl.rexlauncher;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.concurrent.*;

public final class GameActivity extends Activity implements SurfaceHolder.Callback {
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private long session;
    private InputRouter input;
    private TouchPadView osd;
    private SurfaceView surface;
    private TextView status, controller;
    private boolean destroyed, resumed, creationDone;
    private int width,height;
    private final Runnable refresh=new Runnable(){ public void run(){
        if(destroyed)return;
        if(session!=0)status.setText(NativeBridge.status(session));
        ui.postDelayed(this,250);
    }};
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(AppLocale.wrap(base));}
    @Override public void onCreate(Bundle saved) {
        setRequestedOrientation(VideoOptions.orientation(getIntent()));
        super.onCreate(saved);
        VideoOptions.window(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);setContentView(root);
        surface=new SurfaceView(this);root.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        surface.getHolder().addCallback(this);
        VideoOptions.configureSurface(this,surface);
        input=new InputRouter(this,this::send,(physical,name)->{
            if(osd!=null){osd.clear();osd.setVisibility(physical?View.GONE:View.VISIBLE);}
            if(controller!=null)controller.setText(name+" · "+getString(physical?R.string.osd_hidden:R.string.osd_active));
        });
        osd=new TouchPadView(this,input::touch);root.addView(osd,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xb0151c24);panel.setPadding(16,8,16,8);
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(13);status.setText(R.string.diag_start);
        controller=new TextView(this);controller.setTextColor(0xff71d6b2);controller.setTextSize(12);
        panel.addView(status);panel.addView(controller);
        FrameLayout.LayoutParams info=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        info.leftMargin=150;info.rightMargin=150;root.addView(panel,info);
        Button back=new Button(this);back.setText(R.string.menu);back.setAllCaps(false);back.setOnClickListener(v->finish());
        root.addView(back,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT));
        boolean diagnostic=getIntent().getBooleanExtra("diagnostic",false);
        String iso=getIntent().getStringExtra("iso");
        String driverDir=getIntent().getStringExtra("driverDir");
        String driverName=getIntent().getStringExtra("driverName");
        int[] resolution={getIntent().getIntExtra("renderWidth",1280),getIntent().getIntExtra("renderHeight",720)};
        int scale=getIntent().getIntExtra("scale",0);
        File userRoot=new File(getFilesDir(),"user");
        worker.execute(()->{
            long created=0;String error=null;
            try {
                if(!userRoot.isDirectory()&&!userRoot.mkdirs())throw new java.io.IOException("Cannot create user data directory");
                try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(Uri.parse(iso),"r")) {
                    if(fd==null)throw new java.io.IOException("SAF could not open ISO");
                    created=NativeBridge.create(fd.getFd(),driverDir,driverName,userRoot.getAbsolutePath(),resolution[0],resolution[1],scale,diagnostic);
                    if(created==0)throw new java.io.IOException("Could not create native session");
                }
            } catch(Exception e) {error=e.toString();android.util.Log.e("ReXSession","Start",e);}
            long id=created;String message=error;
            ui.post(()->{
                creationDone=true;
                if(destroyed){if(id!=0)worker.execute(()->{NativeBridge.destroy(id);ui.post(this::endRuntimeProcess);});worker.shutdown();return;}
                session=id;
                if(message!=null){status.setText(message);return;}
                Surface current=surface.getHolder().getSurface();
                if(current.isValid())NativeBridge.surface(session,current,width,height);
                NativeBridge.pause(session,!resumed);input.neutral();
            });
        });
        ui.post(refresh);
    }
    private void send(int buttons,int lt,int rt,int lx,int ly,int rx,int ry) {
        if(session!=0)NativeBridge.input(session,buttons,lt,rt,lx,ly,rx,ry);
    }
    public void surfaceCreated(SurfaceHolder h){VideoOptions.surface(this,h.getSurface());}
    public void surfaceChanged(SurfaceHolder h,int format,int w,int height){
        VideoOptions.surface(this,h.getSurface());
        width=w;this.height=height;if(session!=0)NativeBridge.surface(session,h.getSurface(),w,height);
    }
    public void surfaceDestroyed(SurfaceHolder h){if(session!=0)NativeBridge.surface(session,null,0,0);}
    @Override public boolean dispatchKeyEvent(KeyEvent e){return input!=null&&input.key(e)||super.dispatchKeyEvent(e);}
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e){return input!=null&&input.motion(e)||super.dispatchGenericMotionEvent(e);}
    @Override public boolean onGenericMotionEvent(MotionEvent e){return input!=null&&input.motion(e)||super.onGenericMotionEvent(e);}
    @Override protected void onResume(){super.onResume();resumed=true;input.resume();if(session!=0)NativeBridge.pause(session,false);}
    @Override protected void onPause(){resumed=false;osd.clear();input.stop();if(session!=0)NativeBridge.pause(session,true);super.onPause();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(!focus&&input!=null){osd.clear();input.neutral();}if(session!=0)NativeBridge.pause(session,!focus||!resumed);}
    @Override protected void onDestroy(){
        destroyed=true;ui.removeCallbacks(refresh);long id=session;session=0;
        if(id!=0){worker.execute(()->{NativeBridge.destroy(id);ui.post(this::endRuntimeProcess);});worker.shutdown();}
        else if(creationDone)worker.shutdown();
        // If creation is still pending its UI completion owns cleanup and shutdown.
        // The upstream desktop SDK hard-exits on title shutdown. Confine any
        // deadlock/crash to :game and guarantee a fresh Vulkan namespace next time.
        ui.postDelayed(this::endRuntimeProcess,1500);
        super.onDestroy();
    }
    private void endRuntimeProcess(){
        if(!android.app.Application.getProcessName().endsWith(":game"))return;
        LogStore.stop();android.os.Process.killProcess(android.os.Process.myPid());
    }
}
