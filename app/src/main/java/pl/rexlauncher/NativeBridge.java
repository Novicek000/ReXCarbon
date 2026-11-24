package pl.rexlauncher;

import android.view.Surface;

public final class NativeBridge {
    static { System.loadLibrary("rexlauncher"); }
    private NativeBridge() {}
    public static native void init(String logs, String nativeLibraries, String cache);
    public static native boolean hasGame();
    // The native side duplicates fd before returning. SAF owns the original.
    public static native String inspectIso(int fd);
    public static native byte[] readXex(int fd);
    public static native long create(int fd, String driverDir, String driverName,
            String userRoot, int width, int height, int scale, boolean diagnostic);
    public static native void surface(long id, Surface surface, int width, int height);
    public static native void pause(long id, boolean paused);
    public static native void input(long id, int buttons, int lt, int rt,
            int lx, int ly, int rx, int ry);
    public static native String status(long id);
    // May wait for guest/GPU shutdown. Call on a worker, never on the UI thread.
    public static native void destroy(long id);
}
