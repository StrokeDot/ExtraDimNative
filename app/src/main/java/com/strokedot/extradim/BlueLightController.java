package com.strokedot.extradim;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.provider.Settings;

/**
 * Blue-light controls. Xiaomi Reading mode uses System settings (not AOSP
 * Night Light Secure settings). Prefer the Xiaomi backend only when the ROM
 * exposes its native reading-mode keys; never create unrecognized vendor keys.
 * Neither SettingsProvider success nor a value read-back proves the panel
 * actually changed, so the app reports the backend and a diagnostic.
 */
final class BlueLightController {
    private BlueLightController() {}
    static final String ACTIVE = "night_display_activated";
    static final String KELVIN = "night_display_color_temperature";
    static final String XIAOMI_ACTIVE = "screen_paper_mode_enabled";
    static final String XIAOMI_LEVEL = "screen_paper_mode_level";
    private static final String PREFERENCES = "blue_light";
    private static final String STRENGTH = "warmth";
    private static final int XIAOMI_LEVEL_MAX = 255;
    private static final int AOSP_MIN_K = 2596;
    private static final int AOSP_MAX_K = 4082;
    private static final int AOSP_DEFAULT_K = 2850;
    private static volatile String lastIssue = "";

    private static boolean xiaomiManufacturer() {
        String maker = Build.MANUFACTURER == null ? "" :
                Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT);
        return maker.contains("xiaomi") || maker.contains("redmi") || maker.contains("poco");
    }
    private static String xiaomiSetting(Context c,String key) {
        try {
            return Settings.System.getString(c.getContentResolver(),key);
        } catch (RuntimeException ex) {
            lastIssue = "Reading-mode read: " + ex.getClass().getSimpleName();
            return null;
        }
    }
    static boolean usingXiaomi(Context c) {
        return xiaomiManufacturer()
                && (xiaomiSetting(c,XIAOMI_ACTIVE) != null
                    || xiaomiSetting(c,XIAOMI_LEVEL) != null);
    }
    static boolean needsModifySettings(Context c) {
        if (!usingXiaomi(c)) return false;
        try { return !Settings.System.canWrite(c); }
        catch (RuntimeException ex) { return true; }
    }
    static boolean canControl(Context c) {
        if (!usingXiaomi(c)) return DimController.hasPermission(c);
        try { return Settings.System.canWrite(c); }
        catch (RuntimeException ex) { return false; }
    }
    static boolean xiaomiKeysMissing(Context c) {
        return xiaomiManufacturer() && !usingXiaomi(c);
    }

    private static int systemInt(Context c,String key,int fallback) {
        String value = xiaomiSetting(c,key);
        if (value == null) return fallback;
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException ex) { return fallback; }
    }
    private static boolean writeSystem(Context c,String key,int value) {
        if (!canControl(c)) {
            lastIssue="Allow 'Modify system settings' for LumaShade to control Xiaomi Reading mode.";
            return false;
        }
        try {
            boolean ok=Settings.System.putInt(c.getContentResolver(),key,value);
            lastIssue=ok?"":"Xiaomi SettingsProvider rejected " + key;
            if (ok) {
                Context app=c.getApplicationContext();
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                    () -> NotificationController.refresh(app),350);
            }
            return ok;
        } catch (RuntimeException ex) {
            lastIssue=ex.getClass().getSimpleName() + ": " + ex.getMessage();
            return false;
        }
    }
    static String lastWriteIssue(Context c) {
        return usingXiaomi(c) ? lastIssue : DimController.lastWriteIssue();
    }
    private static int config(Context c,String name,int fallback) {
        try {
            Resources r=c.getResources();
            int id=r.getIdentifier(name,"integer","android");
            return id==0?fallback:r.getInteger(id);
        } catch(RuntimeException e) { return fallback; }
    }
    static int minimumKelvin(Context c) {
        return config(c,"config_nightDisplayColorTemperatureMin",AOSP_MIN_K);
    }
    static int maximumKelvin(Context c) {
        return Math.max(minimumKelvin(c)+100,
            config(c,"config_nightDisplayColorTemperatureMax",AOSP_MAX_K));
    }
    static int defaultKelvin(Context c) {
        return config(c,"config_nightDisplayColorTemperatureDefault",AOSP_DEFAULT_K);
    }
    static boolean featureReportedAvailable(Context c) {
        if (usingXiaomi(c)) return true;
        try {
            Resources r=c.getResources();
            int id=r.getIdentifier("config_nightDisplayAvailable","bool","android");
            return id!=0 && r.getBoolean(id);
        } catch (RuntimeException e) { return false; }
    }
    static boolean active(Context c) {
        return usingXiaomi(c)
                ? systemInt(c,XIAOMI_ACTIVE,0)==1
                : DimController.read(c,ACTIVE,0)==1;
    }
    static int strength(Context c) {
        if (usingXiaomi(c)) {
            int level=systemInt(c,XIAOMI_LEVEL,-1);
            if (level>=0)
                return Math.max(0,Math.min(100,Math.round(100f*level/XIAOMI_LEVEL_MAX)));
        } else {
            int temperature=DimController.read(c,KELVIN,-1);
            int min=minimumKelvin(c),max=maximumKelvin(c);
            if (temperature>=0)
                return Math.max(0,Math.min(100,Math.round(100f*(max-temperature)/(max-min))));
        }
        int saved=c.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).getInt(STRENGTH,-1);
        if (saved>=0) return Math.max(0,Math.min(100,saved));
        return Math.max(0,Math.min(100,Math.round(
            100f*(maximumKelvin(c)-defaultKelvin(c))/(maximumKelvin(c)-minimumKelvin(c)))));
    }
    static int kelvin(Context c,int value) {
        int normalized=Math.max(0,Math.min(100,value));
        return maximumKelvin(c)-
            (maximumKelvin(c)-minimumKelvin(c))*normalized/100;
    }
    static boolean setStrength(Context c,int value) {
        int normalized=Math.max(0,Math.min(100,value));
        boolean ok;
        if (usingXiaomi(c)) {
            int raw=Math.round(normalized*XIAOMI_LEVEL_MAX/100f);
            ok=writeSystem(c,XIAOMI_LEVEL,raw);
        } else {
            ok=DimController.write(c,KELVIN,kelvin(c,normalized));
        }
        if(ok) c.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE)
                .edit().putInt(STRENGTH,normalized).apply();
        return ok;
    }
    static boolean setEnabled(Context c,boolean enabled) {
        return usingXiaomi(c)
            ? writeSystem(c,XIAOMI_ACTIVE,enabled?1:0)
            : DimController.write(c,ACTIVE,enabled?1:0);
    }
    static boolean toggle(Context c) {
        return setEnabled(c,!active(c));
    }
    static String description(Context c) {
        if (usingXiaomi(c))
            return "Xiaomi Reading mode • native intensity value (0–255). "
                + "Try Classic mode; HyperOS may require its own display service to apply changes.";
        if (xiaomiKeysMissing(c))
            return "Xiaomi Reading mode settings not detected. Enable Reading mode once "
                + "in Display & brightness, then reopen LumaShade. "
                + "Currently using Android Night Light fallback.";
        return "Android Night Light • device may not apply direct temperature writes.";
    }
    static String diagnostics(Context c) {
        return "backend=" + (usingXiaomi(c)?"Xiaomi Reading":"Android Night Light")
            + " manufacturer=" + Build.MANUFACTURER
            + " systemWrite=" + (usingXiaomi(c)?canControl(c):"n/a")
            + " readingEnabled=" + xiaomiSetting(c,XIAOMI_ACTIVE)
            + " readingLevel=" + xiaomiSetting(c,XIAOMI_LEVEL)
            + " nightActivated=" + DimController.read(c,ACTIVE,-1)
            + " nightKelvin=" + DimController.read(c,KELVIN,-1)
            + " issue=" + lastIssue;
    }
}
