package pl.rexlauncher;

/** JNI exported by the real, separately compiled carbon_europe module. */
public final class CarbonNative {
    private CarbonNative() {}
    public static native void setLaunchReport(String path);
    public static native void setIso(int fd);
    public static native void setPad(int buttons,int lt,int rt,int lx,int ly,int rx,int ry);
}
