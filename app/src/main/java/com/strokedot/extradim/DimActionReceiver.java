package com.strokedot.extradim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

public class DimActionReceiver extends BroadcastReceiver {
    static final String DIM_TOGGLE="com.strokedot.extradim.DIM_TOGGLE";
    static final String FILTER_TOGGLE="com.strokedot.extradim.FILTER_TOGGLE";
    @Override public void onReceive(Context context, Intent intent) {
        if(intent==null)return;
        String action=intent.getAction();
        if(DIM_TOGGLE.equals(action)) {
            if (!DimController.hasPermission(context)) {
                NotificationController.refresh(context);
                return;
            }
            DimController.toggle(context);
        } else if(FILTER_TOGGLE.equals(action)) {
            if (!BlueLightController.canControl(context)) {
                NotificationController.refresh(context);
                return;
            }
            BlueLightController.toggle(context);
        }
        else return;
        Context app=context.getApplicationContext();
        new Handler(Looper.getMainLooper()).postDelayed(
                ()->NotificationController.refresh(app),420);
    }
}
