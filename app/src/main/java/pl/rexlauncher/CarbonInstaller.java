package pl.rexlauncher;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.security.MessageDigest;

public final class CarbonInstaller {
    public static final String XEX_SHA256="f72ce8d9bab3284e4555a1675d19df13f9f665c0ade014a7666a924dce7e10a1";
    private CarbonInstaller() {}
    public static String prepare(Context context,Uri iso) throws Exception {
        byte[] xex;
        try(ParcelFileDescriptor fd=context.getContentResolver().openFileDescriptor(iso,"r")) {
            if(fd==null)throw new IOException(context.getString(R.string.cannot_iso));
            xex=NativeBridge.readXex(fd.getFd());
        }
        byte[] digest=MessageDigest.getInstance("SHA-256").digest(xex);
        StringBuilder hash=new StringBuilder();
        for(byte b:digest)hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        if(!XEX_SHA256.equals(hash.toString()))throw new IOException(context.getString(R.string.xex_mismatch,hash.toString()));
        File root=new File(context.getFilesDir(),"carbon-europe");
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException(context.getString(R.string.carbon_folder));
        File bootstrap=new File(root,"default.xex");
        if(bootstrap.isFile()&&bootstrap.length()==xex.length){
            MessageDigest cached=MessageDigest.getInstance("SHA-256");
            try(InputStream in=new BufferedInputStream(new FileInputStream(bootstrap))){
                byte[] buffer=new byte[65536];int count;
                while((count=in.read(buffer))!=-1)cached.update(buffer,0,count);
            }
            if(MessageDigest.isEqual(cached.digest(),digest)){
                android.util.Log.i("ReXCarbon","Verified XEX bootstrap reused; no rewrite/fsync");
                return root.getAbsolutePath();
            }
        }
        File temporary=File.createTempFile("default-",".xex",root);
        try {
            try(FileOutputStream out=new FileOutputStream(temporary)){out.write(xex);out.getFD().sync();}
            // This is our own runtime bootstrap file, never the user's original ISO.
            android.system.Os.rename(temporary.getAbsolutePath(),bootstrap.getAbsolutePath());
        } finally {if(temporary.exists())temporary.delete();}
        return root.getAbsolutePath();
    }
}
