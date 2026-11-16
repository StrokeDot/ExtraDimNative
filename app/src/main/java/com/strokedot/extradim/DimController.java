package com.strokedot.extradim;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.provider.Settings;
import android.service.quicksettings.TileService;

public final class DimController {
    private DimController() {}
    static final String ACTIVE = "reduce_bright_colors_activated";
    static final String LEVEL = "reduce_bright_colors_level";
    static final String PERSIST = "reduce_bright_colors_persist_across_reboots";
    static final int MIN = 25;
    static final int MAX = 90;

    static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED;
    }
    static int read(Context c, String key, int fallback) {
        try { return Settings.Secure.getInt(c.getContentResolver(), key, fallback); }
        catch (Exception e) { return fallback; }
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
    static boolean write(Context c, String key, int value) {
        if (!hasPermission(c)) return false;
        try {
            boolean ok = Settings.Secure.putInt(c.getContentResolver(), key, value);
            requestTileUpdate(c);
            return ok && read(c, key, Integer.MIN_VALUE) == value;
        } catch (RuntimeException e) { return false; }
    }
    static boolean toggle(Context c) { return write(c, ACTIVE, active(c) ? 0 : 1); }
    static void requestTileUpdate(Context c) {
        try {
            TileService.requestListeningState(c, new ComponentName(c, ExtraDimTile.class));
        } catch (Exception ignored) { }
    }
}
