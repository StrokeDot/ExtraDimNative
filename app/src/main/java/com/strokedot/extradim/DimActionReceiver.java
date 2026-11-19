package com.strokedot.extradim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DimActionReceiver extends BroadcastReceiver {
    static final String DIM_TOGGLE="com.strokedot.extradim.DIM_TOGGLE";
    static final String FILTER_TOGGLE="com.strokedot.extradim.FILTER_TOGGLE";
    @Override public void onReceive(Context context,Intent intent) {
        if(intent==null)return;
        String action=intent.getAction();
        boolean dim=DIM_TOGGLE.equals(action);
        if(!dim&&!FILTER_TOGGLE.equals(action))return;
        if(!DimController.hasPermission(context)){
            NotificationController.refresh(context);
            return;
        }
        DimTransition.recover(context);
        if(dim) {
            PendingResult pending=goAsync();
            // Keep this process alive until the real screen fade has finished,
            // including when the app itself is closed or controls are unpinned.
            DimTransition.setActive(context,!DimController.active(context),pending::finish);
        }else {
            // Android's Night Light service owns its native animated color-matrix
            // transition. Do not change Kelvin mid-fade: that forces a hard reset.
            boolean previous=BlueLightController.active(context);
            boolean accepted=BlueLightController.toggle(context);
            NotificationController.refresh(context,null,accepted?!previous:null);
        }
    }
}
