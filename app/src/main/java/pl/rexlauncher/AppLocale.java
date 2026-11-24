package pl.rexlauncher;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.LocaleList;
import java.util.Locale;

/** Polish for the primary system language pl; English for every other language. */
public final class AppLocale {
    private AppLocale() {}
    public static Context wrap(Context base){
        String primary=Resources.getSystem().getConfiguration().getLocales().get(0).getLanguage();
        int selection=Config.prefs(base).getInt("language",0);
        if(selection==1) primary="en";
        if(selection==2) primary="pl";
        Configuration override=new Configuration();
        Locale chosen="pl".equals(primary)?new Locale("pl"):Locale.ENGLISH;
        override.setLocales(new LocaleList(chosen));override.setLayoutDirection(chosen);
        return base.createConfigurationContext(override);
    }
}
