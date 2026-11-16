package com.strokedot.extradim;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Compact two-mode notification. Only one intensity panel can be open at once.
 * A standard draggable SeekBar is not supported by Android RemoteViews, so the
 * displayed thin progress track uses 10 tappable regions and +/- controls.
 */
public final class NotificationController {
    private NotificationController() {}
    private static final String PREFS="notification_controls";
    private static final String ENABLED="enabled";
    private static final String PANEL="open_panel";
    private static final String CHANNEL="lumashade_controls_v3";
    private static final int ID=31;
    static final int NONE=0,DIM=1,WARM=2;
    private static boolean posting=false;

    static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                .getBoolean(ENABLED,false);
    }
    static boolean canPost(Context c) {
        return Build.VERSION.SDK_INT<33||
                c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                ==PackageManager.PERMISSION_GRANTED;
    }
    static int selected(Context c) {
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getInt(PANEL,NONE);
    }
    static void select(Context c,int panel) {
        if(panel<NONE||panel>WARM)return;
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putInt(PANEL,panel).apply();
        refresh(c);
    }
    static void setEnabled(Context c,boolean show) {
        c.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(ENABLED,show).apply();
        refresh(c);
    }
    private static PendingIntent command(Context c,String action,int req,int value) {
        Intent intent=new Intent(c,DimActionReceiver.class).setAction(action)
                .putExtra(DimActionReceiver.EXTRA_STRENGTH,value);
        return PendingIntent.getBroadcast(c,req,intent,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    private static void wireHeader(Context c,RemoteViews v,boolean compact,PendingIntent open) {
        v.setOnClickPendingIntent(compact?R.id.n_small_settings:R.id.n_settings,open);
        v.setOnClickPendingIntent(compact?R.id.n_small_dim:R.id.n_pick_dim,
                command(c,DimActionReceiver.SELECT_DIM,100,0));
        v.setOnClickPendingIntent(compact?R.id.n_small_warm:R.id.n_pick_warm,
                command(c,DimActionReceiver.SELECT_WARM,101,0));
    }
    /** Match the app's continuous blue/amber SeekBar visual; touch is discrete. */
    private static Bitmap track(int percent,boolean active,boolean blue) {
        Bitmap bitmap=Bitmap.createBitmap(480,48,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        float progress=Math.max(0,Math.min(100,percent))/100f;
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(6f);
        p.setColor(0xFF515665);
        canvas.drawLine(17,24,463,24,p);
        p.setColor(active?(blue?0xFF4B8BF5:0xFFF2C06D):0xFF747784);
        canvas.drawLine(17,24,17+446*progress,24,p);
        p.setStyle(Paint.Style.FILL);
        canvas.drawCircle(17+446*progress,24,active?14:11,p);
        return bitmap;
    }
    static synchronized void refresh(Context c) {
        if(posting)return;
        posting=true;
        try {
            NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
            if(nm==null)return;
            if(!enabled(c)||!canPost(c)){nm.cancel(ID);return;}
            NotificationChannel ch=new NotificationChannel(
                    CHANNEL,"LumaShade controls",NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Extra Dim, blue light and application settings");
            ch.setSound(null,null);ch.enableVibration(false);
            nm.createNotificationChannel(ch);
            int selected=selected(c);
            boolean dim=DimController.active(c),warm=BlueLightController.active(c);
            int dimPercent=Math.max(0,Math.min(100,
                    Math.round(100f*(DimController.level(c)-DimController.MIN)/
                            (DimController.MAX-DimController.MIN))));
            int warmPercent=BlueLightController.strength(c);
            PendingIntent open=PendingIntent.getActivity(c,41,
                    new Intent(c,MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            RemoteViews compact=new RemoteViews(c.getPackageName(),R.layout.notification_lumashade_small);
            wireHeader(c,compact,true,open);
            RemoteViews big=new RemoteViews(c.getPackageName(),R.layout.notification_lumashade_big);
            wireHeader(c,big,false,open);
            big.setViewVisibility(R.id.n_dim_panel,selected==DIM?View.VISIBLE:View.GONE);
            big.setViewVisibility(R.id.n_warm_panel,selected==WARM?View.VISIBLE:View.GONE);
            big.setViewVisibility(R.id.n_help,selected==NONE?View.VISIBLE:View.GONE);
            big.setImageViewBitmap(R.id.n_dim_bar,track(dimPercent,dim,true));
            big.setImageViewBitmap(R.id.n_warm_bar,track(warmPercent,warm,false));
            big.setTextViewText(R.id.n_dim_value,dimPercent+"%");
            big.setTextViewText(R.id.n_warm_value,warmPercent+"%");
            big.setTextViewText(R.id.n_dim_power,dim?"On":"Off");
            big.setTextViewText(R.id.n_warm_power,warm?"On":"Off");
            big.setOnClickPendingIntent(R.id.n_dim_close,
                    command(c,DimActionReceiver.SELECT_NONE,102,0));
            big.setOnClickPendingIntent(R.id.n_warm_close,
                    command(c,DimActionReceiver.SELECT_NONE,103,0));
            big.setOnClickPendingIntent(R.id.n_dim_power,
                    command(c,DimActionReceiver.DIM_TOGGLE,104,0));
            big.setOnClickPendingIntent(R.id.n_warm_power,
                    command(c,DimActionReceiver.FILTER_TOGGLE,105,0));
            big.setOnClickPendingIntent(R.id.n_dim_minus,
                    command(c,DimActionReceiver.DIM_ADJUST,106,-5));
            big.setOnClickPendingIntent(R.id.n_dim_plus,
                    command(c,DimActionReceiver.DIM_ADJUST,107,5));
            big.setOnClickPendingIntent(R.id.n_warm_minus,
                    command(c,DimActionReceiver.FILTER_ADJUST,108,-5));
            big.setOnClickPendingIntent(R.id.n_warm_plus,
                    command(c,DimActionReceiver.FILTER_ADJUST,109,5));
            for(int i=1;i<=10;i++) {
                int dimZone=c.getResources().getIdentifier("n_dim_hit"+i,"id",c.getPackageName());
                int warmZone=c.getResources().getIdentifier("n_warm_hit"+i,"id",c.getPackageName());
                if(dimZone!=0)big.setOnClickPendingIntent(dimZone,
                        command(c,DimActionReceiver.DIM_STRENGTH,120+i,i*10));
                if(warmZone!=0)big.setOnClickPendingIntent(warmZone,
                        command(c,DimActionReceiver.FILTER_STRENGTH,140+i,i*10));
            }
            Notification n=new Notification.Builder(c,CHANNEL)
                    .setSmallIcon(R.drawable.ic_lumashade_mono)
                    .setContentTitle("LumaShade")
                    .setContentText("Comfort controls")
                    .setContentIntent(open)
                    .setStyle(new Notification.DecoratedCustomViewStyle())
                    .setCustomContentView(compact)
                    .setCustomBigContentView(big)
                    .setOnlyAlertOnce(true)
                    .setShowWhen(false)
                    .setOngoing(true)
                    .setCategory(Notification.CATEGORY_STATUS)
                    .build();
            nm.notify(ID,n);
        } finally {posting=false;}
    }
}
