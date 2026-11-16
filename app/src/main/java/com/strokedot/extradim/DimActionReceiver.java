package com.strokedot.extradim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Notification button handler. One explicit user action causes at most two native writes. */
public class DimActionReceiver extends BroadcastReceiver {
    static final String TOGGLE = "com.strokedot.extradim.TOGGLE";
    static final String DIMMER = "com.strokedot.extradim.DIMMER";
    static final String BRIGHTER = "com.strokedot.extradim.BRIGHTER";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !DimController.hasPermission(context)) {
            NotificationController.refresh(context);
            return;
        }
        String action = intent.getAction();
        if (TOGGLE.equals(action)) {
            DimController.toggle(context);
        } else if (DIMMER.equals(action)) {
            // Limit quick-action dimming to 85 to reduce accidental unreadable screens.
            DimController.write(context, DimController.LEVEL,
                    Math.min(85, DimController.level(context) + 10));
            if (!DimController.active(context)) {
                DimController.write(context, DimController.ACTIVE, 1);
            }
        } else if (BRIGHTER.equals(action)) {
            DimController.write(context, DimController.LEVEL,
                    Math.max(DimController.MIN, DimController.level(context) - 10));
        }
        NotificationController.refresh(context);
    }
}
