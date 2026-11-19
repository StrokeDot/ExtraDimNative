package com.strokedot.extradim;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * Hosts user-enabled pinned notification controls while LumaShade is not open.
 * Does not perform background screen modifications or use overlays.
 */
public final class NotificationControlsService extends Service {
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(!NotificationController.enabled(this) || !NotificationController.pinned(this)
                || !NotificationController.canPost(this)){
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            android.app.Notification notification=
                    NotificationController.build(this,0,false,null,null);
            if(Build.VERSION.SDK_INT>=34)
                startForeground(NotificationController.ID,notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            else startForeground(NotificationController.ID,notification);
            return START_STICKY;
        }catch(RuntimeException e){
            Log.w("LumaShade","Could not keep notification controls foreground",e);
            stopSelf();
            NotificationController.refresh(this);
            return START_NOT_STICKY;
        }
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
