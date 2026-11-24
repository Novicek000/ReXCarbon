package pl.rexlauncher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

final class AppUpdates {
    private static final String REPO = "Novicek000/ReXCarbon";
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static final int MAX_APK = 256 * 1024 * 1024;
    private final Activity activity;
    AppUpdates(Activity activity) { this.activity = activity; }

    static boolean newer(String tag, String current) {
        if (!tag.matches("v[0-9]{1,8}\\.[0-9]{1,8}\\.[0-9]{1,8}") ||
            !current.matches("[0-9]{1,8}\\.[0-9]{1,8}\\.[0-9]{1,8}")) return false;
        String[] a = tag.substring(1).split("\\."), b = current.split("\\.");
        for (int i=0;i<3;i++) {
            int cmp = Integer.compare(Integer.parseInt(a[i]),Integer.parseInt(b[i]));
            if (cmp != 0) return cmp > 0;
        }
        return false;
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("User-Agent","ReXCarbon-Android");
        return conn;
    }
    private void ui(Runnable r) {
        activity.runOnUiThread(()->{if (!activity.isFinishing() && !activity.isDestroyed()) r.run();});
    }
    void check() {
        worker.execute(()->{
            try {
                HttpURLConnection conn = open("https://api.github.com/repos/"+REPO+"/releases/latest");
                JSONObject release;
                try {
                    if (conn.getResponseCode()==404) return;
                    try (InputStream in=conn.getInputStream(); ByteArrayOutputStream buf=new ByteArrayOutputStream()) {
                        byte[] block=new byte[8192]; int n;
                        while ((n=in.read(block))!=-1) {
                            if(buf.size()+n>1024*1024) throw new IOException("Release metadata");
                            buf.write(block,0,n);
                        }
                        release=new JSONObject(buf.toString("UTF-8"));
                    }
                } finally {conn.disconnect();}
                String tag=release.getString("tag_name");
                if (release.optBoolean("draft") || release.optBoolean("prerelease") ||
                    !newer(tag, installed().versionName)) return;
                String name="ReX-Carbon-"+tag.substring(1)+"-arm64.apk";
                String url="https://github.com/"+REPO+"/releases/download/"+tag+"/"+name;
                JSONArray assets=release.getJSONArray("assets");
                for(int i=0;i<assets.length();i++) {
                    JSONObject asset=assets.getJSONObject(i);
                    if (!url.equals(asset.optString("browser_download_url"))) continue;
                    long size=asset.getLong("size");
                    String digest=asset.optString("digest");
                    if(size<=0 || size>MAX_APK || !digest.matches("sha256:[a-fA-F0-9]{64}")) continue;
                    ui(()->new AlertDialog.Builder(activity).setTitle(R.string.update_title)
                        .setMessage(activity.getString(R.string.update_prompt,tag))
                        .setNegativeButton(android.R.string.cancel,null)
                        .setPositiveButton(R.string.update_download,(d,w)->download(url,tag,size,digest.substring(7))).show());
                    return;
                }
            } catch(Exception e){Log.w("ReXUpdates","Release check",e);}
        });
    }
    private PackageInfo installed() throws PackageManager.NameNotFoundException {
        return activity.getPackageManager().getPackageInfo(activity.getPackageName(),PackageManager.GET_SIGNING_CERTIFICATES);
    }
    private void download(String url,String tag,long size,String expected) {
        Toast.makeText(activity,R.string.update_progress,Toast.LENGTH_LONG).show();
        worker.execute(()->{
            File tmp=new File(activity.getCacheDir(),"update-part.apk");
            File apk=new File(activity.getCacheDir(),"update.apk");
            try {
                MessageDigest hash=MessageDigest.getInstance("SHA-256");
                HttpURLConnection conn=open(url);
                long total=0;
                try(InputStream in=conn.getInputStream(); OutputStream out=new FileOutputStream(tmp)){
                    byte[] block=new byte[65536]; int n;
                    while((n=in.read(block))!=-1){
                        total+=n;
                        if(total>size) throw new IOException("APK length");
                        out.write(block,0,n);hash.update(block,0,n);
                    }
                } finally { conn.disconnect(); }
                StringBuilder hex=new StringBuilder();
                for(byte b:hash.digest()) hex.append(String.format(Locale.ROOT,"%02x",b & 255));
                if(total!=size || !expected.equalsIgnoreCase(hex.toString())) throw new IOException("APK digest");
                PackageInfo candidate=activity.getPackageManager().getPackageArchiveInfo(tmp.getPath(),PackageManager.GET_SIGNING_CERTIFICATES);
                PackageInfo local=installed();
                if(candidate==null || !local.packageName.equals(candidate.packageName) ||
                    !tag.equals("v"+candidate.versionName) || candidate.getLongVersionCode()<=local.getLongVersionCode() ||
                    candidate.signingInfo==null || !Arrays.equals(local.signingInfo.getApkContentsSigners(),candidate.signingInfo.getApkContentsSigners()))
                    throw new IOException("APK identity");
                if(!tmp.renameTo(apk)) throw new IOException("APK cache");
                ui(this::install);
            } catch(Exception e){
                Log.e("ReXUpdates","Download",e);
                ui(()->Toast.makeText(activity,R.string.update_error,Toast.LENGTH_LONG).show());
            } finally {tmp.delete();}
        });
    }
    void install() {
        try {
            if(!activity.getPackageManager().canRequestPackageInstalls()){
                activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:"+activity.getPackageName())),74);
                return;
            }
            activity.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(UpdateProvider.URI,
                "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch(ActivityNotFoundException | SecurityException e){
            Log.w("ReXUpdates","Installer",e);
            Toast.makeText(activity,R.string.update_error,Toast.LENGTH_LONG).show();
        }
    }
}
