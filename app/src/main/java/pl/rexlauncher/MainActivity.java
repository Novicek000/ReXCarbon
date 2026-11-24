package pl.rexlauncher;

import android.animation.ValueAnimator;
import android.app.*;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.util.ArrayList;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int ISO=10,DRIVER=11,LOGS=12,CARBON=13;
    private static final int INK=0xffe7f0f2,MUTED=0xff93a7af,MINT=0xff76e5d3;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final ArrayList<View> actions=new ArrayList<>();
    private final ArrayList<Button> tabs=new ArrayList<>();
    private LinearLayout page;
    private TextView game,status,readyTitle,readyBody,summary;
    private Button launch,chooseIso;
    private CarbonScene scene;
    private boolean busy;
    private int selectedTab;
    private String message;
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(AppLocale.wrap(base));}
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        // Existing ISO, drivers, and game saves keep their original paths.
        if(!Config.prefs(this).getBoolean("carbonUi2",false))
            Config.prefs(this).edit().putBoolean("carbonUi2",true).putInt("orientation",2).apply();
        if(saved!=null){selectedTab=saved.getInt("tab",0);message=saved.getString("status");}
        getWindow().setStatusBarColor(0xff090e14);getWindow().setNavigationBarColor(0xff090e14);
        immersive();
        FrameLayout root=new FrameLayout(this);scene=new CarbonScene(this);root.addView(scene,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setPadding(dp(22),dp(10),dp(22),dp(10));
        root.addView(shell,new FrameLayout.LayoutParams(-1,-1));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            android.view.DisplayCutout cutout=insets.getDisplayCutout();
            int left=Math.max(insets.getSystemWindowInsetLeft(),cutout==null?0:cutout.getSafeInsetLeft());
            int right=Math.max(insets.getSystemWindowInsetRight(),cutout==null?0:cutout.getSafeInsetRight());
            shell.setPadding(dp(22)+left,dp(10)+insets.getSystemWindowInsetTop(),dp(22)+right,dp(10)+insets.getSystemWindowInsetBottom());return insets;
        });
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.carbon_icon);icon.setContentDescription(getString(R.string.app_name));
        header.addView(icon,new LinearLayout.LayoutParams(dp(30),dp(30)));
        TextView brand=text("ReX Carbon",22,INK);brand.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD_ITALIC));brand.setPadding(dp(10),0,0,0);
        header.addView(brand,new LinearLayout.LayoutParams(0,dp(38),1));brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView edition=text("EUROPE  /  360",10,MINT);edition.setLetterSpacing(.12f);header.addView(edition);
        shell.addView(header,new LinearLayout.LayoutParams(-1,dp(40)));
        LinearLayout columns=new LinearLayout(this);columns.setOrientation(LinearLayout.HORIZONTAL);
        shell.addView(columns,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,-1,1.07f);hp.rightMargin=dp(20);columns.addView(hero,hp);
        TextView kicker=text(getString(R.string.native_port),9,MINT);kicker.setLetterSpacing(.13f);kicker.setPadding(0,dp(9),0,dp(4));hero.addView(kicker);
        boolean compact=getResources().getConfiguration().screenHeightDp<380;
        TextView title=text(getString(R.string.own_night),compact?35:44,INK);title.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD_ITALIC));
        title.setLineSpacing(-dp(5),1);hero.addView(title);
        TextView caption=text(getString(R.string.hero_caption),12,MUTED);hero.addView(caption);
        hero.addView(new View(this),new LinearLayout.LayoutParams(1,0,1));
        TextView imageLabel=text(getString(R.string.game_image),9,MINT);imageLabel.setLetterSpacing(.16f);hero.addView(imageLabel);
        LinearLayout imageRow=new LinearLayout(this);imageRow.setGravity(Gravity.CENTER_VERTICAL);
        game=text("",12,INK);game.setMaxLines(2);game.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        imageRow.addView(game,new LinearLayout.LayoutParams(0,-2,1));
        chooseIso=button("",()->choose(ISO));chooseIso.setTextSize(11);imageRow.addView(chooseIso,new LinearLayout.LayoutParams(-2,dp(42)));
        hero.addView(imageRow,new LinearLayout.LayoutParams(-1,dp(44)));
        launch=button(getString(R.string.launch),()->openGame(false));launch.setTextColor(0xff0a2225);launch.setTextSize(17);
        launch.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD_ITALIC));
        launch.setBackground(ripple(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{0xff88efc7,0xff65cddd}),0x40ffffff));
        hero.addView(launch,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(12),dp(4),dp(12),dp(10));
        panel.setBackground(shape(0xee131e28,0x553a5866,dp(9)));
        columns.addView(panel,new LinearLayout.LayoutParams(0,-1,1));
        LinearLayout navigation=new LinearLayout(this);
        int[] labels={R.string.garage,R.string.display,R.string.drivers,R.string.tools};
        for(int i=0;i<labels.length;i++){
            final int index=i;Button tab=button(getString(labels[i]),()->showPage(index,true));tab.setTextSize(12);
            tab.setPadding(0,0,0,0);navigation.addView(tab,new LinearLayout.LayoutParams(0,dp(44),1));tabs.add(tab);
        }
        panel.addView(navigation);
        View rule=new View(this);rule.setBackgroundColor(0x223a6371);panel.addView(rule,new LinearLayout.LayoutParams(-1,dp(1)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(4),dp(10),dp(4),dp(6));scroll.addView(page);
        panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        status=text("",10,MUTED);status.setMaxLines(1);status.setEllipsize(TextUtils.TruncateAt.END);status.setPadding(0,dp(4),0,0);
        shell.addView(status,new LinearLayout.LayoutParams(-1,dp(22)));
        status.setOnClickListener(v->{if(message!=null)new AlertDialog.Builder(this).setMessage(message).setPositiveButton(android.R.string.ok,null).show();});
        showPage(selectedTab,false);refresh();note(message==null?getString(R.string.choose_first):message);
        if(saved==null) new AppUpdates(this).check();
        if(ValueAnimator.areAnimatorsEnabled()){columns.setAlpha(0);columns.setTranslationY(dp(10));columns.animate().alpha(1).translationY(0).setDuration(360).start();}
    }
    private void immersive(){getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);}
    private TextView text(String value,int sp,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
    private GradientDrawable shape(int color,int stroke,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private Drawable ripple(Drawable background,int color){return new RippleDrawable(android.content.res.ColorStateList.valueOf(color),background,null);}
    private Button button(String value,Runnable action){
        Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextColor(INK);b.setTextSize(13);
        b.setMinHeight(0);b.setMinimumHeight(0);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(12),0,dp(12),0);
        b.setBackground(ripple(shape(0x99203240,0x55354e5c,dp(5)),0x334df8d0));
        b.setOnClickListener(v->{if(!busy)action.run();});return b;
    }
    private void heading(int id){TextView t=text(getString(id),10,MINT);t.setLetterSpacing(.12f);t.setPadding(0,dp(8),0,dp(8));page.addView(t);}
    private void body(int id){TextView t=text(getString(id),12,MUTED);t.setLineSpacing(dp(3),1);t.setPadding(0,0,0,dp(10));page.addView(t);}
    private void action(int id,Runnable run,boolean requiresIso){
        Button b=button(getString(id),run);b.setTag(requiresIso);actions.add(b);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(44));lp.bottomMargin=dp(8);page.addView(b,lp);
    }
    private void showPage(int index,boolean animate){
        selectedTab=index;page.animate().cancel();page.removeAllViews();actions.clear();readyTitle=readyBody=summary=null;
        for(int i=0;i<tabs.size();i++){
            Button t=tabs.get(i);t.setTextColor(i==index?MINT:MUTED);
            t.setBackground(ripple(shape(i==index?0x333f7c7c:0,0,dp(4)),0x224df8d0));
        }
        if(index==0){
            heading(R.string.quick_setup);
            readyTitle=text("",23,INK);readyTitle.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD_ITALIC));page.addView(readyTitle);
            readyBody=text("",12,MUTED);readyBody.setPadding(0,dp(8),0,dp(14));readyBody.setLineSpacing(dp(3),1);page.addView(readyBody);
            summary=text("",13,MINT);summary.setLineSpacing(dp(5),1);page.addView(summary);
            TextView hint=text(getString(R.string.boot_hint),11,MUTED);hint.setPadding(0,dp(14),0,0);page.addView(hint);
        }else if(index==1){
            heading(R.string.resolution);
            Button edit=button(getString(R.string.edit_custom),this::customSize);
            spinner(Config.resolutionLabels(this),Config.resolution(this),"resolution",(pos,previous)->{
                edit.setVisibility(pos==ResolutionPolicy.CUSTOM?View.VISIBLE:View.GONE);
                if(pos==ResolutionPolicy.CUSTOM)customSize(previous);
            });
            LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(-1,dp(40));ep.bottomMargin=dp(4);page.addView(edit,ep);actions.add(edit);
            edit.setVisibility(Config.resolution(this)==ResolutionPolicy.CUSTOM?View.VISIBLE:View.GONE);
            heading(R.string.scaling);spinner(Config.scaleLabels(this),Config.scale(this),"scale",null);
            heading(R.string.refresh);spinner(Config.REFRESH_LABELS,Config.refreshIndex(this),"refresh",null);
            heading(R.string.orientation);spinner(Config.orientationLabels(this),Config.orientation(this),"orientation",null);
            body(R.string.video_hint);
            heading(R.string.image_enhancements);
            spinner(new String[]{getString(R.string.filter_off),"AMD FSR 1 · EASU + RCAS","AMD FidelityFX CAS"},
                GraphicsPolicy.clamp(Config.prefs(this).getInt("imageEffect",0),2),"imageEffect",null);
            heading(R.string.fsr_quality);
            spinner(new String[]{getString(R.string.ultra_quality),getString(R.string.quality),getString(R.string.balanced),getString(R.string.performance)},
                GraphicsPolicy.clamp(Config.prefs(this).getInt("fsrQuality",1),3),"fsrQuality",null);
            final int strength=GraphicsPolicy.clamp(Config.prefs(this).getInt("imageSharpness",50),100);
            TextView strengthLabel=text(getString(R.string.sharpness,strength),12,INK);strengthLabel.setPadding(0,dp(12),0,dp(4));page.addView(strengthLabel);
            SeekBar sharpness=new SeekBar(this);sharpness.setMax(100);sharpness.setProgress(strength);sharpness.setContentDescription(getString(R.string.sharpness,strength));
            sharpness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(SeekBar bar,int value,boolean user){strengthLabel.setText(getString(R.string.sharpness,value));bar.setContentDescription(getString(R.string.sharpness,value));if(user)Config.prefs(MainActivity.this).edit().putInt("imageSharpness",value).apply();}
                public void onStartTrackingTouch(SeekBar bar){}
                public void onStopTrackingTouch(SeekBar bar){Config.prefs(MainActivity.this).edit().putInt("imageSharpness",bar.getProgress()).apply();}
            });page.addView(sharpness,new LinearLayout.LayoutParams(-1,dp(44)));actions.add(sharpness);
            heading(R.string.antialiasing);
            spinner(new String[]{getString(R.string.filter_off),"FXAA","FXAA High"},GraphicsPolicy.clamp(Config.prefs(this).getInt("antialiasing",0),2),"antialiasing",null);
            heading(R.string.anisotropy);
            spinner(new String[]{getString(R.string.game_default),getString(R.string.filter_off),"2×","4×","8×","16×"},GraphicsPolicy.clamp(Config.prefs(this).getInt("anisotropy",3),5),"anisotropy",null);
            heading(R.string.internal_scale);
            spinner(new String[]{"1×","2×","3×"},GraphicsPolicy.clamp(Config.prefs(this).getInt("internalScale",0),2),"internalScale",null);
            body(R.string.filter_hint);
        }else if(index==2){
            heading(R.string.current_driver);
            TextView current=text(Config.prefs(this).getString("driverTitle",getString(R.string.system_driver)),18,INK);current.setPadding(0,0,0,dp(14));page.addView(current);
            action(R.string.import_driver,()->choose(DRIVER),false);
            action(R.string.use_system,()->{Config.prefs(this).edit().remove("driverDir").remove("driverName").remove("driverTitle").apply();showPage(2,false);note(getString(R.string.system_selected));},false);
            body(R.string.driver_hint);
        }else{
            heading(R.string.app_language);
            spinner(new String[]{getString(R.string.system_language),"English","Polski"}, Math.max(0,Math.min(2,Config.prefs(this).getInt("language",0))),"language",(pos,old)->{message=null;recreate();});
            action(R.string.export_logs,this::exportLogs,false);action(R.string.inspect_iso,this::inspect,true);
            action(R.string.diagnostics,()->openGame(true),true);
            Switch logs=new Switch(this);logs.setText(R.string.detailed_logs);logs.setTextSize(12);logs.setTextColor(INK);
            logs.setChecked(Config.prefs(this).getBoolean("detailedLogs",false));logs.setPadding(0,dp(8),0,dp(8));
            logs.setOnCheckedChangeListener((v,on)->Config.prefs(this).edit().putBoolean("detailedLogs",on).apply());page.addView(logs);actions.add(logs);
            body(R.string.logs_hint);body(R.string.build_info);
        }
        refresh();
        if(animate&&ValueAnimator.areAnimatorsEnabled()){
            page.setAlpha(0);page.setTranslationX(dp(12));page.animate().alpha(1).translationX(0).setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        }else{page.setAlpha(1);page.setTranslationX(0);}
    }
    private interface Selection{void choose(int index,int previous);}
    private void spinner(String[] values,int selected,String key,Selection callback){
        Spinner s=new Spinner(this);s.setBackground(ripple(shape(0xff1c2b38,0x55465a66,dp(4)),0x225cdbc5));
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,values){
            @Override public View getView(int p,View old,ViewGroup parent){TextView t=(TextView)super.getView(p,old,parent);t.setTextSize(13);t.setTextColor(INK);t.setSingleLine(true);t.setEllipsize(TextUtils.TruncateAt.END);t.setPadding(dp(10),0,dp(24),0);return t;}
            @Override public View getDropDownView(int p,View old,ViewGroup parent){TextView t=(TextView)super.getDropDownView(p,old,parent);t.setTextColor(INK);t.setTextSize(13);t.setBackgroundColor(0xff1c2b38);t.setPadding(dp(12),dp(12),dp(12),dp(12));return t;}
        };
        s.setAdapter(adapter);s.setSelection(selected);s.setContentDescription(values[selected]);
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            private int last=selected;
            public void onItemSelected(AdapterView<?> parent,View v,int position,long id){
                if(position==last)return;int previous=last;last=position;
                Config.prefs(MainActivity.this).edit().putInt(key,position).apply();
                s.setContentDescription(values[position]);if(callback!=null)callback.choose(position,previous);
            }
            public void onNothingSelected(AdapterView<?> p){}
        });page.addView(s,new LinearLayout.LayoutParams(-1,dp(44)));actions.add(s);
    }
    private void customSize(){
        customSize(Config.resolution(this));
    }
    private void customSize(int previous){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(24),dp(8),dp(24),dp(8));
        EditText width=new EditText(this),height=new EditText(this);
        width.setHint(R.string.width);height.setHint(R.string.height);width.setInputType(2);height.setInputType(2);
        width.setSingleLine();height.setSingleLine();width.setSelectAllOnFocus(true);height.setSelectAllOnFocus(true);
        width.setText(Integer.toString(Config.prefs(this).getInt("customWidth",1280)));height.setText(Integer.toString(Config.prefs(this).getInt("customHeight",720)));
        width.setContentDescription(getString(R.string.width));height.setContentDescription(getString(R.string.height));
        box.addView(text(getString(R.string.width),12,MUTED));box.addView(width);
        box.addView(text(getString(R.string.height),12,MUTED));box.addView(height);
        ScrollView customScroll=new ScrollView(this);customScroll.addView(box);
        Runnable cancel=()->{if(previous!=ResolutionPolicy.CUSTOM){Config.prefs(this).edit().putInt("resolution",previous).apply();showPage(1,false);}};
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(R.string.custom).setView(customScroll).setNegativeButton(R.string.cancel,(d,which)->cancel.run()).setPositiveButton(R.string.apply,null).create();
        dialog.setOnCancelListener(d->cancel.run());
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            int w=0,h=0;try{w=Integer.parseInt(width.getText().toString().trim());h=Integer.parseInt(height.getText().toString().trim());}catch(NumberFormatException ignored){}
            if(!ResolutionPolicy.valid(w,h)){width.setError(getString(R.string.bad_size));return;}
            Config.prefs(this).edit().putInt("customWidth",w).putInt("customHeight",h).putInt("resolution",ResolutionPolicy.CUSTOM).apply();
            dialog.dismiss();showPage(1,false);note(getString(R.string.saved_size));
        }));dialog.show();
    }
    private void choose(int request){startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION),request);}
    private void exportLogs(){startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"rex-carbon-"+System.currentTimeMillis()+".txt"),LOGS);}
    private void refresh(){
        if(game==null)return;
        boolean selected=Config.prefs(this).contains("iso");game.setText(Config.prefs(this).getString("isoTitle",getString(R.string.no_iso)));
        chooseIso.setText(selected?R.string.change_iso:R.string.choose_iso);chooseIso.setEnabled(!busy);
        boolean available=CarbonActivity.available(this)||NativeBridge.hasGame();launch.setEnabled(!busy&&selected&&available);launch.setAlpha(launch.isEnabled()?1:.45f);
        for(View v:actions)v.setEnabled(!busy&&(!Boolean.TRUE.equals(v.getTag())||selected));
        for(Button tab:tabs)tab.setEnabled(!busy);
        if(readyTitle!=null){readyTitle.setText(selected?R.string.ready:R.string.setup);readyBody.setText(selected?R.string.ready_body:R.string.setup_body);
            int[] size=Config.renderSize(this);summary.setText(size[0]+" × "+size[1]+"  /  "+Config.REFRESH_LABELS[Config.refreshIndex(this)]+"\n"+Config.prefs(this).getString("driverTitle",getString(R.string.system_driver)));}
    }
    private void openGame(boolean diagnostic){
        if(!diagnostic&&CarbonActivity.available(this)){
            Uri uri=Uri.parse(Config.prefs(this).getString("iso",""));
            work(()->{String root=CarbonInstaller.prepare(this,uri);
                runOnUiThread(()->{if(!isDestroyed())startActivityForResult(launchIntent(CarbonActivity.class,false).putExtra("gameRoot",root),CARBON);});return getString(R.string.prepared);});
        }else startActivity(launchIntent(GameActivity.class,diagnostic));
    }
    private Intent launchIntent(Class<?> activity,boolean diagnostic){
        int[] size=Config.renderSize(this);
        return GraphicsOptions.put(this,new Intent(this,activity).putExtra("diagnostic",diagnostic).putExtra("iso",Config.prefs(this).getString("iso",""))
            .putExtra("driverDir",Config.prefs(this).getString("driverDir","")).putExtra("driverName",Config.prefs(this).getString("driverName",""))
            .putExtra("renderWidth",size[0]).putExtra("renderHeight",size[1]).putExtra("refreshRate",Config.REFRESH_RATES[Config.refreshIndex(this)])
            .putExtra("orientation",Config.orientation(this)).putExtra("scale",Config.scale(this)).putExtra("detailedLogs",Config.prefs(this).getBoolean("detailedLogs",false)));
    }
    private void inspect(){Uri uri=Uri.parse(Config.prefs(this).getString("iso",""));work(()->{try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r")){if(fd==null)throw new java.io.IOException(getString(R.string.cannot_iso));return NativeBridge.inspectIso(fd.getFd());}});}
    private void note(String text){message=text;if(status!=null)status.setText(text);}
    private void work(Callable<String> task){busy=true;refresh();note(getString(R.string.working));io.execute(()->{
        String answer;try{answer=task.call();}catch(Exception e){android.util.Log.e("ReXCarbon","Operation failed",e);answer=getString(R.string.error,e.getMessage());}
        final String result=answer;runOnUiThread(()->{if(isDestroyed())return;busy=false;showPage(selectedTab,false);note(result);});
    });}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==74){if(getPackageManager().canRequestPackageInstalls())new AppUpdates(this).install();return;}
        if(request==CARBON){note(data!=null&&data.hasExtra("error")?getString(R.string.start_failed,data.getStringExtra("error")):getString(R.string.session_end));return;}
        if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==ISO){try{
            getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);
            String name=uri.getLastPathSegment();try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())name=c.getString(0);}
            String old=Config.prefs(this).getString("iso","");
            if(!Config.prefs(this).edit().putString("iso",uri.toString()).putString("isoTitle",name).commit())throw new java.io.IOException(getString(R.string.cannot_save_iso));
            if(!old.isEmpty()&&!old.equals(uri.toString()))try{getContentResolver().releasePersistableUriPermission(Uri.parse(old),Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}
            refresh();inspect();
        }catch(Exception e){note("SAF: "+e.getMessage());}}
        else if(request==DRIVER)work(()->getString(R.string.installed,DriverStore.install(this,uri)));
        else if(request==LOGS)work(()->{LogStore.export(this,uri);return getString(R.string.logs_saved);});
    }
    @Override protected void onResume(){super.onResume();if(scene!=null)scene.animateScene(true);immersive();refresh();}
    @Override protected void onPause(){if(scene!=null)scene.animateScene(false);super.onPause();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)immersive();}
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("tab",selectedTab);out.putString("status",message);super.onSaveInstanceState(out);}
    @Override protected void onDestroy(){if(scene!=null)scene.animateScene(false);io.shutdown();super.onDestroy();}
}
