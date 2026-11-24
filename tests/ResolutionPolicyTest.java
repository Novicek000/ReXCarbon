import pl.rexlauncher.ResolutionPolicy;
import java.util.Arrays;

public final class ResolutionPolicyTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void size(int[] actual,int w,int h,String message){check(Arrays.equals(actual,new int[]{w,h}),message+" "+Arrays.toString(actual));}
    public static void main(String[] args){
        int[][] presets={{640,360},{960,540},{1280,720},{1920,1080}};
        for(int i=0;i<presets.length;i++)size(ResolutionPolicy.resolve(i,2400,1080,1,1),presets[i][0],presets[i][1],"existing preset "+i);
        size(ResolutionPolicy.resolve(4,1440,3040,1,1),3040,1440,"native portrait device axes");
        size(ResolutionPolicy.resolve(4,3040,1440,1,1),3040,1440,"native landscape device axes");
        size(ResolutionPolicy.resolve(5,2400,1080,2560,1080),2560,1080,"custom ultrawide");
        size(ResolutionPolicy.resolve(5,2400,1080,Integer.MAX_VALUE,1080),1280,720,"invalid stored custom fallback");
        check(ResolutionPolicy.valid(320,180),"minimum dimensions");
        check(ResolutionPolicy.valid(8192,2048),"maximum pixel budget");
        for(int[] value:new int[][]{{0,0},{319,180},{320,179},{1080,1920},{8193,1080},{8192,8192},{Integer.MAX_VALUE,Integer.MAX_VALUE},{-640,360}})
            check(!ResolutionPolicy.valid(value[0],value[1]),"reject invalid/overflowing dimensions");
        size(ResolutionPolicy.fit(1280,720,2340,1080,0),1920,1080,"aspect on wide phone");
        size(ResolutionPolicy.fit(1280,720,2340,1080,1),2340,1080,"stretch");
        size(ResolutionPolicy.fit(1280,720,2340,1080,2),1280,720,"integer letterbox");
        size(ResolutionPolicy.fit(640,360,2560,1440,2),2560,1440,"integer 4x");
        size(ResolutionPolicy.fit(1280,720,1000,480,2),853,480,"integer fallback below native");
        size(ResolutionPolicy.fit(1280,720,0,0,0),1,1,"unmeasured layout");
        int[] original=ResolutionPolicy.resolve(0,1,1,1,1);original[0]=7;
        size(ResolutionPolicy.resolve(0,1,1,1,1),640,360,"preset immutability");
        System.out.println("PASS: "+checks+" resolution validation, migration, native/custom and surface scaling checks");
    }
}
