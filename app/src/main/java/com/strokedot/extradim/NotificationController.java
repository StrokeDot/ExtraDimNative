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

/** Optional low-priority controls in the notification shade. No overlay or foreground service. */
public final class NotificationController {
    private NotificationController() { }

    private static final String PREFS = "notification_controls";
    private static final String ENABLED = "enabled";
    private static final String CHANNEL_ID = "extra_dim_shortcuts";
    private static final int NOTIFICATION_ID = 31;

    static boolean enabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(ENABLED, false);
    }

    static boolean canPost(Context c) {
        return Build.VERSION.SDK_INT < 33 ||
                c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED;
    }

    static void setEnabled(Context c, boolean show) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(ENABLED, show).apply();
        refresh(c);
    }

    static void refresh(Context c) {
        NotificationManager manager =
                (NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (!enabled(c) || !canPost(c)) {
            manager.cancel(NOTIFICATION_ID);
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Extra Dim controls", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Control native Extra Dim from the notification shade");
        channel.enableVibration(false);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);

        boolean active = DimController.active(c);
        int strength = DimController.level(c);
        Intent open = new Intent(c, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openApp = PendingIntent.getActivity(c, 11, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new Notification.Builder(c, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_lamp)
                .setContentTitle("Extra Dim • " + (active ? "On" : "Off"))
                .setContentText("Intensity " + strength + "  •  Native display control")
                .setContentIntent(openApp)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setShowWhen(false)
                .setAutoCancel(false)
                .setCategory(Notification.CATEGORY_STATUS)
                .addAction(new Notification.Action.Builder(R.drawable.ic_lamp,
                        active ? "Turn off" : "Turn on",
                        action(c, DimActionReceiver.TOGGLE, 21)).build())
                .addAction(new Notification.Action.Builder(R.drawable.ic_lamp,
                        "Dimmer",
                        action(c, DimActionReceiver.DIMMER, 22)).build())
                .addAction(new Notification.Action.Builder(R.drawable.ic_lamp,
                        "Brighter",
                        action(c, DimActionReceiver.BRIGHTER, 23)).build())
                .build();
        manager.notify(NOTIFICATION_ID, notification);
    }

    private static PendingIntent action(Context c, String name, int requestCode) {
        Intent i = new Intent(c, DimActionReceiver.class).setAction(name);
        return PendingIntent.getBroadcast(c, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
