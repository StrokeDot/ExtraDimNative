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

/** Minimal controls: moon toggles dim, sun toggles warmth, gear opens settings. */
public final class NotificationController {
    private NotificationController(){}
    private static final String PREFS="notification_controls";
    private static final String ENABLED="enabled";
    private static final String PINNED="pinned";
    private static final String CHANNEL="lumashade_controls_v4";
    private static final int ID=31;
    private static boolean posting=false;
    static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(ENABLED,false);
    }
    static boolean pinned(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(PINNED,true);
    }
    static void setPinned(Context c,boolean pin) {
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(PINNED,pin).apply();
        refresh(c);
    }
    static boolean canPost(Context c) {
        return Build.VERSION.SDK_INT < 33 ||
                c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }
    static void setEnabled(Context c,boolean show) {
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(ENABLED,show).apply();
        refresh(c);
    }
    private static PendingIntent action(Context c,String name,int id) {
        Intent intent=new Intent(c,DimActionReceiver.class).setAction(name);
        return PendingIntent.getBroadcast(c,id,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static RemoteViews ui(Context c,boolean dim,boolean warm,PendingIntent open){
        RemoteViews view=new RemoteViews(c.getPackageName(),R.layout.notification_lumashade_small);
        view.setOnClickPendingIntent(R.id.n_small_dim,
                action(c,DimActionReceiver.DIM_TOGGLE,401));
        view.setOnClickPendingIntent(R.id.n_small_warm,
                action(c,DimActionReceiver.FILTER_TOGGLE,402));
        view.setOnClickPendingIntent(R.id.n_small_settings,open);
        view.setImageViewResource(R.id.n_dim_icon,
                dim?R.drawable.ic_dim_on:R.drawable.ic_dim_off);
        view.setTextViewText(R.id.n_dim_caption,dim?"Dim on":"Dim off");
        view.setImageViewResource(R.id.n_warm_icon,
                warm?R.drawable.ic_warm_filter_on:R.drawable.ic_warm_filter_off);
        view.setTextViewText(R.id.n_warm_caption,warm?"Warm on":"Warm off");
        return view;
    }
    static synchronized void refresh(Context c) {
        if(posting)return;
        posting=true;
        try{
            NotificationManager nm=(NotificationManager)
                    c.getSystemService(Context.NOTIFICATION_SERVICE);
            if(nm==null)return;
            if(!enabled(c)||!canPost(c)){nm.cancel(ID);return;}
            NotificationChannel ch=new NotificationChannel(CHANNEL,"LumaShade controls",
                    NotificationManager.IMPORTANCE_LOW);
            ch.setSound(null,null);
            ch.enableVibration(false);
            ch.setDescription("Tap moon, sun or settings gear");
            nm.createNotificationChannel(ch);
            boolean dim=DimController.active(c);
            boolean warm=BlueLightController.active(c);
            PendingIntent open=PendingIntent.getActivity(c,41,
                    new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            |Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            Notification n=new Notification.Builder(c,CHANNEL)
                    .setSmallIcon(R.drawable.ic_lumashade_mono)
                    .setContentTitle("LumaShade")
                    .setContentText("Moon for dim • Sun for blue light • Gear for settings")
                    .setContentIntent(open)
                    .setStyle(new Notification.DecoratedCustomViewStyle())
                    .setCustomContentView(ui(c,dim,warm,open))
                    .setCustomBigContentView(ui(c,dim,warm,open))
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setOngoing(pinned(c))
                    .setAutoCancel(false)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .build();
            if(pinned(c)){
                n.flags |= Notification.FLAG_NO_CLEAR;
                n.flags |= Notification.FLAG_ONGOING_EVENT;
            }
            nm.notify(ID,n);
        } finally {posting=false;}
    }
}
