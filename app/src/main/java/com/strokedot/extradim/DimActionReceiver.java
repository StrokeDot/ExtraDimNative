package com.strokedot.extradim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Explicit notification taps change one of two independent Android display controls. */
public class DimActionReceiver extends BroadcastReceiver {
    static final String DIM_TOGGLE="com.strokedot.extradim.DIM_TOGGLE";
    static final String DIM_STRENGTH="com.strokedot.extradim.DIM_STRENGTH";
    static final String FILTER_TOGGLE="com.strokedot.extradim.FILTER_TOGGLE";
    static final String FILTER_STRENGTH="com.strokedot.extradim.FILTER_STRENGTH";
    static final String EXTRA_STRENGTH="strength_percent";

    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null||!DimController.hasPermission(context))return;
        String type=intent.getAction();
        int percent=intent.getIntExtra(EXTRA_STRENGTH,-1);
        if(DIM_TOGGLE.equals(type)) {
            DimController.toggle(context);
        } else if(FILTER_TOGGLE.equals(type)) {
            BlueLightController.toggle(context);
        } else if(DIM_STRENGTH.equals(type)) {
            // Disabled dim bar has no action, as in the app UI.
            if(DimController.active(context)&&percent>=0&&percent<=100){
                int value=DimController.MIN+
                    (DimController.MAX-DimController.MIN)*percent/100;
                DimController.write(context,DimController.LEVEL,value);
            }
        } else if(FILTER_STRENGTH.equals(type)) {
            if(BlueLightController.active(context)&&percent>=0&&percent<=100){
                BlueLightController.setStrength(context,percent);
            }
        }
        NotificationController.refresh(context);
    }
}
