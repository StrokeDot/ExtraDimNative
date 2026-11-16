package com.strokedot.extradim;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Reposts the optional notification after a reboot; does NOT switch Extra Dim on. */
public class DimBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            NotificationController.refresh(context);
        }
    }
}
