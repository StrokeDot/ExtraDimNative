package com.strokedot.extradim;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.util.Locale;

/** Uses native Xiaomi 15T Reading mode if its tested system keys are available.
 * Other devices retain the existing AOSP Night Light behavior.
 * No overlay, Accessibility service, root or undocumented Binder transactions. */
final class BlueLightController {
    private BlueLightController() {}
    static final String ACTIVE="night_display_activated";
    static final String KELVIN="night_display_color_temperature";
    static final String XIAOMI_MODE="screen_mode_type";
    static final String XIAOMI_ACTIVE="screen_paper_mode_enabled";
    static final String XIAOMI_LEVEL="screen_paper_texture_level";
    static final int XIAOMI_MIN=58;
    static final int XIAOMI_MAX=255;
    private static final int AOSP_MIN=2596;
    private static final int AOSP_MAX=4082;
    private static final int AOSP_DEFAULT=2850;
    private static final String PREFERENCES="blue_light";
    private static final String STRENGTH="warmth";
    private static volatile String issue="";
    static String lastIssue(){return issue;}
    private static int readSystem(Context c,String key,int fallback) {
        try {return Settings.System.getInt(c.getContentResolver(),key,fallback);}
        catch(RuntimeException e){return fallback;}
    }
    static boolean xiaomiSupported(Context c) {
        String manufacturer=Build.MANUFACTURER==null?"":Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        String model=Build.MODEL==null?"":Build.MODEL.toLowerCase(Locale.ROOT);
        // Conservative device gate: only the Xiaomi 15T was tested.
        if(!manufacturer.contains("xiaomi") ||
                !(model.contains("15t") || model.contains("25069ptebg")))return false;
        try {
            String mode=Settings.System.getString(c.getContentResolver(),XIAOMI_MODE);
            String level=Settings.System.getString(c.getContentResolver(),XIAOMI_LEVEL);
            String enabled=Settings.System.getString(c.getContentResolver(),XIAOMI_ACTIVE);
            if(!"3".equals(mode)||level==null||enabled==null)return false;
            int value=Integer.parseInt(level);
            return value>=XIAOMI_MIN && value<=XIAOMI_MAX;
        } catch(RuntimeException e){return false;}
    }
    static boolean canControl(Context c){
        return xiaomiSupported(c) ? Settings.System.canWrite(c) :
                DimController.hasPermission(c);
    }
    static boolean active(Context c){
        return xiaomiSupported(c) ? readSystem(c,XIAOMI_ACTIVE,0)==1 :
                DimController.read(c,ACTIVE,0)==1;
    }
    static int strength(Context c){
        if(xiaomiSupported(c)){
            int level=readSystem(c,XIAOMI_LEVEL,XIAOMI_MIN);
            return Math.max(0,Math.min(100,
                Math.round(100f*(level-XIAOMI_MIN)/(XIAOMI_MAX-XIAOMI_MIN))));
        }
        int temp=DimController.read(c,KELVIN,-1),min=minimumKelvin(c),max=maximumKelvin(c);
        if(temp>=0)return Math.max(0,Math.min(100,Math.round(100f*(max-temp)/(max-min))));
        int saved=c.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).getInt(STRENGTH,-1);
        return saved>=0?Math.max(0,Math.min(100,saved)):
                Math.max(0,Math.min(100,Math.round(100f*(max-defaultKelvin(c))/(max-min))));
    }
    static int xiaomiLevelForPercent(int percent){
        int p=Math.max(0,Math.min(100,percent));
        return XIAOMI_MIN + Math.round((XIAOMI_MAX-XIAOMI_MIN)*p/100f);
    }
    static boolean setStrength(Context c,int percent){
        int normalized=Math.max(0,Math.min(100,percent));
        if(xiaomiSupported(c)){
            if(!Settings.System.canWrite(c)){issue="Modify system settings permission required";return false;}
            try{
                int level=xiaomiLevelForPercent(normalized);
                boolean written=Settings.System.putInt(c.getContentResolver(),XIAOMI_LEVEL,level);
                boolean readback=readSystem(c,XIAOMI_LEVEL,-1)==level;
                issue=written&&readback?"":"Xiaomi did not accept the warmth level";
                if(written&&readback){refresh(c);return true;}
                return false;
            }catch(RuntimeException e){issue=e.toString();return false;}
        }
        boolean ok=DimController.write(c,KELVIN,kelvin(c,normalized));
        if(ok)c.getSharedPreferences(PREFERENCES,Context.MODE_PRIVATE).edit()
                .putInt(STRENGTH,normalized).apply();
        return ok;
    }
    static boolean setEnabled(Context c,boolean enabled){
        if(xiaomiSupported(c)){
            if(!Settings.System.canWrite(c)){issue="Modify system settings permission required";return false;}
            try {
                boolean written=Settings.System.putInt(c.getContentResolver(),
                        XIAOMI_ACTIVE,enabled?1:0);
                boolean readback=readSystem(c,XIAOMI_ACTIVE,-1)==(enabled?1:0);
                issue=written&&readback?"":"Xiaomi did not accept the Reading mode state";
                if(written&&readback){
                    // Avoid stacking the old AOSP yellow filter on native Xiaomi Reading mode.
                    if(enabled&&DimController.hasPermission(c)&&DimController.read(c,ACTIVE,0)==1)
                        DimController.write(c,ACTIVE,0);
                    refresh(c);
                    return true;
                }
                return false;
            }catch(RuntimeException e){issue=e.toString();return false;}
        }
        return DimController.write(c,ACTIVE,enabled?1:0);
    }
    static boolean toggle(Context c){return setEnabled(c,!active(c));}
    private static void refresh(Context c){
        Context app=c.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(
                ()->NotificationController.refresh(app),350);
    }
    static boolean featureReportedAvailable(Context c){
        try{
            Resources r=c.getResources();
            int id=r.getIdentifier("config_nightDisplayAvailable","bool","android");
            return id!=0&&r.getBoolean(id);
        }catch(RuntimeException e){return false;}
    }
    private static int config(Context c,String name,int fallback){
        try{
            Resources r=c.getResources();
            int id=r.getIdentifier(name,"integer","android");
            return id==0?fallback:r.getInteger(id);
        }catch(RuntimeException e){return fallback;}
    }
    private static int minimumKelvin(Context c){
        return config(c,"config_nightDisplayColorTemperatureMin",AOSP_MIN);
    }
    private static int maximumKelvin(Context c){
        return Math.max(minimumKelvin(c)+100,
            config(c,"config_nightDisplayColorTemperatureMax",AOSP_MAX));
    }
    private static int defaultKelvin(Context c){
        return config(c,"config_nightDisplayColorTemperatureDefault",AOSP_DEFAULT);
    }
    private static int kelvin(Context c,int percent){
        return maximumKelvin(c)-(maximumKelvin(c)-minimumKelvin(c))*percent/100;
    }
}
