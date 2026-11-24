package pl.rexlauncher;

/** Pure validation shared by the UI, launch configuration and tests. */
public final class ResolutionPolicy {
    public static final int NATIVE=4,CUSTOM=5;
    private static final int[][] PRESETS={{640,360},{960,540},{1280,720},{1920,1080}};
    private ResolutionPolicy() {}
    public static boolean valid(int width,int height){
        return width>=320&&height>=180&&width<=8192&&height<=8192&&width>=height
                &&(long)width*height<=16_777_216L;
    }
    public static int[] resolve(int index,int screenWidth,int screenHeight,int customWidth,int customHeight){
        if(index==NATIVE){
            int w=Math.max(screenWidth,screenHeight),h=Math.min(screenWidth,screenHeight);
            if(valid(w,h))return new int[]{w,h};
        }else if(index==CUSTOM&&valid(customWidth,customHeight))return new int[]{customWidth,customHeight};
        if(index>=0&&index<PRESETS.length)return PRESETS[index].clone();
        return PRESETS[2].clone();
    }
    public static int[] fit(int width,int height,int availableWidth,int availableHeight,int mode){
        if(width<=0||height<=0||availableWidth<=0||availableHeight<=0)return new int[]{1,1};
        if(mode==1)return new int[]{availableWidth,availableHeight};
        double factor=Math.min((double)availableWidth/width,(double)availableHeight/height);
        if(mode==2&&factor>=1)factor=Math.floor(factor);
        return new int[]{Math.max(1,Math.min(availableWidth,(int)Math.round(width*factor))),
            Math.max(1,Math.min(availableHeight,(int)Math.round(height*factor)))};
    }
}
