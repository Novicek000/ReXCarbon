package pl.rexlauncher;

import android.app.Application;
import java.io.File;

public final class LauncherApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        File logs = new File(getFilesDir(), "logs");
        if (!logs.isDirectory() && !logs.mkdirs()) throw new IllegalStateException("Cannot create log directory");
        LogStore.start(logs);
        NativeBridge.init(logs.getAbsolutePath(), applicationInfo().nativeLibraryDir,
                getCacheDir().getAbsolutePath());
    }
    private android.content.pm.ApplicationInfo applicationInfo() { return getApplicationInfo(); }
}
