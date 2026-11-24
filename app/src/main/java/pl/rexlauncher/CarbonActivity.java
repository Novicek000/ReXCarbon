package pl.rexlauncher;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import org.libsdl.app.SDLActivity;

/** SDL owns the native window/audio lifecycle; launcher owns pad routing and SAF. */
public final class CarbonActivity extends SDLActivity {
    private InputRouter input;
    private TouchPadView osd;
    private File failureFile;
    private final Handler ui=new Handler(Looper.getMainLooper());
    public static boolean available(Context c){return new File(c.getApplicationInfo().nativeLibraryDir,"libcarbon_europe.so").isFile();}
    @Override protected org.libsdl.app.SDLSurface createSDLSurface(Context context){
        if(GraphicsOptions.effect(getIntent())==0)return super.createSDLSurface(context);
        return new PostFxSurface(context,getIntent(),message->{
            if(!isFinishing()){
                setResult(RESULT_CANCELED,new android.content.Intent().putExtra("error",getString(R.string.filter_error,message)));
                finish();
            }
        });
    }
    @Override protected String[] getLibraries(){return new String[]{"c++_shared","SDL3","rex_android_gpu_driver","rexruntime","rexgpu-xenos","carbon_europe"};}
    @Override protected String[] getArguments(){
        File user=new File(getFilesDir(),"carbon-user");File logs=new File(getFilesDir(),"logs");
        user.mkdirs();
        int[] gameSize=GraphicsOptions.inputSize(getIntent());
        return new String[]{"--game_data_root="+getIntent().getStringExtra("gameRoot"),
            "--user_data_root="+user.getAbsolutePath(),"--cache_root="+new File(user,"cache").getAbsolutePath(),
            "--log_file="+new File(logs,"carbon-"+android.os.Process.myPid()+".log").getAbsolutePath(),
            "--android_native_lib_dir="+getApplicationInfo().nativeLibraryDir,
            "--android_tmp_dir="+getCacheDir().getAbsolutePath(),
            "--android_gpu_driver_dir="+getIntent().getStringExtra("driverDir"),
            "--android_gpu_driver_name="+getIntent().getStringExtra("driverName"),
            "--gpu_plugin=xenos","--fullscreen=true","--headless=true",
            "--video_mode_width="+gameSize[0],
            "--video_mode_height="+gameSize[1],
            "--video_mode_refresh_rate="+VideoOptions.hz(getIntent()),"--vsync=true",
            "--log_level="+(getIntent().getBooleanExtra("detailedLogs",false)?"info":"warn"),
            "--log_noisy=false",
            "--swap_post_effect="+GraphicsOptions.aa(getIntent()),
            "--anisotropic_override="+GraphicsOptions.anisotropy(getIntent()),
            "--resolution_scale="+GraphicsOptions.scale(getIntent()),
            "--present_letterbox="+(getIntent().getIntExtra("scale",0)!=1),
            "--vulkan_require_geometry_shader=false","--vulkan_require_fill_mode_non_solid=false"};
    }
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(AppLocale.wrap(base));}
    @Override protected void onCreate(Bundle saved){
        setRequestedOrientation(VideoOptions.orientation(getIntent()));
        super.onCreate(saved);
        if(mBrokenLibraries)return;
        VideoOptions.window(this);
        if(mSurface!=null){VideoOptions.configureSurface(this,mSurface);VideoOptions.observe(this,mSurface.getHolder());}
        failureFile=new File(getFilesDir(),"logs/carbon-launch-"+android.os.Process.myPid()+".error.txt");
        if(failureFile.exists())failureFile.delete();
        CarbonNative.setLaunchReport(failureFile.getAbsolutePath());
        try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(Uri.parse(getIntent().getStringExtra("iso")),"r")){
            if(fd==null)throw new IOException(getString(R.string.cannot_iso));CarbonNative.setIso(fd.getFd());
        }catch(Exception e){
            android.util.Log.e("Carbon","ISO",e);
            setResult(RESULT_CANCELED,new android.content.Intent().putExtra("error","ISO: "+e.getMessage()));
            finish();return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        input=new InputRouter(this,CarbonNative::setPad,(physical,name)->{
            if(osd!=null){osd.clear();osd.setVisibility(physical?View.GONE:View.VISIBLE);}
        });
        osd=new TouchPadView(this,input::touch);
        addContentView(osd,new ViewGroup.LayoutParams(-1,-1));
        Button menu=new Button(this);menu.setText(R.string.menu);menu.setAllCaps(false);
        menu.setOnClickListener(v->finish());
        FrameLayout.LayoutParams button=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);
        addContentView(menu,button);
    }
    @Override public void setOrientationBis(int width,int height,boolean resizable,String hint){
        // Preserve the chosen sensor/fixed-side policy after SDL window hints.
        setRequestedOrientation(VideoOptions.orientation(getIntent()));
    }
    @Override public boolean dispatchKeyEvent(KeyEvent e){return input!=null&&input.key(e)||super.dispatchKeyEvent(e);}
    @Override public boolean dispatchGenericMotionEvent(MotionEvent e){return input!=null&&input.motion(e)||super.dispatchGenericMotionEvent(e);}
    @Override public boolean onGenericMotionEvent(MotionEvent e){return input!=null&&input.motion(e)||super.onGenericMotionEvent(e);}
    @Override protected void onResume(){super.onResume();if(input!=null)input.resume();}
    @Override protected void onPause(){if(osd!=null)osd.clear();if(input!=null)input.stop();super.onPause();}
    @Override public void onWindowFocusChanged(boolean focus){if(!focus&&input!=null){osd.clear();input.neutral();}super.onWindowFocusChanged(focus);}
    @Override public void finish(){
        // Activity results are dispatched by finish(), before onDestroy().
        if(failureFile!=null&&failureFile.isFile()){
            try(BufferedReader reader=new BufferedReader(new FileReader(failureFile))){
                char[] chars=new char[8192];int count=reader.read(chars);
                if(count>0)setResult(RESULT_CANCELED,new android.content.Intent().putExtra("error",new String(chars,0,count).trim()));
            }catch(IOException e){android.util.Log.e("Carbon","Odczyt bledu sesji",e);}
        }
        super.finish();
    }
    @Override protected void onDestroy(){
        if(input!=null)input.stop();
        // A separate watchdog also covers an SDK/SDL teardown blocked on a guest.
        Thread watchdog=new Thread(()->{
            try{Thread.sleep(1800);}catch(InterruptedException ignored){}
            endProcess();
        },"Carbon shutdown");watchdog.setDaemon(true);watchdog.start();
        super.onDestroy();
        if(mSurface instanceof PostFxSurface)((PostFxSurface)mSurface).shutdown();
        endProcess();
    }
    private void endProcess(){
        if(android.app.Application.getProcessName().endsWith(":carbon")){
            LogStore.stop();android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
}
