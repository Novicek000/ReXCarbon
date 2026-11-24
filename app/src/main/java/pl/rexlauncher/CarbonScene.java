package pl.rexlauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Native vector night scene: no bitmap uploads or allocations in the draw loop. */
public final class CarbonScene extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mountain=new Path(),body=new Path(),glass=new Path(),hood=new Path(),road=new Path();
    private final RectF wheel=new RectF();
    private Shader sky,glow;
    private float phase;
    private ValueAnimator animator;
    public CarbonScene(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        sky=new LinearGradient(0,0,w,h,new int[]{0xff0b1721,0xff101820,0xff080b10},null,Shader.TileMode.CLAMP);
        glow=new RadialGradient(w*.24f,h*.64f,Math.max(w*.44f,1),new int[]{0x2457dfd0,0x003c818b},null,Shader.TileMode.CLAMP);
        mountain.reset();mountain.moveTo(0,h*.52f);mountain.lineTo(w*.075f,h*.28f);mountain.lineTo(w*.14f,h*.38f);
        mountain.lineTo(w*.25f,h*.2f);mountain.lineTo(w*.37f,h*.42f);mountain.lineTo(w*.49f,h*.31f);mountain.lineTo(w*.66f,h*.57f);
        mountain.lineTo(w,h*.52f);mountain.lineTo(w,h);mountain.lineTo(0,h);mountain.close();
        body.reset();body.moveTo(23,104);body.lineTo(59,77);body.lineTo(154,60);body.lineTo(216,13);body.cubicTo(243,2,308,2,333,18);
        body.lineTo(398,58);body.lineTo(475,83);body.lineTo(504,117);body.lineTo(490,148);body.lineTo(459,154);
        body.cubicTo(455,89,392,88,382,153);body.lineTo(154,153);body.cubicTo(146,95,83,95,75,151);
        body.lineTo(29,141);body.close();
        glass.reset();glass.moveTo(177,59);glass.lineTo(224,23);glass.cubicTo(251,15,295,15,319,26);
        glass.lineTo(360,59);glass.close();
        hood.reset();hood.moveTo(31,105);hood.lineTo(171,82);hood.lineTo(369,77);hood.lineTo(488,112);
        road.reset();road.moveTo(70,153);road.lineTo(440,153);road.lineTo(640,240);road.lineTo(-100,240);road.close();
    }
    public void animateScene(boolean run){
        if(run&&ValueAnimator.areAnimatorsEnabled()){
            if(animator!=null)return;
            animator=ValueAnimator.ofFloat(0,1);animator.setDuration(8500);animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(a->{phase=(float)a.getAnimatedValue();invalidate();});animator.start();
        }else if(animator!=null){animator.cancel();animator=null;}
    }
    @Override protected void onDetachedFromWindow(){animateScene(false);super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas c){
        p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);p.setShader(sky);c.drawRect(0,0,getWidth(),getHeight(),p);
        p.setShader(glow);c.drawRect(0,0,getWidth(),getHeight(),p);p.setShader(null);
        p.setColor(0xff0b131c);c.drawPath(mountain,p);
        p.setStrokeWidth(1);p.setColor(0x142d737e);
        for(int x=-getHeight();x<getWidth();x+=28)c.drawLine(x,0,x+getHeight(),getHeight(),p);
        float w=getWidth(),h=getHeight();p.setColor(0x155ee6d3);
        for(int i=0;i<7;i++){float y=h*(.55f+i*.07f);c.drawLine(0,y,w*.55f,y-h*.08f,p);}
        // The car occupies the open space in the left-hand hero panel.
        c.save();float scale=w*.44f/530f;c.translate(w*.025f,h*.43f);c.scale(scale,scale);
        p.setColor(0x143cfff1);c.drawPath(road,p);
        p.setColor(0xff151f29);c.drawPath(body,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.1f);p.setColor(0xff63868c);c.drawPath(body,p);
        p.setColor(0xff4cb6bc);c.drawPath(hood,p);p.setStyle(Paint.Style.FILL);p.setColor(0xff09171f);c.drawPath(glass,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);p.setColor(0xff41777e);c.drawPath(glass,p);
        p.setColor(0xff263640);c.drawLine(263,20,265,60,p);c.drawLine(290,81,281,143,p);
        p.setStyle(Paint.Style.FILL);p.setColor(0xff080c12);wheel.set(83,110,143,169);c.drawOval(wheel,p);wheel.set(390,108,451,168);c.drawOval(wheel,p);
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);p.setColor(0xff405461);c.drawCircle(113,140,20,p);c.drawCircle(421,138,20,p);
        p.setStrokeWidth(2);p.setColor(0xff6ce9d7);c.drawLine(41,109,108,103,p);c.drawLine(162,153,375,153,p);
        p.setColor(0xffe06b68);c.drawLine(476,98,492,113,p);
        p.setColor(0x55a5fff0);p.setStrokeWidth(2);float x=-70+phase*650;c.drawLine(x,197,x+110,179,p);c.restore();
        p.setStyle(Paint.Style.FILL);
    }
}
