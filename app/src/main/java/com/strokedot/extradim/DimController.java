package com.strokedot.extradim;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.os.Handler;
import android.os.Looper;

public final class DimController {
    private DimController() {}
    static final String ACTIVE = "reduce_bright_colors_activated";
    static final String LEVEL = "reduce_bright_colors_level";
    static final String PERSIST = "reduce_bright_colors_persist_across_reboots";
    static final int MIN = 25;
    // The device supports 100, but cap LumaShade at 95 for usable visibility.
    static final int MAX = 95;

    static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }
    // Production Android 16 apps cannot read hidden native Extra Dim keys.
    // Fall back to the last value that THIS app successfully wrote. This is
    // app-owned state, not authoritative state when external Android Settings,
    // Xiaomi Reading mode or another app changes the same hidden key.
    private static final String SHADOW_PREFS = "last_applied_native_settings";
    private static volatile String lastReadIssue = "";
    private static int localValue(Context c,String key,int fallback) {
        return c.getSharedPreferences(SHADOW_PREFS,Context.MODE_PRIVATE)
                .getInt(key,fallback);
    }
    private static void remember(Context c,String key,int value) {
        c.getSharedPreferences(SHADOW_PREFS,Context.MODE_PRIVATE).edit()
                .putInt(key,value).apply();
    }
    static boolean isShadowState() { return !BuildConfig.DEBUG; }
    static String lastReadIssue() { return lastReadIssue; }
    static int read(Context c, String key, int fallback) {
        // UI state describes the user's target, never a temporary animation frame.
        Integer target=DimTransition.value(c,key);
        return target!=null?target:readNative(c,key,fallback);
    }
    static int readNative(Context c, String key, int fallback) {
        try {
            int value = Settings.Secure.getInt(c.getContentResolver(), key, fallback);
            lastReadIssue = "";
            return value;
        } catch (RuntimeException e) {
            lastReadIssue = key + ": " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " - " + e.getMessage());
            android.util.Log.w("ExtraDimNative",lastReadIssue);
            return localValue(c,key,fallback);
        }
    }
    static boolean featureReportedAvailable(Context c) {
        try {
            Resources r = c.getResources();
            int id = r.getIdentifier("config_reduceBrightColorsAvailable", "bool", "android");
            return id == 0 || r.getBoolean(id);
        } catch (Exception e) { return true; }
    }
    static boolean active(Context c) { return read(c, ACTIVE, 0) == 1; }
    static boolean persistent(Context c) { return read(c, PERSIST, 0) == 1; }
    static int level(Context c) { return Math.max(MIN, Math.min(MAX, read(c, LEVEL, 50))); }
    // When upgrading from v0.15.3 the system may still have level 100.
    // Migrate it once to the new maximum so the display actually brightens;
    // merely clamping the slider position would leave the hardware at 100.
    static void enforceMaxLevel(Context c) {
        if (!hasPermission(c)) return;
        int raw = read(c, LEVEL, 50);
        if (raw > MAX) write(c, LEVEL, MAX);
    }
    // A successful SettingsProvider write can be observed asynchronously.
    // Never call it a failure solely because an immediate read returns the old value.
    private static volatile String lastWriteIssue = "";
    static String lastWriteIssue() { return lastWriteIssue; }
    static boolean write(Context c, String key, int value) {
        if(ACTIVE.equals(key))return DimTransition.setActive(c,value==1,null);
        if(LEVEL.equals(key))DimTransition.finish(c);
        boolean accepted=writeNative(c,key,value,true);
        if(accepted)refreshLater(c,key);
        return accepted;
    }
    // Animation frames must not replace the saved intensity or repost notifications.
    static boolean writeNative(Context c,String key,int value,boolean rememberValue) {
        if (!hasPermission(c)) {
            lastWriteIssue = "WRITE_SECURE_SETTINGS was not granted";
            return false;
        }
        try {
            boolean accepted = Settings.Secure.putInt(c.getContentResolver(), key, value);
            lastWriteIssue = accepted ? "" : "Android SettingsProvider rejected the write";
            if(accepted&&rememberValue)remember(c,key,value);
            return accepted;
        } catch (RuntimeException e) {
            lastWriteIssue = e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage());
            return false;
        }
    }
    private static void refreshLater(Context c, String key) {
        Context app=c.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (BlueLightController.ACTIVE.equals(key)) {
                requestTileUpdate(app, BlueLightTile.class);
            } else {
                requestTileUpdate(app);
            }
            NotificationController.refresh(app);
        },350);
    }
    static boolean toggle(Context c) { return write(c, ACTIVE, active(c) ? 0 : 1); }
    static void requestTileUpdate(Context c) {
        requestTileUpdate(c, ExtraDimTile.class);
    }
    private static void requestTileUpdate(Context c, Class<? extends TileService> tile) {
        try {
            TileService.requestListeningState(c, new ComponentName(c, tile));
        } catch (Exception ignored) { }
    }
}
