package pl.rexlauncher;

public final class GraphicsPolicy {
    public static final String[] EFFECTS={"off","fsr1","cas"};
    public static final String[] AA={"none","fxaa","fxaa_extreme"};
    public static final int[] ANISOTROPY={-1,0,2,3,4,5};
    public static final double[] FSR_RATIOS={1.3,1.5,1.7,2.0};
    private GraphicsPolicy(){}
    public static int clamp(int value,int max){return Math.max(0,Math.min(max,value));}
    public static int[] inputSize(int width,int height,int effect,int quality){
        if(!ResolutionPolicy.valid(width,height))return new int[]{1280,720};
        if(effect!=1)return new int[]{width,height};
        double ratio=Math.min(FSR_RATIOS[clamp(quality,3)],Math.min(width/320.0,height/180.0));
        int w=Math.max(320,((int)Math.ceil(width/ratio)+1)/2*2);
        int h=Math.max(180,((int)Math.ceil(height/ratio)+1)/2*2);
        return new int[]{Math.min(width,w),Math.min(height,h)};
    }
}
