package pl.rexlauncher;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

public final class UpdateProvider extends ContentProvider {
    static final Uri URI = Uri.parse("content://pl.rexlauncher.updates/update.apk");
    private File apk() { return new File(getContext().getCacheDir(), "update.apk"); }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/vnd.android.package-archive"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!URI.equals(uri) || !"r".equals(mode)) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(apk(), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String where,String[] args,String sort) {
        if (!URI.equals(uri)) throw new IllegalArgumentException();
        String[] cols = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE} : projection;
        MatrixCursor result = new MatrixCursor(cols);
        MatrixCursor.RowBuilder row = result.newRow();
        for (String col : cols) row.add(OpenableColumns.SIZE.equals(col) ? apk().length() :
            OpenableColumns.DISPLAY_NAME.equals(col) ? "ReX-Carbon.apk" : null);
        return result;
    }
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
