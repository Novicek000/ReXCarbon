import pl.rexlauncher.GraphicsPolicy;
import java.util.Arrays;
public final class GraphicsPolicyTest {
    private static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void size(int[] actual,int width,int height){check(Arrays.equals(actual,new int[]{width,height}),Arrays.toString(actual));}
    public static void main(String[] args){
        size(GraphicsPolicy.inputSize(1920,1080,1,0),1478,832);
        size(GraphicsPolicy.inputSize(1920,1080,1,1),1280,720);
        size(GraphicsPolicy.inputSize(1920,1080,1,2),1130,636);
        size(GraphicsPolicy.inputSize(1920,1080,1,3),960,540);
        size(GraphicsPolicy.inputSize(2340,1080,1,1),1560,720);
        size(GraphicsPolicy.inputSize(320,180,1,3),320,180);
        size(GraphicsPolicy.inputSize(1280,720,0,3),1280,720);
        size(GraphicsPolicy.inputSize(1280,720,2,3),1280,720);
        size(GraphicsPolicy.inputSize(Integer.MAX_VALUE,1080,1,3),1280,720);
        size(GraphicsPolicy.inputSize(1920,1080,1,99),960,540);
        size(GraphicsPolicy.inputSize(1920,1080,1,-5),1478,832);
        size(GraphicsPolicy.inputSize(320,320,1,3),320,320);
        size(GraphicsPolicy.inputSize(321,181,1,3),320,181);
        check(GraphicsPolicy.ANISOTROPY[3]==3,"previous 4x default preserved");
        check(GraphicsPolicy.ANISOTROPY[0]==-1&&GraphicsPolicy.ANISOTROPY[5]==5,"game default and 16x native flags");
        check(GraphicsPolicy.AA[0].equals("none")&&GraphicsPolicy.AA[2].equals("fxaa_extreme"),"native anti-aliasing flags");
        check(GraphicsPolicy.clamp(-1,100)==0&&GraphicsPolicy.clamp(130,100)==100,"sharpness bounds");
        for(int w=320;w<=3840;w+=137)for(int quality=0;quality<4;quality++){
            int h=Math.max(180,w*9/16);int[] input=GraphicsPolicy.inputSize(w,h,1,quality);
            check(input[0]<=w&&input[1]<=h,"FSR never enlarges its input");
            check(input[0]>=320&&input[1]>=180,"input minimums");
            check((input[0]%2==0||input[0]==w)&&(input[1]%2==0||input[1]==h),"even input dimensions");
            check(input[0]<=w&&input[0]*2>=w&&input[1]*2>=h,"EASU 1-2x upscaling per axis");
        }
        System.out.println("PASS: "+checks+" graphics policy checks: FSR ratios, source dimensions, bounds, unchanged Off/CAS, native flag mappings");
    }
}
