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

/**
 * Custom notification with tappable 5-step bars for native Extra Dim and warmth.
 * Android RemoteViews do not support dragging a SeekBar in a custom notification.
 */
public final class NotificationController {
    private NotificationController() { }
    private static final String PREFS="notification_controls";
    private static final String ENABLED="enabled";
    private static final String CHANNEL="lumashade_controls_v2";
    private static final int ID=31;
    private static final int[] DIM_IDS={
        R.id.n_dim_strength1,R.id.n_dim_strength2,R.id.n_dim_strength3,
        R.id.n_dim_strength4,R.id.n_dim_strength5};
    private static final int[] WARM_IDS={
        R.id.n_strength1,R.id.n_strength2,R.id.n_strength3,
        R.id.n_strength4,R.id.n_strength5};
    private static boolean isPosting=false;

    static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .getBoolean(ENABLED,false);
    }
    static boolean canPost(Context c) {
        return Build.VERSION.SDK_INT<33||
                c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
    }
    static void setEnabled(Context c,boolean enabled) {
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putBoolean(ENABLED,enabled).apply();
        refresh(c);
    }
    static synchronized void refresh(Context c) {
        if(isPosting) return;
        isPosting=true;
        try {
            NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
            if(nm==null)return;
            if(!enabled(c)||!canPost(c)){nm.cancel(ID);return;}
            NotificationChannel ch=new NotificationChannel(
                    CHANNEL,"LumaShade controls",NotificationManager.IMPORTANCE_LOW);
            ch.enableVibration(false);
            ch.setSound(null,null);
            ch.setDescription("Brightness and blue-light quick controls");
            nm.createNotificationChannel(ch);
            boolean dim=DimController.active(c);
            boolean warm=BlueLightController.active(c);
            int dimLevel=DimController.level(c);
            // The app slider runs right-to-left for stronger dimming.
            int dimPercent=Math.round(100f*(DimController.MAX-dimLevel)
                    /(DimController.MAX-DimController.MIN));
            int warmPercent=BlueLightController.strength(c);
            Intent open=new Intent(c,MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent openApp=PendingIntent.getActivity(c,41,open,
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

            RemoteViews compact=new RemoteViews(c.getPackageName(),
                    R.layout.notification_lumashade_small);
            compact.setTextViewText(R.id.n_small_title,
                    "LumaShade • Dim " + (dim?"on":"off")
                            + " • Warmth " + (warm?"on":"off"));
            compact.setOnClickPendingIntent(R.id.n_small_settings,openApp);

            RemoteViews expanded=new RemoteViews(c.getPackageName(),
                    R.layout.notification_lumashade_big);
            expanded.setOnClickPendingIntent(R.id.n_settings,openApp);
            expanded.setTextViewText(R.id.n_dim_state,dim?"On":"Off");
            expanded.setTextViewText(R.id.n_filter_state,warm?"On":"Off");
            expanded.setTextViewText(R.id.n_dim_value,dimPercent+"%");
            expanded.setTextViewText(R.id.n_warmth_value,warmPercent+"%");
            expanded.setOnClickPendingIntent(R.id.n_dim_toggle,
                    action(c,DimActionReceiver.DIM_TOGGLE,110,0));
            expanded.setOnClickPendingIntent(R.id.n_filter_toggle,
                    action(c,DimActionReceiver.FILTER_TOGGLE,111,0));

            for(int i=0;i<5;i++){
                int value=20*(i+1);
                int dimDrawable=!dim?R.drawable.bg_segment_disabled
                    :(dimPercent>=value?R.drawable.bg_segment_blue_on:
                        R.drawable.bg_segment_blue_off);
                int warmDrawable=!warm?R.drawable.bg_segment_disabled
                    :(warmPercent>=value?R.drawable.bg_segment_on:
                        R.drawable.bg_segment_off);
                expanded.setImageViewResource(DIM_IDS[i],dimDrawable);
                expanded.setImageViewResource(WARM_IDS[i],warmDrawable);
                if(dim)expanded.setOnClickPendingIntent(DIM_IDS[i],
                    action(c,DimActionReceiver.DIM_STRENGTH,120+i,value));
                if(warm)expanded.setOnClickPendingIntent(WARM_IDS[i],
                    action(c,DimActionReceiver.FILTER_STRENGTH,140+i,value));
            }
            Notification n=new Notification.Builder(c,CHANNEL)
                    .setSmallIcon(R.drawable.ic_lumashade_mono)
                    .setContentTitle("LumaShade")
                    .setContentText("Dim and warmth controls")
                    .setContentIntent(openApp)
                    .setStyle(new Notification.DecoratedCustomViewStyle())
                    .setCustomContentView(compact)
                    .setCustomBigContentView(expanded)
                    .setShowWhen(false)
                    .setOnlyAlertOnce(true)
                    .setOngoing(true)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .build();
            nm.notify(ID,n);
        } finally {isPosting=false;}
    }
    private static PendingIntent action(Context c,String type,int request,int value) {
        Intent i=new Intent(c,DimActionReceiver.class).setAction(type)
                .putExtra(DimActionReceiver.EXTRA_STRENGTH,value);
        return PendingIntent.getBroadcast(c,request,i,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
}
