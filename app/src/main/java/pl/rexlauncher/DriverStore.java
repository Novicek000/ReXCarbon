package pl.rexlauncher;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

public final class DriverStore {
    private static final long LIMIT = 256L * 1024 * 1024;
    private DriverStore() {}

    public static String install(Context context, Uri uri) throws Exception {
        File root = new File(context.getFilesDir(), "drivers");
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException(context.getString(R.string.driver_folder));
        File stage = new File(root, UUID.randomUUID().toString());
        if (!stage.mkdir()) throw new IOException(context.getString(R.string.install_folder));
        boolean committed = false;
        try {
            String prefix = stage.getCanonicalPath() + File.separator;
            long total = 0;
            int count = 0;
            Set<String> names = new HashSet<>();
            InputStream source = context.getContentResolver().openInputStream(uri);
            if (source == null) throw new IOException(context.getString(R.string.cannot_zip));
            try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(source))) {
                ZipEntry entry;
                byte[] buffer = new byte[65536];
                while ((entry = zip.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (++count > 256 || name.indexOf('\\') >= 0 || name.startsWith("/") ||
                            name.indexOf('\0') >= 0) throw new IOException(context.getString(R.string.zip_path));
                    File target = new File(stage, name);
                    String canonical = target.getCanonicalPath();
                    if (!canonical.startsWith(prefix) || !names.add(canonical))
                        throw new IOException(context.getString(R.string.zip_duplicate));
                    if (entry.isDirectory()) {
                        if (!target.isDirectory() && !target.mkdirs()) throw new IOException(context.getString(R.string.zip_folder));
                        continue;
                    }
                    File parent = target.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException(context.getString(R.string.zip_folder));
                    long size = 0;
                    try (FileOutputStream out = new FileOutputStream(target)) {
                        int n;
                        while ((n = zip.read(buffer)) != -1) {
                            total += n; size += n;
                            if (total > LIMIT || size > LIMIT / 2) throw new IOException(context.getString(R.string.zip_large));
                            out.write(buffer, 0, n);
                        }
                        out.getFD().sync();
                    }
                }
            }
            File metaFile = new File(stage, "meta.json");
            if (!metaFile.isFile() || metaFile.length() > 65536) throw new IOException(context.getString(R.string.zip_meta));
            JSONObject meta;
            try (InputStream in = new FileInputStream(metaFile)) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096]; int n;
                while ((n = in.read(chunk)) != -1) bytes.write(chunk, 0, n);
                meta = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            }
            int minApi = meta.optInt("minApi", 28);
            if (minApi > Build.VERSION.SDK_INT) throw new IOException(context.getString(R.string.driver_api,minApi));
            String library = meta.getString("libraryName");
            if (!library.matches("[A-Za-z0-9_.+-]+\\.so")) throw new IOException(context.getString(R.string.driver_library));
            File lib = new File(stage, library);
            byte[] elf = new byte[20];
            try (DataInputStream in = new DataInputStream(new FileInputStream(lib))) { in.readFully(elf); }
            if (elf[0] != 0x7f || elf[1] != 'E' || elf[2] != 'L' || elf[3] != 'F' ||
                    elf[4] != 2 || elf[5] != 1 || (elf[18] & 255) != 183 || elf[19] != 0)
                throw new IOException(context.getString(R.string.driver_abi));
            String title = meta.optString("name", library) + " " + meta.optString("version", "");
            if (!Config.prefs(context).edit().putString("driverDir", stage.getAbsolutePath())
                    .putString("driverName", library).putString("driverTitle", title.trim()).commit())
                throw new IOException(context.getString(R.string.driver_save));
            committed = true;
            return title.trim();
        } finally {
            if (!committed) removeStage(stage);
        }
    }
    // Only rolls back files created by this installation attempt.
    private static void removeStage(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File child : children) removeStage(child);
        if (f.exists() && !f.delete()) android.util.Log.w("ReXDriver", "Rollback: " + f);
    }
}
