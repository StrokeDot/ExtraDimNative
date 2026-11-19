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
    static final int ANIMATE_DIM=1;
    static final int ANIMATE_FILTER=2;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static int animationGeneration=0;

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
    private static int buttonBackground(boolean dim,boolean active,boolean pulse) {
        if(pulse)return active ?
            (dim?R.drawable.bg_notify_dim_pulse:R.drawable.bg_notify_warm_pulse) :
            R.drawable.bg_notify_off_pulse;
        return active ?
            (dim?R.drawable.bg_notify_dim_active:R.drawable.bg_notify_warm_active) :
            R.drawable.bg_notify_neutral;
    }
    private static RemoteViews ui(Context c,boolean dim,boolean warm,
                                  PendingIntent open,int pulseTarget,boolean pulse) {
        RemoteViews view=new RemoteViews(c.getPackageName(),R.layout.notification_lumashade_small);
        view.setOnClickPendingIntent(R.id.n_small_dim,
                action(c,DimActionReceiver.DIM_TOGGLE,401));
        view.setOnClickPendingIntent(R.id.n_small_warm,
                action(c,DimActionReceiver.FILTER_TOGGLE,402));
        view.setOnClickPendingIntent(R.id.n_small_settings,open);
        view.setInt(R.id.n_small_dim,"setBackgroundResource",
                buttonBackground(true,dim,pulse&&pulseTarget==ANIMATE_DIM));
        view.setInt(R.id.n_small_warm,"setBackgroundResource",
                buttonBackground(false,warm,pulse&&pulseTarget==ANIMATE_FILTER));
        view.setImageViewResource(R.id.n_dim_icon,dim?R.drawable.ic_dim_on:R.drawable.ic_dim_off);
        view.setTextViewText(R.id.n_dim_caption,"Dim");
        view.setTextColor(R.id.n_dim_caption,
                pulse&&pulseTarget==ANIMATE_DIM ? 0xFFFFFFFF :
                (dim?0xFFB6CCFF:0xFF9AA6B9));
        view.setImageViewResource(R.id.n_warm_icon,
                warm?R.drawable.ic_warm_filter_on:R.drawable.ic_warm_filter_off);
        view.setTextViewText(R.id.n_warm_caption,"Blue light\nfilter");
        view.setTextColor(R.id.n_warm_caption,
                pulse&&pulseTarget==ANIMATE_FILTER ? 0xFFFFFFFF :
                (warm?0xFFF7D4A4:0xFF9AA6B9));
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
    static Notification build(Context c,int pulseTarget,boolean pulse,
                              Boolean dimOverride,Boolean warmOverride) {
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
                .setCustomContentView(ui(c,dim,warm,open,pulseTarget,pulse))
                .setCustomBigContentView(ui(c,dim,warm,open,pulseTarget,pulse))
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
    static synchronized void refresh(Context c) {
        post(c,0,false,null,null);
    }
    private static void post(Context c,int target,boolean pulse,
                             Boolean dim,Boolean warm) {
        NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if(nm==null)return;
        if(!enabled(c)||!canPost(c)){
            nm.cancel(ID);
            return;
        }
        nm.notify(ID,build(c,target,pulse,dim,warm));
    }
    /** Brief press/settle pulse works with RemoteViews across process boundaries.
     * Each tap changes the real device setting before updating notification visuals.
     */
    static synchronized void animate(Context c,int target,boolean newState,Runnable completed) {
        final int generation=++animationGeneration;
        final Context app=c.getApplicationContext();
        final Boolean dim=target==ANIMATE_DIM?Boolean.valueOf(newState):null;
        final Boolean warm=target==ANIMATE_FILTER?Boolean.valueOf(newState):null;
        post(app,target,true,dim,warm);
        MAIN.postDelayed(()->{
            synchronized(NotificationController.class){
                if(generation==animationGeneration)
                    post(app,target,false,dim,warm);
            }
        },170);
        MAIN.postDelayed(()->{
            synchronized(NotificationController.class){
                if(generation==animationGeneration)refresh(app);
            }
            if(completed!=null)completed.run();
        },460);
    }
}
