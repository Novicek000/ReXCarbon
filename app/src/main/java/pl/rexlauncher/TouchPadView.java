package pl.rexlauncher;

import android.content.Context;
import android.graphics.*;
import android.util.SparseArray;
import android.view.*;
import java.util.*;

/** Vector-drawn Xbox layout, pointer IDs (not indices), two independent sticks. */
public final class TouchPadView extends View {
    private static final class Control {
        final String text; final float x,y,r; final int bit,color,kind;
        Control(String t,float x,float y,float r,int b,int color,int kind) {
            text=t; this.x=x;this.y=y;this.r=r;bit=b;this.color=color;this.kind=kind;
        }
    }
    private static final class Finger { Control control; float x,y; Finger(Control c,float x,float y) { control=c;this.x=x;this.y=y; } }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<Control> controls = new ArrayList<>();
    private final SparseArray<Finger> fingers = new SparseArray<>();
    private final InputRouter.Sink sink;
    private float unit, offsetX, offsetY;
    public TouchPadView(Context c, InputRouter.Sink sink) { super(c); this.sink=sink; setContentDescription(c.getString(R.string.osd_description)); setFocusable(true); }
    private void add(String t,float x,float y,float r,int b,int color,int kind) { controls.add(new Control(t,x,y,r,b,color,kind)); }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) {
        unit=Math.min(w/1000f,h/560f); offsetX=(w-unit*1000)/2;offsetY=(h-unit*560)/2;
        controls.clear();
        int white=0xffe7edf2;
        add("LS",125,325,70,0,white,1); add("RS",690,425,62,0,white,2);
        add("↑",275,385,28,1,white,0);add("↓",275,495,28,2,white,0);
        add("←",220,440,28,4,white,0);add("→",330,440,28,8,white,0);
        add("A",865,455,35,0x1000,0xff63ce66,0);add("B",930,390,35,0x2000,0xfff15b60,0);
        add("X",800,390,35,0x4000,0xff59a6f7,0);add("Y",865,325,35,0x8000,0xffffd05b,0);
        add("LB",90,175,34,0x100,white,0);add("RB",910,175,34,0x200,white,0);
        add("LT",90,90,34,0,white,3);add("RT",910,90,34,0,white,4);
        add("BACK",440,450,30,0x20,white,0);add("START",550,450,30,0x10,white,0);
        add("L3",390,350,25,0x40,white,0);add("R3",590,350,25,0x80,white,0);
        clear();
    }
    public void clear() { fingers.clear(); sink.pad(0,0,0,0,0,0,0); invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.save();canvas.translate(offsetX,offsetY);canvas.scale(unit,unit);
        paint.setTextAlign(Paint.Align.CENTER);paint.setStrokeWidth(2);
        for(Control c:controls) {
            Finger held=null;
            for(int i=0;i<fingers.size();i++) if(fingers.valueAt(i).control==c) held=fingers.valueAt(i);
            paint.setStyle(Paint.Style.FILL);paint.setColor(held==null?0x452e3c49:0x99708291);
            canvas.drawCircle(c.x,c.y,c.r,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setColor(c.color);paint.setAlpha(165);canvas.drawCircle(c.x,c.y,c.r,paint);
            paint.setStyle(Paint.Style.FILL);paint.setColor(c.color);paint.setTextSize(c.text.length()>2?13:24);
            canvas.drawText(c.text,c.x,c.y+8,paint);
            if(c.kind==1||c.kind==2) {
                float dx=held==null?0:held.x-c.x,dy=held==null?0:held.y-c.y;
                float length=(float)Math.hypot(dx,dy), factor=length>c.r?c.r/length:1;
                paint.setColor(0x997b909e);canvas.drawCircle(c.x+dx*factor,c.y+dy*factor,c.r*.38f,paint);
            }
        }
        canvas.restore();
    }
    private Control hit(float x,float y) {
        for(Control c:controls) if(Math.hypot(x-c.x,y-c.y)<=c.r) return c;
        return null;
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        int action=e.getActionMasked(), index=e.getActionIndex();
        if(action==MotionEvent.ACTION_CANCEL) {clear();return true;}
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN) {
            float x=(e.getX(index)-offsetX)/unit,y=(e.getY(index)-offsetY)/unit;
            Control c=hit(x,y);
            if(c!=null) {
                boolean occupied=false;
                for(int i=0;i<fingers.size();i++) if(fingers.valueAt(i).control==c) occupied=true;
                if(!occupied) fingers.put(e.getPointerId(index),new Finger(c,x,y));
            }
        }
        for(int i=0;i<e.getPointerCount();i++) {
            Finger f=fingers.get(e.getPointerId(i));
            if(f!=null) {
                f.x=(e.getX(i)-offsetX)/unit;f.y=(e.getY(i)-offsetY)/unit;
                if(f.control.kind!=1&&f.control.kind!=2) f.control=hit(f.x,f.y);
                if(f.control==null) fingers.remove(e.getPointerId(i));
            }
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP) fingers.remove(e.getPointerId(index));
        emit();invalidate();return true;
    }
    private void emit() {
        int buttons=0,lt=0,rt=0,lx=0,ly=0,rx=0,ry=0;
        for(int i=0;i<fingers.size();i++) {
            Finger f=fingers.valueAt(i);Control c=f.control;buttons|=c.bit;
            if(c.kind==3)lt=255;if(c.kind==4)rt=255;
            if(c.kind==1||c.kind==2) {
                float dx=(f.x-c.x)/c.r,dy=(c.y-f.y)/c.r;
                float length=(float)Math.hypot(dx,dy);if(length>1){dx/=length;dy/=length;}
                if(length<.12f){dx=dy=0;}
                int x=Math.round(dx*(dx<0?32768:32767)),y=Math.round(dy*(dy<0?32768:32767));
                if(c.kind==1){lx=x;ly=y;}else{rx=x;ry=y;}
            }
        }
        sink.pad(buttons,lt,rt,lx,ly,rx,ry);
    }
}
