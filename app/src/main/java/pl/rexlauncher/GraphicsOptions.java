package pl.rexlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public final class GraphicsOptions {
    private GraphicsOptions(){}
    public static int effect(Intent i){return GraphicsPolicy.clamp(i.getIntExtra("imageEffect",0),2);}
    public static int[] inputSize(Intent i){return GraphicsPolicy.inputSize(i.getIntExtra("renderWidth",1280),i.getIntExtra("renderHeight",720),effect(i),i.getIntExtra("fsrQuality",1));}
    public static Intent put(Context c,Intent i){
        SharedPreferences p=Config.prefs(c);
        return i.putExtra("imageEffect",GraphicsPolicy.clamp(p.getInt("imageEffect",0),2))
            .putExtra("fsrQuality",GraphicsPolicy.clamp(p.getInt("fsrQuality",1),3))
            .putExtra("imageSharpness",GraphicsPolicy.clamp(p.getInt("imageSharpness",50),100))
            .putExtra("antialiasing",GraphicsPolicy.clamp(p.getInt("antialiasing",0),2))
            .putExtra("anisotropy",GraphicsPolicy.clamp(p.getInt("anisotropy",3),5))
            .putExtra("internalScale",GraphicsPolicy.clamp(p.getInt("internalScale",0),2));
    }
    public static String aa(Intent i){return GraphicsPolicy.AA[GraphicsPolicy.clamp(i.getIntExtra("antialiasing",0),2)];}
    public static int anisotropy(Intent i){return GraphicsPolicy.ANISOTROPY[GraphicsPolicy.clamp(i.getIntExtra("anisotropy",3),5)];}
    public static int scale(Intent i){return 1+GraphicsPolicy.clamp(i.getIntExtra("internalScale",0),2);}
}
