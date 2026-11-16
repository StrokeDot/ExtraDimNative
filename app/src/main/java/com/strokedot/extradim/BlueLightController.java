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
    // AOSP defaults. Device overlays can customize both endpoints.
    private static final int AOSP_MIN_K = 2596;
    private static final int AOSP_MAX_K = 4082;
    private static final int AOSP_DEFAULT_K = 2850;

    private static int config(Context c,String name,int fallback) {
        try {
            Resources r = c.getResources();
            int id = r.getIdentifier(name,"integer","android");
            return id == 0 ? fallback : r.getInteger(id);
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
        int temperature = DimController.read(c,KELVIN,-1);
        int min=minimumKelvin(c), max=maximumKelvin(c);
        if (temperature >= 0) {
            int value=Math.round(100f*(max-temperature)/(max-min));
            return Math.max(0,Math.min(100,value));
        }
        // Only fallback to a remembered user choice if system value cannot be read.
        int saved=c.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE)
                .getInt(STRENGTH,-1);
        if(saved>=0) return Math.max(0,Math.min(100,saved));
        return Math.max(0,Math.min(100,
                Math.round(100f*(max-defaultKelvin(c))/(max-min))));
    }

    static int kelvin(Context c,int value) {
        int clamped=Math.max(0,Math.min(100,value));
        return maximumKelvin(c)-
                (maximumKelvin(c)-minimumKelvin(c))*clamped/100;
    }

    static boolean setStrength(Context c,int strength) {
        int normalized=Math.max(0,Math.min(100,strength));
        boolean ok=DimController.write(c,KELVIN,kelvin(c,normalized));
        if(ok) {
            c.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE)
                    .edit().putInt(STRENGTH,normalized).apply();
        }
        return ok;
    }

    static boolean toggle(Context c) {
        return DimController.write(c,ACTIVE,active(c)?0:1);
    }
}
