package com.strokedot.extradim;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import java.util.HashMap;
import android.os.Process;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView;

public final class MainActivity extends Activity {
    private static final int WHITE = 0xFFF4F4F6;
    private static final int GREY = 0xFF9994A5;
    private static final int PANEL = 0xFF242424;
    private static final String GRANT =
        "adb -d shell pm grant com.strokedot.extradim android.permission.WRITE_SECURE_SETTINGS";
    private Switch enabled, persist, notificationControls, pinnedNotification, blueLight;
    private SeekBar intensity;
    private PhoneIllustrationView illustration;
    private TextView permissionBanner, actionStatus;
    private boolean syncing;
    private boolean draggingIntensity;
    private final HashMap<String,Integer> pending = new HashMap<>();
    private final HashMap<String,Integer> generations = new HashMap<>();
    private String history = "";
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable intensityProgressListener = this::syncIntensity;
    private ContentObserver observer;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        buildScreen();
        observer = new ContentObserver(main) {
            @Override public void onChange(boolean selfChange) { onExternalChange("unknown"); }
            @Override public void onChange(boolean selfChange, android.net.Uri uri) {
                onExternalChange(uri == null ? "unknown" : uri.getLastPathSegment());
            }
        };
        trace("opened (app Android user " + (Process.myUid()/100000) + ")");
    }
    private void trace(String entry) {
        String stamp = new java.text.SimpleDateFormat("HH:mm:ss.SSS",java.util.Locale.US)
                .format(new java.util.Date());
        history += stamp + " " + entry + "\n";
        if (history.length() > 6500) history = history.substring(history.length()-6500);
        android.util.Log.i("ExtraDimNative",entry);
    }
    private void onExternalChange(String key) {
        // The slider gets fade frames directly from DimTransition.
        if(DimController.LEVEL.equals(key)&&DimTransition.value(this,key)!=null)return;
        trace("observer " + key + ": active=" + DimController.read(this,DimController.ACTIVE,-1)
                + " level=" + DimController.read(this,DimController.LEVEL,-1)
                + " persist=" + DimController.read(this,DimController.PERSIST,-1));
        main.post(this::sync);
    }
    @Override protected void onResume() {
        super.onResume();
        DimTransition.recover(this);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(DimController.ACTIVE), false, observer);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(DimController.LEVEL), false, observer);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(DimController.PERSIST), false, observer);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(BlueLightController.ACTIVE), false, observer);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(BlueLightController.KELVIN), false, observer);
        DimController.enforceMaxLevel(this);
        DimTransition.addProgressListener(intensityProgressListener);
        sync();
        NotificationController.reconcile(this);
        UpdateActivity.checkOnAppOpen(this);
    }
    @Override protected void onPause() {
        DimTransition.removeProgressListener(intensityProgressListener);
        getContentResolver().unregisterContentObserver(observer);
        super.onPause();
    }
    private int d(float value) { return (int)(value * getResources().getDisplayMetrics().density + .5f); }
    private LinearLayout vertical() {
        LinearLayout v = new LinearLayout(this);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }
    private GradientDrawable rounded(int background, int radius) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(background);
        gd.setCornerRadius(d(radius));
        return gd;
    }
    private TextView text(String value, float sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }
    private LinearLayout.LayoutParams lp(int width, int height) {
        return new LinearLayout.LayoutParams(width < 0 ? width : d(width), height < 0 ? height : d(height));
    }
    private void addGap(LinearLayout list, int height) {
        View spacer = new View(this); list.addView(spacer, lp(1, height));
    }
    private LinearLayout panel() {
        LinearLayout p = vertical();
        p.setBackground(rounded(PANEL, 22));
        p.setPadding(d(20),d(14),d(20),d(14));
        return p;
    }
    private void addWithMargins(LinearLayout parent, View view, int top, int bottom) {
        LinearLayout.LayoutParams l = lp(-1,-2);
        l.topMargin=d(top);
        l.bottomMargin=d(bottom);
        parent.addView(view,l);
    }
    private TextView actionButton(String label, int color, int background) {
        TextView button=text(label,15,color,true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(d(48));
        button.setPadding(d(14),d(12),d(14),d(12));
        button.setBackground(rounded(background,14));
        return button;
    }
    private void feedback(String message) {
        if (actionStatus==null) return;
        actionStatus.setText(message);
        actionStatus.setVisibility(View.VISIBLE);
    }
    private Switch sw() {
        Switch s = new Switch(this);
        s.setShowText(false);
        s.setMinimumHeight(d(48));
        return s;
    }
    private LinearLayout labelSwitch(String label, Switch s) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(d(50));
        TextView labelView = text(label,16,WHITE,true);
        labelView.setOnClickListener(v -> s.setChecked(!s.isChecked()));
        row.addView(labelView,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(s,lp(-2,-2));
        return row;
    }
    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(Color.BLACK);
        FrameLayout outer = new FrameLayout(this);
        LinearLayout body = vertical();
        body.setPadding(d(12),d(38),d(12),d(24));
        int width = Math.min(d(560),getResources().getDisplayMetrics().widthPixels);
        FrameLayout.LayoutParams bodyParams = new FrameLayout.LayoutParams(width, -2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        outer.addView(body,bodyParams);
        scroll.addView(outer);
        setContentView(scroll);

        // Center the app identity below the notification/status area.
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_lumashade);
        logo.setContentDescription("LumaShade");
        top.addView(logo,lp(56,56));
        TextView title = text("LumaShade",28,WHITE,true);
        LinearLayout.LayoutParams titleP = lp(-2,-2);
        titleP.leftMargin = d(12);
        top.addView(title,titleP);
        body.addView(top,lp(-1,74));

        illustration = new PhoneIllustrationView(this);
        illustration.setContentDescription("Extra Dim preview. Long press to restore normal brightness.");
        illustration.setOnLongClickListener(v -> {
            boolean off = DimController.write(this,DimController.ACTIVE,0);
            boolean reset = DimController.write(this,DimController.LEVEL,50);
            sync();
            if (off && reset) Toast.makeText(this,"Extra dim disabled and level restored to 50",Toast.LENGTH_LONG).show();
            else failed();
            return true;
        });
        addWithMargins(body,illustration,10,0);

        LinearLayout masterCard = panel();
        enabled = sw();
        masterCard.addView(labelSwitch("Extra Dim",enabled),lp(-1,-2));
        addWithMargins(body,masterCard,14,14);

        LinearLayout settingsCard = panel();
        settingsCard.addView(text("Intensity",16,WHITE,true),lp(-1,35));
        addGap(settingsCard,8);
        intensity = new SeekBar(this);
        intensity.setMax(DimController.MAX-DimController.MIN);
        intensity.setProgress(DimController.MAX-50);
        settingsCard.addView(intensity,lp(-1,50));
        LinearLayout hints = new LinearLayout(this);
        TextView dimmer = text("Dimmer • 95",13,GREY,false);
        TextView brighter = text("Brighter • 25",13,GREY,false);
        brighter.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        hints.addView(dimmer,new LinearLayout.LayoutParams(0,d(25),1));
        hints.addView(brighter,new LinearLayout.LayoutParams(0,d(25),1));
        settingsCard.addView(hints,lp(-1,-2));
        addGap(settingsCard,16);
        persist=sw();
        settingsCard.addView(labelSwitch("Keep enabled after restart",persist),lp(-1,-2));

        addWithMargins(body,settingsCard,0,18);

        LinearLayout blueCard = panel();
        blueLight = sw();
        blueCard.addView(labelSwitch("Blue light filter",blueLight),lp(-1,-2));
        addWithMargins(body,blueCard,0,15);

        LinearLayout notificationCard = panel();
        notificationControls = sw();
        notificationCard.addView(labelSwitch("Notification bar", notificationControls),lp(-1,-2));
        pinnedNotification = sw();
        notificationCard.addView(labelSwitch("Keep notification pinned", pinnedNotification), lp(-1,-2));
        addWithMargins(body,notificationCard,0,14);
        LinearLayout updateCard = panel();
        TextView updateTitle=text("Updates",16,WHITE,true);
        updateCard.addView(updateTitle,lp(-1,-2));
        addGap(updateCard,10);
        TextView updateButton=actionButton("Check for updates",0xFFBBD7FF,0xFF2D3446);
        updateButton.setOnClickListener(v -> startActivity(new Intent(this,UpdateActivity.class)));
        updateCard.addView(updateButton,lp(-1,-2));
        addWithMargins(body,updateCard,0,14);

        permissionBanner = text("",14,0xFFFFD9A1,false);
        permissionBanner.setBackground(rounded(0xFF29231B,14));
        permissionBanner.setPadding(d(16),d(14),d(16),d(14));
        permissionBanner.setOnClickListener(v -> {
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Extra Dim ADB grant",GRANT));
            Toast.makeText(this,"ADB command copied",Toast.LENGTH_SHORT).show();
        });
        addWithMargins(body,permissionBanner,0,14);

        TextView reset=actionButton("Restore normal brightness",0xFFFFD1DB,0xFF38272F);
        reset.setOnClickListener(v -> {
            if (!DimController.write(this,DimController.ACTIVE,0)) failed();
            sync();
        });
        addWithMargins(body,reset,8,0);

        actionStatus = text("",13,0xFFBBD7FF,false);
        actionStatus.setVisibility(View.GONE);
        actionStatus.setGravity(Gravity.CENTER);
        actionStatus.setPadding(d(12),d(7),d(12),d(7));
        addWithMargins(body,actionStatus,4,2);

        TextView diagnostic=actionButton("Copy LumaShade diagnostics",0xFFBBD7FF,0xFF2D3446);
        diagnostic.setOnClickListener(v -> {
            String report = "LumaShade " + BuildConfig.VERSION_NAME
                + "\nDevice: " + Build.MANUFACTURER + " " + Build.MODEL
                + " Android " + Build.VERSION.RELEASE + " user=" + (Process.myUid()/100000)
                + "\nSecure grant=" + DimController.hasPermission(this)
                + "\nCurrent: " + snapshot()
                + "\nRead issue: " + DimController.lastReadIssue() + "\n" + history;
            ClipboardManager cb = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("LumaShade diagnostics",report));
            Toast.makeText(this,"Diagnostics copied",Toast.LENGTH_SHORT).show();
        });
        addWithMargins(body,diagnostic,8,8);

        enabled.setOnCheckedChangeListener((b,isChecked) -> {
            if (syncing) return;
            applySetting("Extra Dim",DimController.ACTIVE,isChecked ? 1 : 0);
        });
        intensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser) {
                // Apply once on release, not on every touch event.
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { draggingIntensity=true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                boolean wasDragging = draggingIntensity;
                draggingIntensity=false;
                if (!wasDragging) return;
                applySetting("Intensity",DimController.LEVEL,
                        DimController.MAX - bar.getProgress());
            }
        });
        persist.setOnCheckedChangeListener((b,isChecked) -> {
            if (syncing) return;
            applySetting("Restart persistence",DimController.PERSIST,isChecked ? 1 : 0);
        });
        blueLight.setOnCheckedChangeListener((view,checked) -> {
            if (syncing) return;
            applySetting("Blue light filter",BlueLightController.ACTIVE,checked ? 1 : 0);
        });
        pinnedNotification.setOnCheckedChangeListener((button, checked) -> {
            if (syncing) return;
            NotificationController.setPinned(this, checked);

        });
        notificationControls.setOnCheckedChangeListener((button, checked) -> {
            if (syncing) return;
            if (checked && !NotificationController.canPost(this)) {
                syncing = true;
                notificationControls.setChecked(false);
                syncing = false;
                if (Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 310);
                }
                return;
            }
            NotificationController.setEnabled(this, checked);

            sync();
        });
    }
    @Override public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 310) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                NotificationController.setEnabled(this, true);
                Toast.makeText(this,"Notification controls enabled",Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,"Allow notifications to use shade controls",Toast.LENGTH_LONG).show();
            }
            sync();
        }
    }

    private void failed() {
        String issue = DimController.lastWriteIssue();
        String message = "Android rejected the change"
                + (issue.isEmpty() ? "." : ": " + issue);
        feedback(message);
        Toast.makeText(this,message,Toast.LENGTH_LONG).show();
    }
    private String snapshot() {
        return "active=" + DimController.read(this,DimController.ACTIVE,-1)
            + " level=" + DimController.read(this,DimController.LEVEL,-1)
            + " persist=" + DimController.read(this,DimController.PERSIST,-1)
            + " night=" + DimController.read(this,BlueLightController.ACTIVE,-1)
            + " kelvin=" + DimController.read(this,BlueLightController.KELVIN,-1);
    }
    private int visible(String key,int actual) {
        return pending.containsKey(key) ? pending.get(key) : actual;
    }
    private void applySetting(String label, String key, int requested) {
        int before = DimController.read(this,key,-1);
        int generation = generations.getOrDefault(key,0) + 1;
        generations.put(key,generation);
        pending.put(key,requested);
        trace("requested " + key + "=" + requested + " previous=" + before);
        boolean accepted = DimController.write(this,key,requested);
        trace("provider accepted=" + accepted + " immediate=" + DimController.read(this,key,-1)
                + " reason=" + DimController.lastWriteIssue());
        if (!accepted) {
            pending.remove(key);
            failed();
            sync();
            return;
        }
        feedback(label + " — checking…");
        main.postDelayed(() -> verify(label,key,requested,generation,false),300);
        main.postDelayed(() -> verify(label,key,requested,generation,true),1600);
    }
    private void verify(String label,String key,int wanted,int generation,boolean finalCheck) {
        if (isFinishing() || isDestroyed() || generations.getOrDefault(key,0)!=generation) return;
        int observed = DimController.read(this,key,-1);
        trace((finalCheck ? "final " : "early ") + key + " wanted=" + wanted + " actual=" + observed);
        if (!finalCheck) return;
        pending.remove(key);
        feedback(observed == wanted
                ? label + " saved."
                : label + " could not be confirmed. Copy LumaShade diagnostics.");
        sync();
    }
    private void syncIntensity() {
        boolean dimOn = DimController.active(this);
        intensity.setEnabled(DimController.hasPermission(this) && dimOn);
        if (!intensity.isEnabled()) draggingIntensity = false;
        if (draggingIntensity) return;

        Integer level = DimTransition.previewLevel();
        if (level == null) {
            level = dimOn ? visible(DimController.LEVEL, DimController.level(this)) : DimController.MIN;
        }
        intensity.setProgress(DimController.MAX - Math.max(DimController.MIN, Math.min(DimController.MAX, level)));
    }

    private void sync() {
        if (enabled==null) return;
        syncing=true;
        enabled.setChecked(visible(DimController.ACTIVE,
                DimController.read(this,DimController.ACTIVE,0))==1);
        syncIntensity();
        persist.setChecked(visible(DimController.PERSIST,
                DimController.read(this,DimController.PERSIST,0))==1);
        blueLight.setChecked(visible(BlueLightController.ACTIVE,
                DimController.read(this,BlueLightController.ACTIVE,0)) == 1);
        if (illustration != null) illustration.setWarmthEnabled(BlueLightController.active(this));
        notificationControls.setChecked(NotificationController.enabled(this));
        pinnedNotification.setChecked(NotificationController.pinned(this));
        boolean permitted=DimController.hasPermission(this);
        enabled.setEnabled(permitted);
        blueLight.setEnabled(permitted);
        persist.setEnabled(permitted);
        notificationControls.setEnabled(permitted);
        pinnedNotification.setEnabled(NotificationController.enabled(this));
        permissionBanner.setVisibility(permitted?View.GONE:View.VISIBLE);
        if (!permitted) {
            permissionBanner.setText("Permission required. Tap to copy the ADB setup command.");
        }
        syncing=false;
    }
}
