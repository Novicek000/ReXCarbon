package pl.rexlauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Display;
import android.view.WindowManager;

public final class Config {
    public static final int[] REFRESH_RATES={30,60};
    public static final String[] REFRESH_LABELS={"30 Hz","60 Hz"};
    public static SharedPreferences prefs(Context c){return c.getSharedPreferences("launcher",Context.MODE_PRIVATE);}
    public static int resolution(Context c){return Math.max(0,Math.min(5,prefs(c).getInt("resolution",2)));}
    public static int scale(Context c){return Math.max(0,Math.min(2,prefs(c).getInt("scale",0)));}
    public static int refreshIndex(Context c){return Math.max(0,Math.min(1,prefs(c).getInt("refresh",1)));}
    // 0/1 preserve the previous fixed-side values, 2 follows the landscape sensor.
    public static int orientation(Context c){return Math.max(0,Math.min(2,prefs(c).getInt("orientation",2)));}
    public static int[] screen(Context c){
        Display display=((WindowManager)c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        Display.Mode mode=display.getMode();
        return new int[]{Math.max(mode.getPhysicalWidth(),mode.getPhysicalHeight()),Math.min(mode.getPhysicalWidth(),mode.getPhysicalHeight())};
    }
    public static int[] renderSize(Context c){
        int[] screen=screen(c);
        return ResolutionPolicy.resolve(resolution(c),screen[0],screen[1],prefs(c).getInt("customWidth",1280),prefs(c).getInt("customHeight",720));
    }
    public static String[] resolutionLabels(Context c){
        int[] screen=screen(c);
        return new String[]{"640 × 360","960 × 540","1280 × 720","1920 × 1080",
            c.getString(R.string.resolution_native,screen[0],screen[1]),
            c.getString(R.string.resolution_custom,prefs(c).getInt("customWidth",1280),prefs(c).getInt("customHeight",720))};
    }
    public static String[] scaleLabels(Context c){return new String[]{c.getString(R.string.aspect),c.getString(R.string.stretch),c.getString(R.string.integer)};}
    public static String[] orientationLabels(Context c){return new String[]{c.getString(R.string.landscape),c.getString(R.string.reverse_landscape),c.getString(R.string.sensor)};}
}
