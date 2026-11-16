package com.strokedot.extradim;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
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
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int WHITE = 0xFFF4F4F6;
    private static final int GREY = 0xFF9994A5;
    private static final int PANEL = 0xFF242424;
    private static final String GRANT =
        "adb -d shell pm grant com.strokedot.extradim android.permission.WRITE_SECURE_SETTINGS";
    private Switch enabled, persist, shortcut;
    private SeekBar intensity;
    private TextView permissionBanner, currentStatus, actionStatus;
    private boolean syncing;
    private boolean draggingIntensity;
    private final Handler main = new Handler(Looper.getMainLooper());
    private ContentObserver observer;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        buildScreen();
        observer = new ContentObserver(main) {
            @Override public void onChange(boolean selfChange) { main.post(MainActivity.this::sync); }
        };
    }
    @Override protected void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(DimController.ACTIVE), false, observer);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(DimController.LEVEL), false, observer);
        getContentResolver().registerContentObserver(Settings.Secure.getUriFor(DimController.PERSIST), false, observer);
        sync();
    }
    @Override protected void onPause() {
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
        body.setPadding(d(12),d(16),d(12),d(24));
        int width = Math.min(d(560),getResources().getDisplayMetrics().widthPixels);
        FrameLayout.LayoutParams bodyParams = new FrameLayout.LayoutParams(width, -2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        outer.addView(body,bodyParams);
        scroll.addView(outer);
        setContentView(scroll);

        LinearLayout top= new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹",42,WHITE,false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("Back");
        back.setOnClickListener(v -> finish());
        top.addView(back,lp(56,56));
        TextView title = text("Extra dim",23,WHITE,true);
        title.setGravity(Gravity.CENTER);
        top.addView(title,new LinearLayout.LayoutParams(0,d(56),1));
        top.addView(new View(this),lp(56,56));
        body.addView(top,lp(-1,60));

        TextView intro = text("Make your screen dimmer so it’s more comfortable to read",16,GREY,true);
        intro.setPadding(d(16),0,d(14),0);
        addWithMargins(body,intro,10,0);
        TextView device = text("Device: " + Build.MANUFACTURER + " " + Build.MODEL
                + "  ·  Android " + Build.VERSION.RELEASE,12,GREY,false);
        device.setPadding(d(16),d(10),0,0);
        body.addView(device,lp(-1,-2));

        PhoneIllustrationView illustration = new PhoneIllustrationView(this);
        illustration.setContentDescription("Extra Dim preview. Long press to restore normal brightness.");
        illustration.setOnLongClickListener(v -> {
            boolean off = DimController.write(this,DimController.ACTIVE,0);
            boolean reset = DimController.write(this,DimController.LEVEL,50);
            sync();
            if (off && reset) Toast.makeText(this,"Extra dim disabled and level restored to 50",Toast.LENGTH_LONG).show();
            else failed();
            return true;
        });
        addWithMargins(body,illustration,26,0);

        LinearLayout masterCard = panel();
        enabled = sw();
        masterCard.addView(labelSwitch("Make screen extra dim",enabled),lp(-1,-2));
        addWithMargins(body,masterCard,14,25);

        TextView options = text("Options",16,GREY,true);
        options.setPadding(d(16),0,0,0);
        addWithMargins(body,options,0,12);

        LinearLayout settingsCard = panel();
        settingsCard.addView(text("Intensity",16,WHITE,false),lp(-1,35));
        addGap(settingsCard,8);
        intensity = new SeekBar(this);
        intensity.setMax(DimController.MAX-DimController.MIN);
        intensity.setProgress(DimController.MAX-50);
        settingsCard.addView(intensity,lp(-1,50));
        LinearLayout hints = new LinearLayout(this);
        TextView dimmer = text("Dimmer",13,GREY,false);
        TextView brighter = text("Brighter",13,GREY,false);
        brighter.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        hints.addView(dimmer,new LinearLayout.LayoutParams(0,d(25),1));
        hints.addView(brighter,new LinearLayout.LayoutParams(0,d(25),1));
        settingsCard.addView(hints,lp(-1,-2));
        addGap(settingsCard,16);
        persist=sw();
        settingsCard.addView(labelSwitch("Keep on after device restarts",persist),lp(-1,-2));
        addGap(settingsCard,20);

        LinearLayout shortcutRow=new LinearLayout(this);
        shortcutRow.setOrientation(LinearLayout.HORIZONTAL);
        shortcutRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout shortcutLabels = vertical();
        shortcutLabels.addView(text("Extra dim shortcut",16,WHITE,true),lp(-1,-2));
        shortcutLabels.addView(text("Accessibility button",14,GREY,false),lp(-1,-2));
        shortcutRow.addView(shortcutLabels,new LinearLayout.LayoutParams(0,-2,1));
        shortcut=sw();
        shortcutRow.addView(shortcut,lp(-2,-2));
        settingsCard.addView(shortcutRow,lp(-1,60));
        addWithMargins(body,settingsCard,0,18);

        permissionBanner = text("",14,0xFFFFD9A1,false);
        permissionBanner.setBackground(rounded(0xFF29231B,14));
        permissionBanner.setPadding(d(16),d(14),d(16),d(14));
        permissionBanner.setOnClickListener(v -> {
            ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Extra Dim ADB grant",GRANT));
            Toast.makeText(this,"ADB command copied",Toast.LENGTH_SHORT).show();
        });
        addWithMargins(body,permissionBanner,0,14);

        TextView help=text("Extra dim can be helpful when:\n• Your phone’s default minimum brightness is still too bright\n• You’re using your phone in a dark environment before bed",13,GREY,false);
        help.setPadding(d(16),d(2),d(12),d(5));
        body.addView(help,lp(-1,-2));

        TextView reset=text("Restore normal brightness",14,WHITE,true);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(d(12),d(12),d(12),d(12));
        reset.setOnClickListener(v -> {
            if (!DimController.write(this,DimController.ACTIVE,0)) failed();
            sync();
        });
        addWithMargins(body,reset,15,0);

        actionStatus = text("Ready for a native dimming test. Changes are checked after a short delay.",13,0xFFBBD7FF,false);
        actionStatus.setGravity(Gravity.CENTER);
        actionStatus.setPadding(d(12),d(7),d(12),d(7));
        addWithMargins(body,actionStatus,4,2);

        currentStatus = text("",12,GREY,false);
        currentStatus.setGravity(Gravity.CENTER);
        addWithMargins(body,currentStatus,4,4);
        TextView safety = text("Keep normal brightness above minimum while testing. If the display becomes unreadable, use the restore control or the ADB recovery command.",12,GREY,false);
        safety.setGravity(Gravity.CENTER);
        safety.setPadding(d(16),d(6),d(16),0);
        addWithMargins(body,safety,0,4);

        enabled.setOnCheckedChangeListener((b,isChecked) -> {
            if (syncing) return;
            applySetting("Extra Dim",DimController.ACTIVE,isChecked ? 1 : 0);
        });
        intensity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser) {
                // Do not flood HyperOS with writes on every slider movement.
                if (fromUser && draggingIntensity) {
                    actionStatus.setText("Selected intensity: " + (DimController.MAX - progress)
                            + ". Release the slider to apply.");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { draggingIntensity=true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                draggingIntensity=false;
                applySetting("Intensity",DimController.LEVEL,
                        DimController.MAX - bar.getProgress());
            }
        });
        persist.setOnCheckedChangeListener((b,isChecked) -> {
            if (syncing) return;
            applySetting("Restart persistence",DimController.PERSIST,isChecked ? 1 : 0);
        });
        shortcut.setOnCheckedChangeListener((b,isChecked) -> {
            if (syncing) return;
            Toast.makeText(this,"Enable or disable ‘Extra dim shortcut’ in Accessibility settings",Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            main.postDelayed(this::sync,500);
        });
        shortcutLabels.setOnClickListener(v -> shortcut.setChecked(!shortcut.isChecked()));
    }
    private boolean shortcutEnabled() {
        AccessibilityManager mgr=(AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
        if (mgr==null) return false;
        for (AccessibilityServiceInfo info : mgr.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getId()!=null && info.getId().contains(getPackageName()+"/")) return true;
        }
        return false;
    }
    private void failed() {
        String issue = DimController.lastWriteIssue();
        String message = "Android rejected the change"
                + (issue.isEmpty() ? "." : ": " + issue);
        if (actionStatus != null) actionStatus.setText(message);
        Toast.makeText(this,message,Toast.LENGTH_LONG).show();
    }
    private void applySetting(String label, String key, int requested) {
        if (!DimController.write(this,key,requested)) {
            failed();
            sync();
            return;
        }
        actionStatus.setText(label + ": write accepted; checking saved value...");
        // Check after the SettingsProvider/HyperOS observers have had time to run.
        main.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            int stored = DimController.read(this,key,Integer.MIN_VALUE);
            if (stored != requested) {
                String actual = stored == Integer.MIN_VALUE ? "not present" : Integer.toString(stored);
                actionStatus.setText(label + " did not persist. Requested "
                        + requested + ", Android now reports " + actual
                        + ". HyperOS may be resetting the value.");
            } else {
                actionStatus.setText(label + " saved (" + stored
                        + "). Check whether screen brightness actually changed.");
            }
            sync();
        },650);
    }
    private void sync() {
        if (enabled==null) return;
        syncing=true;
        enabled.setChecked(DimController.active(this));
        if (!draggingIntensity) intensity.setProgress(DimController.MAX-DimController.level(this));
        persist.setChecked(DimController.persistent(this));
        shortcut.setChecked(shortcutEnabled());
        boolean available = DimController.featureReportedAvailable(this);
        currentStatus.setText("Native dimming " + (DimController.active(this) ? "ON" : "OFF")
                + "  ·  saved level " + DimController.level(this)
                + "  ·  " + (DimController.hasPermission(this) ? "ADB grant ready" : "ADB grant needed")
                + (available ? "" : "\nNative feature not reported as available on this device; verify visually."));
        boolean permitted=DimController.hasPermission(this);
        enabled.setEnabled(permitted);
        intensity.setEnabled(permitted);
        persist.setEnabled(permitted);
        shortcut.setEnabled(permitted);
        permissionBanner.setVisibility(permitted?View.GONE:View.VISIBLE);
        if (!permitted) {
            permissionBanner.setText("One-time ADB setup required. Tap to copy the permission command, run it from PowerShell, then return to this screen.");
        }
        syncing=false;
    }
}
