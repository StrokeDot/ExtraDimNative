package com.strokedot.extradim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Notification controls operate Night Light, not native Extra Dim. */
public class DimActionReceiver extends BroadcastReceiver {
    static final String FILTER_TOGGLE="com.strokedot.extradim.FILTER_TOGGLE";
    static final String FILTER_STRENGTH="com.strokedot.extradim.FILTER_STRENGTH";
    static final String EXTRA_STRENGTH="warmth_percent";

    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null||!DimController.hasPermission(context)) {
            NotificationController.refresh(context);
            return;
        }
        String action=intent.getAction();
        if(FILTER_TOGGLE.equals(action)) {
            BlueLightController.toggle(context);
        } else if(FILTER_STRENGTH.equals(action)) {
            int strength=intent.getIntExtra(EXTRA_STRENGTH,-1);
            if(strength>=0&&strength<=100) {
                if(BlueLightController.setStrength(context,strength)
                        &&!BlueLightController.active(context)) {
                    DimController.write(context,BlueLightController.ACTIVE,1);
                }
            }
        }
        NotificationController.refresh(context);
    }
}
