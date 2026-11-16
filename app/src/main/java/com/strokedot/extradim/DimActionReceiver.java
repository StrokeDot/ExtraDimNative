package com.strokedot.extradim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Three notification tabs, mutually exclusive intensity panels and fine controls. */
public class DimActionReceiver extends BroadcastReceiver {
    static final String SELECT_DIM="com.strokedot.extradim.SELECT_DIM";
    static final String SELECT_WARM="com.strokedot.extradim.SELECT_WARM";
    static final String SELECT_NONE="com.strokedot.extradim.SELECT_NONE";
    static final String DIM_TOGGLE="com.strokedot.extradim.DIM_TOGGLE";
    static final String DIM_STRENGTH="com.strokedot.extradim.DIM_STRENGTH";
    static final String DIM_ADJUST="com.strokedot.extradim.DIM_ADJUST";
    static final String FILTER_TOGGLE="com.strokedot.extradim.FILTER_TOGGLE";
    static final String FILTER_STRENGTH="com.strokedot.extradim.FILTER_STRENGTH";
    static final String FILTER_ADJUST="com.strokedot.extradim.FILTER_ADJUST";
    static final String EXTRA_STRENGTH="strength_percent";

    private static int clamp(int value) {return Math.max(0,Math.min(100,value));}
    private static int dimPercent(Context c) {
        return clamp(Math.round(100f*(DimController.MAX-DimController.level(c))/
                (DimController.MAX-DimController.MIN)));
    }
    private static void setDim(Context c,int percent) {
        if(!DimController.hasPermission(c))return;
        int level=DimController.MAX-(DimController.MAX-DimController.MIN)*clamp(percent)/100;
        DimController.write(c,DimController.LEVEL,level);
    }
    @Override public void onReceive(Context c,Intent intent) {
        if(intent==null)return;
        String action=intent.getAction();
        if(SELECT_NONE.equals(action)) {NotificationController.select(c,NotificationController.NONE);return;}
        if(SELECT_DIM.equals(action)) {NotificationController.select(c,NotificationController.DIM);return;}
        if(SELECT_WARM.equals(action)) {NotificationController.select(c,NotificationController.WARM);return;}
        if(!DimController.hasPermission(c)){NotificationController.refresh(c);return;}
        int value=intent.getIntExtra(EXTRA_STRENGTH,0);
        if(DIM_TOGGLE.equals(action)) DimController.toggle(c);
        else if(FILTER_TOGGLE.equals(action)) BlueLightController.toggle(c);
        else if(DIM_STRENGTH.equals(action)) setDim(c,value);
        else if(DIM_ADJUST.equals(action))setDim(c,dimPercent(c)+value);
        else if(FILTER_STRENGTH.equals(action))BlueLightController.setStrength(c,clamp(value));
        else if(FILTER_ADJUST.equals(action))
            BlueLightController.setStrength(c,clamp(BlueLightController.strength(c)+value));
        NotificationController.refresh(c);
    }
}
