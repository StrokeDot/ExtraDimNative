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
        int target;
        boolean previous;
        if(DIM_TOGGLE.equals(action)){
            target=NotificationController.ANIMATE_DIM;
            previous=DimController.active(context);
        }else if(FILTER_TOGGLE.equals(action)){
            target=NotificationController.ANIMATE_FILTER;
            previous=BlueLightController.active(context);
        }else return;
        if(!DimController.hasPermission(context)){
            NotificationController.refresh(context);
            return;
        }
        boolean accepted=target==NotificationController.ANIMATE_DIM?
                DimController.toggle(context):BlueLightController.toggle(context);
        if(!accepted){
            NotificationController.refresh(context);
            return;
        }
        PendingResult pending=goAsync();
        NotificationController.animate(context,target,!previous,pending::finish);
    }
}
