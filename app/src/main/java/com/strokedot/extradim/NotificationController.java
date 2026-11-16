package com.strokedot.extradim;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.RemoteViews;

/** Notification shade: app logo, blue-light toggle, warmth presets, Settings. */
public final class NotificationController {
    private NotificationController() { }
    private static final String PREFS = "notification_controls";
    private static final String ENABLED = "enabled";
    private static final String CHANNEL = "lumashade_controls_v1";
    private static final int ID = 31;
    private static final int[] STRENGTH_IDS={
        R.id.n_strength1,R.id.n_strength2,R.id.n_strength3,
        R.id.n_strength4,R.id.n_strength5
    };

    static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(ENABLED,false);
    }
    static boolean canPost(Context c) {
        return Build.VERSION.SDK_INT<33 ||
                c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
    }
    static void setEnabled(Context c,boolean value) {
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putBoolean(ENABLED,value).apply();
        refresh(c);
    }
    static void refresh(Context c) {
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null) return;
        if(!enabled(c)||!canPost(c)) { nm.cancel(ID); return; }
        NotificationChannel channel=new NotificationChannel(
                CHANNEL,"LumaShade controls",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Compact controls for blue light warmth and settings");
        channel.setSound(null,null);
        channel.enableVibration(false);
        nm.createNotificationChannel(channel);

        boolean active=BlueLightController.active(c);
        int strength=BlueLightController.strength(c);
        Intent open = new Intent(c,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openApp=PendingIntent.getActivity(c,41,open,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        RemoteViews compact=new RemoteViews(c.getPackageName(),R.layout.notification_lumashade_small);
        compact.setTextViewText(R.id.n_small_title,
                "LumaShade • Blue light " + (active ? "on" : "off"));
        compact.setOnClickPendingIntent(R.id.n_small_settings,openApp);

        RemoteViews expanded=new RemoteViews(c.getPackageName(),R.layout.notification_lumashade_big);
        expanded.setTextViewText(R.id.n_filter_state,active?"On":"Off");
        expanded.setTextViewText(R.id.n_warmth_value,strength+"%");
        expanded.setImageViewResource(R.id.n_filter_icon,
                active?R.drawable.ic_warm_filter_on:R.drawable.ic_warm_filter_off);
        expanded.setOnClickPendingIntent(R.id.n_filter_toggle,
                broadcast(c,DimActionReceiver.FILTER_TOGGLE,80,0));
        expanded.setOnClickPendingIntent(R.id.n_settings,openApp);
        for(int i=0;i<STRENGTH_IDS.length;i++){
            int preset=20*(i+1);
            expanded.setImageViewResource(STRENGTH_IDS[i],
                    strength>=preset?R.drawable.bg_segment_on:R.drawable.bg_segment_off);
            expanded.setOnClickPendingIntent(STRENGTH_IDS[i],
                    broadcast(c,DimActionReceiver.FILTER_STRENGTH,90+i,preset));
        }
        Notification n=new Notification.Builder(c,CHANNEL)
                .setSmallIcon(R.drawable.ic_lumashade_mono)
                .setContentTitle("LumaShade")
                .setContentText(active?"Blue light filter on":"Blue light filter off")
                .setContentIntent(openApp)
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setCustomContentView(compact)
                .setCustomBigContentView(expanded)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .build();
        nm.notify(ID,n);
    }
    private static PendingIntent broadcast(Context c,String action,int requestCode,int strength) {
        Intent i=new Intent(c,DimActionReceiver.class).setAction(action);
        i.putExtra(DimActionReceiver.EXTRA_STRENGTH,strength);
        return PendingIntent.getBroadcast(c,requestCode,i,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
}
