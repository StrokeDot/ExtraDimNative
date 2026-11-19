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
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;

/** User-controlled notification with two native toggles and a settings shortcut.
 * Pinned mode uses a lightweight foreground service; no overlays or accessibility.
 */
public final class NotificationController {
    private NotificationController() {}
    private static final String PREFS="notification_controls";
    private static final String ENABLED="enabled";
    private static final String PINNED="pinned";
    private static final String CHANNEL="lumashade_controls_v5";
    static final int ID=31;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());

    static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(ENABLED,false);
    }
    static boolean pinned(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(PINNED,true);
    }
    static void setPinned(Context c,boolean pin) {
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(PINNED,pin).apply();
        reconcile(c);
    }
    static boolean canPost(Context c) {
        if(Build.VERSION.SDK_INT>=33 &&
                c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            return false;
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null||!nm.areNotificationsEnabled())return false;
        NotificationChannel ch=nm.getNotificationChannel(CHANNEL);
        return ch==null||ch.getImportance()!=NotificationManager.IMPORTANCE_NONE;
    }
    static void setEnabled(Context c,boolean show) {
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(ENABLED,show).apply();
        reconcile(c);
    }
    /** Only invoked from a visible app, notification interaction, or boot/package-replaced
     * receiver; Android may still restrict startup on some HyperOS builds.
     */
    static void reconcile(Context c) {
        Context app=c.getApplicationContext();
        Intent service=new Intent(app,NotificationControlsService.class);
        if(enabled(app)&&pinned(app)&&canPost(app)){
            try {
                if(Build.VERSION.SDK_INT>=26)app.startForegroundService(service);
                else app.startService(service);
            }catch(RuntimeException e){
                android.util.Log.w("LumaShade","Foreground notification service not available",e);
                refresh(app);
            }
        }else{
            app.stopService(service);
            // Allow foreground-service teardown before reposting unpinned notification.
            MAIN.postDelayed(()->refresh(app),160);
            if(!enabled(app)||!canPost(app))refresh(app);
        }
    }
    private static PendingIntent action(Context c,String name,int id) {
        Intent intent=new Intent(c,DimActionReceiver.class).setAction(name);
        return PendingIntent.getBroadcast(c,id,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static RemoteViews ui(Context c,boolean dim,boolean warm,PendingIntent open) {
        RemoteViews view=new RemoteViews(c.getPackageName(),R.layout.notification_lumashade_small);
        view.setOnClickPendingIntent(R.id.n_small_dim,
                action(c,DimActionReceiver.DIM_TOGGLE,401));
        view.setOnClickPendingIntent(R.id.n_small_warm,
                action(c,DimActionReceiver.FILTER_TOGGLE,402));
        view.setOnClickPendingIntent(R.id.n_small_settings,open);
        // Only update state. Replacing the drawable on every refresh would interrupt
        // its animation. SystemUI runs the animated selectors locally at display rate,
        // with Android's animator duration scale, without notification frame reposts.
        // The icon is decorative; its parent remains enabled and handles every tap.
        view.setBoolean(R.id.n_dim_icon,"setEnabled",dim);
        view.setBoolean(R.id.n_warm_icon,"setEnabled",warm);
        view.setContentDescription(R.id.n_small_dim,"Extra Dim " + (dim?"on":"off") + ", toggle");
        view.setContentDescription(R.id.n_small_warm,
                "Blue light filter " + (warm?"on":"off") + ", toggle");
        return view;
    }
    private static void createChannel(NotificationManager nm) {
        NotificationChannel ch=new NotificationChannel(CHANNEL,"LumaShade controls",
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setSound(null,null);
        ch.enableVibration(false);
        ch.setDescription("Dim and blue light filter controls");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        nm.deleteNotificationChannel("lumashade_controls_v4");
    }
    static Notification build(Context c,Boolean dimOverride,Boolean warmOverride) {
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm!=null)createChannel(nm);
        boolean dim=dimOverride!=null?dimOverride:DimController.active(c);
        boolean warm=warmOverride!=null?warmOverride:BlueLightController.active(c);
        PendingIntent open=PendingIntent.getActivity(c,41,
                new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        |Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(c,CHANNEL)
                .setSmallIcon(R.drawable.ic_lumashade_mono)
                .setContentTitle("LumaShade")
                .setContentText("Dim • Blue light filter • Settings")
                .setContentIntent(open)
                .setStyle(new Notification.DecoratedCustomViewStyle())
                .setCustomContentView(ui(c,dim,warm,open))
                .setCustomBigContentView(ui(c,dim,warm,open))
                .setPriority(Notification.PRIORITY_HIGH)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(pinned(c))
                .setAutoCancel(false)
                .setCategory(Notification.CATEGORY_STATUS)
                .build();
        if(pinned(c)){
            n.flags|=Notification.FLAG_NO_CLEAR;
            n.flags|=Notification.FLAG_ONGOING_EVENT;
        }
        return n;
    }
    static void refresh(Context c) {
        refresh(c,null,null);
    }
    static void refresh(Context c,Boolean dim,Boolean warm) {
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        if(!enabled(c)||!canPost(c)){
            nm.cancel(ID);
            return;
        }
        nm.notify(ID,build(c,dim,warm));
    }
}
