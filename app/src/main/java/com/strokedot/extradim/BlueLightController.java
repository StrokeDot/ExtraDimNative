package com.strokedot.extradim;

import android.content.Context;
import android.content.res.Resources;

/** First-party Android native Night Light integration; no additional overlay. */
final class BlueLightController {
    private BlueLightController() {}
    static final String ACTIVE = "night_display_activated";
    static final String KELVIN = "night_display_color_temperature";
    private static final String PREFERENCES = "blue_light";
    private static final String STRENGTH = "warmth";
    static final int KELVIN_COOL = 4000;
    static final int KELVIN_WARM = 2700;

    static boolean featureReportedAvailable(Context c) {
        try {
            Resources r = c.getResources();
            int id = r.getIdentifier("config_nightDisplayAvailable", "bool", "android");
            return id != 0 && r.getBoolean(id);
        } catch (RuntimeException e) { return false; }
    }

    static boolean active(Context c) {
        return DimController.read(c,ACTIVE,0)==1;
    }

    static int strength(Context c) {
        int saved = c.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE)
                .getInt(STRENGTH,-1);
        if (saved >= 0) return Math.max(0,Math.min(100,saved));
        int temperature = DimController.read(c,KELVIN,3350);
        return Math.max(0,Math.min(100,
                Math.round(100f*(KELVIN_COOL-temperature)/(KELVIN_COOL-KELVIN_WARM))));
    }

    static int kelvin(int strength) {
        return KELVIN_COOL -
                (KELVIN_COOL-KELVIN_WARM)*Math.max(0,Math.min(100,strength))/100;
    }

    static boolean setStrength(Context c,int strength) {
        int normalized=Math.max(0,Math.min(100,strength));
        boolean ok=DimController.write(c,KELVIN,kelvin(normalized));
        if (ok) {
            c.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).edit()
                    .putInt(STRENGTH,normalized).apply();
        }
        return ok;
    }

    static boolean toggle(Context c) {
        return DimController.write(c,ACTIVE,active(c)?0:1);
    }
}
