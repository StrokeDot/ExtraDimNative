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
        boolean previous=dim?DimController.active(context):BlueLightController.active(context);
        boolean accepted=dim?DimController.write(context,DimController.ACTIVE,previous?0:1):
                DimController.write(context,BlueLightController.ACTIVE,previous?0:1);
        // A successful provider write may be read back asynchronously. Use its
        // accepted target now; subsequent refreshes preserve the same drawable.
        NotificationController.refresh(context,
                accepted&&dim?Boolean.valueOf(!previous):null,
                accepted&&!dim?Boolean.valueOf(!previous):null);
    }
}
