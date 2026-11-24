package pl.rexlauncher;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Process;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class LogStore {
    private static File root;
    private static java.lang.Process capture;
    private LogStore() {}
    public static void stop() {
        if (capture != null) { capture.destroy(); capture = null; }
    }
    public static void start(File directory) {
        root = directory;
        try {
            capture = new ProcessBuilder("logcat", "-b", "all", "--pid=" + Process.myPid(), "-v", "threadtime")
                    .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(
                            new File(root, "logcat-" + System.currentTimeMillis() + ".log"))).start();
        } catch (IOException e) { android.util.Log.e("ReXLogs", "Logcat capture unavailable", e); }
    }
    public static void export(Context c, Uri target) throws Exception {
        OutputStream raw = c.getContentResolver().openOutputStream(target, "wt");
        if (raw == null) throw new IOException(c.getString(R.string.log_target));
        try (OutputStream out = new BufferedOutputStream(raw)) {
            String version=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName;
            write(out, "ReX Carbon " + version + "\n" + new Date() + "\n" + Build.MANUFACTURER + " " + Build.MODEL +
                    " API " + Build.VERSION.SDK_INT + " ABI " + Arrays.toString(Build.SUPPORTED_ABIS) +
                    "\nPID=" + Process.myPid() + "\nGame ABI module=" + NativeBridge.hasGame() +
                    "\nCarbon Europe module=" + CarbonActivity.available(c) +
                    "\nSettings=" + Config.prefs(c).getAll() + "\n");
            File snapshot = File.createTempFile("logcat-", ".txt", c.getCacheDir());
            try {
                java.lang.Process dump = new ProcessBuilder("logcat", "-d", "-b", "all",
                        "--pid=" + Process.myPid(), "-v", "threadtime")
                        .redirectErrorStream(true).redirectOutput(snapshot).start();
                if (!dump.waitFor(8, TimeUnit.SECONDS)) {
                    dump.destroyForcibly();
                    dump.waitFor(2, TimeUnit.SECONDS);
                    write(out, "\n[logcat dump timeout; streaming capture below]\n");
                }
                write(out, "\n=== Current process logcat ===\n");
                copySnapshot(snapshot, out);
            } catch (IOException e) { write(out, "\nlogcat: " + e + "\n"); }
            finally { if (!snapshot.delete()) snapshot.deleteOnExit(); }
            appendDirectory(root, out, root.getCanonicalPath() + File.separator);
        }
    }
    private static void appendDirectory(File folder, OutputStream out, String prefix) throws IOException {
        File[] files = folder.listFiles();
        if (files == null) return;
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File file : files) {
            if (!file.getCanonicalPath().startsWith(prefix)) continue;
            if (file.isDirectory()) appendDirectory(file, out, prefix);
            else {
                write(out, "\n=== " + file.getName() + " ===\n");
                copySnapshot(file, out);
            }
        }
    }
    private static void copySnapshot(File file, OutputStream out) throws IOException {
        // Fix the end offset: a live logger must not make export run forever.
        long remaining = file.length();
        byte[] buffer = new byte[65536];
        try (InputStream in = new FileInputStream(file)) {
            while (remaining > 0) {
                int n = in.read(buffer, 0, (int)Math.min(buffer.length, remaining));
                if (n < 0) break;
                out.write(buffer, 0, n); remaining -= n;
            }
        }
    }
    private static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }
}
