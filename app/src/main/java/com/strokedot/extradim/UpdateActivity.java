package com.strokedot.extradim;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * LumaShade self-updater. No formal GitHub release required: a signed APK and
 * JSON metadata are published automatically on the public updates branch.
 * Does not attempt silent installation, permission bypass or signature override.
 */
public final class UpdateActivity extends Activity {
    private static final String META =
            "https://raw.githubusercontent.com/StrokeDot/ExtraDimNative/updates/latest.json";
    private static final String APK_PREFIX =
            "https://raw.githubusercontent.com/StrokeDot/ExtraDimNative/updates/apks/";
    private static final String BUILDS =
            "https://github.com/StrokeDot/ExtraDimNative/actions/workflows/android-updates.yml";
    private static final String PREFS="updates";
    private static final String UPDATE_CHANNEL="lumashade_app_updates";
    private static final long CHECK_INTERVAL=6L*60*60*1000;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());

    private TextView status,checkButton,downloadButton,installButton;
    private String latestName,assetUrl,fileName,expectedSha;
    private int latestCode;
    private long downloadId=-1;
    private boolean active=true,autoInstallAfterDownload;
    private boolean inspecting;

    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);d.setCornerRadius(dp(14));return d;
    }
    private TextView text(String value,int size,boolean bold){
        TextView t=new TextView(this);
        t.setText(value);t.setTextSize(size);t.setTextColor(0xFFE9EDF6);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private TextView button(String label,LinearLayout panel) {
        TextView t=text(label,15,true);
        t.setPadding(dp(12),dp(14),dp(12),dp(14));
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(0xFF2A3449));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(12);panel.addView(t,p);
        return t;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(0xFF090B10);
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24),dp(40),dp(24),dp(24));
        scroll.addView(panel);setContentView(scroll);
        TextView back=text("‹  LumaShade",17,false);
        back.setOnClickListener(v->finish());panel.addView(back);
        TextView title=text("App updates",27,true);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(24);panel.addView(title,p);
        TextView current=text("Installed: v"+BuildConfig.VERSION_NAME,15,false);
        current.setTextColor(0xFF9DA9BD);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.topMargin=dp(12);panel.addView(current,cp);
        status=text("Checking for a newer LumaShade APK…",14,false);
        status.setTextColor(0xFFC3CEDD);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);
        sp.topMargin=dp(20);panel.addView(status,sp);
        checkButton=button("Check again",panel);
        checkButton.setOnClickListener(v->check());
        downloadButton=button("Download and install update",panel);
        downloadButton.setVisibility(View.GONE);
        downloadButton.setOnClickListener(v->download());
        installButton=button("Install downloaded APK",panel);
        installButton.setVisibility(View.GONE);
        installButton.setOnClickListener(v->verifyAndInstall());
        TextView source=button("View update build history",panel);
        source.setOnClickListener(v->startActivity(
                new Intent(Intent.ACTION_VIEW,Uri.parse(BUILDS))));
        TextView info=text("Updates are published automatically after a signed build. "
                +"Android will ask you to confirm installation. A one-time switch "
                +"from the old ADB-only test build is required because those builds "
                +"used a temporary signing key and Android's test-only flag.",
                13,false);
        info.setTextColor(0xFF9BA6B9);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,-2);
        ip.topMargin=dp(20);panel.addView(info,ip);

        android.content.SharedPreferences pref=getSharedPreferences(PREFS,MODE_PRIVATE);
        downloadId=pref.getLong("download_id",-1L);
        fileName=pref.getString("download_name",null);
        expectedSha=pref.getString("download_sha",null);
        latestCode=pref.getInt("download_code",0);
        autoInstallAfterDownload=false;
        if(downloadId>=0)checkDownload();
        check();
    }
    @Override protected void onResume(){
        super.onResume();
        if(downloadId>=0)checkDownload();
    }
    @Override protected void onDestroy(){active=false;super.onDestroy();}
    private void report(String message){if(active)status.setText(message);}
    private static String readUrl(String url) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(10000);conn.setReadTimeout(10000);
        conn.setUseCaches(false);
        conn.setRequestProperty("Cache-Control","no-cache");
        conn.setRequestProperty("User-Agent","LumaShade/OTA");
        try{
            int code=conn.getResponseCode();
            if(code==404)throw new java.io.FileNotFoundException("No signed APK published yet");
            if(code<200||code>=300)throw new java.io.IOException("HTTP "+code);
            try(InputStream in=conn.getInputStream();ByteArrayOutputStream bytes=new ByteArrayOutputStream()){
                byte[] buf=new byte[4096];int count;
                while((count=in.read(buf))!=-1){
                    bytes.write(buf,0,count);
                    if(bytes.size()>100000)throw new java.io.IOException("Update metadata too large");
                }
                return bytes.toString("UTF-8");
            }
        }finally{conn.disconnect();}
    }
    private static JSONObject metadata() throws Exception {
        JSONObject info=new JSONObject(readUrl(META+"?t="+System.currentTimeMillis()));
        String version=info.getString("versionName");
        int code=info.getInt("versionCode");
        String apk=info.getString("apkUrl");
        String hash=info.getString("sha256").toLowerCase(Locale.US);
        if(!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || code<=0 ||
           !apk.equals(APK_PREFIX+"LumaShade-v"+version+".apk") ||
           !hash.matches("[a-f0-9]{64}")) {
            throw new SecurityException("Unrecognized update metadata");
        }
        return info;
    }
    private void check(){
        report("Checking the latest automatically published APK…");
        checkButton.setEnabled(false);
        downloadButton.setVisibility(View.GONE);
        new Thread(()->{
            try{
                JSONObject data=metadata();
                String version=data.getString("versionName");
                int code=data.getInt("versionCode");
                String url=data.getString("apkUrl");
                String hash=data.getString("sha256");
                MAIN.post(()->{
                    if(!active)return;
                    checkButton.setEnabled(true);
                    if(code<=BuildConfig.VERSION_CODE) {
                        report("LumaShade v"+BuildConfig.VERSION_NAME+
                                " is up to date. Latest published: v"+version+".");
                    }else{
                        latestName=version;
                        latestCode=code;
                        assetUrl=url;
                        fileName="LumaShade-v"+version+".apk";
                        expectedSha=hash;
                        report("Update available: LumaShade v"+version+
                                ". Tap Download and install.");
                        downloadButton.setVisibility(View.VISIBLE);
                        downloadButton.setText("Download and install v"+version);
                    }
                });
            }catch(Exception e){
                MAIN.post(()->{
                    if(!active)return;
                    checkButton.setEnabled(true);
                    report(e instanceof java.io.FileNotFoundException
                            ?"No installable update has been published yet. "
                                +"The signing setup must be completed once in GitHub."
                            :"Unable to check updates: "+e.getMessage());
                });
            }
        },"LumaShade-CheckUpdates").start();
    }
    private void download(){
        if(assetUrl==null||fileName==null||expectedSha==null)return;
        if(!assetUrl.startsWith(APK_PREFIX))return;
        try{
            File folder=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if(folder==null)throw new IllegalStateException("Download folder unavailable");
            File apk=new File(folder,fileName);
            if(apk.exists()&&!apk.delete())throw new java.io.IOException("Cannot replace old download");
            DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request req=new DownloadManager.Request(Uri.parse(assetUrl));
            req.setTitle("LumaShade v"+latestName);
            req.setDescription("Verified in-app update");
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalFilesDir(this,Environment.DIRECTORY_DOWNLOADS,fileName);
            downloadId=dm.enqueue(req);
            getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                    .putLong("download_id",downloadId)
                    .putString("download_name",fileName)
                    .putString("download_sha",expectedSha)
                    .putInt("download_code",latestCode)
                    .apply();
            autoInstallAfterDownload=true;
            downloadButton.setVisibility(View.GONE);
            report("Downloading LumaShade v"+latestName+"…");
            MAIN.postDelayed(this::checkDownload,1500);
        }catch(Exception e){report("Download could not start: "+e.getMessage());}
    }
    private void checkDownload(){
        if(!active||downloadId<0||inspecting)return;
        inspecting=true;
        try{
            DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            try(android.database.Cursor cursor=dm.query(
                    new DownloadManager.Query().setFilterById(downloadId))){
                if(cursor==null||!cursor.moveToFirst())return;
                int state=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if(state==DownloadManager.STATUS_SUCCESSFUL){
                    report("Download complete. Verifying APK…");
                    installButton.setVisibility(View.VISIBLE);
                    if(autoInstallAfterDownload){
                        autoInstallAfterDownload=false;
                        verifyAndInstall();
                    }
                }else if(state==DownloadManager.STATUS_FAILED){
                    report("Download failed; tap Check again.");
                    downloadId=-1;
                }else{
                    MAIN.postDelayed(this::checkDownload,1700);
                }
            }
        }catch(Exception e){report("Unable to read download status: "+e.getMessage());}
        finally{inspecting=false;}
    }
    private static String sha256(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){
            byte[] buf=new byte[16*1024];int length;
            while((length=in.read(buf))!=-1)digest.update(buf,0,length);
        }
        StringBuilder hex=new StringBuilder();
        for(byte b:digest.digest())hex.append(String.format(Locale.US,"%02x",b & 0xff));
        return hex.toString();
    }
    private static boolean sameSigner(PackageInfo before,PackageInfo after){
        if(before.signingInfo==null||after.signingInfo==null)return false;
        Signature[] current=before.signingInfo.getApkContentsSigners();
        Signature[] next=after.signingInfo.getApkContentsSigners();
        if(current==null||next==null||current.length==0||next.length==0)return false;
        for(Signature a:current)for(Signature b:next)if(a.equals(b))return true;
        return false;
    }
    private void verifyAndInstall(){
        String localName=getSharedPreferences(PREFS,MODE_PRIVATE)
                .getString("download_name",fileName);
        String expected=getSharedPreferences(PREFS,MODE_PRIVATE)
                .getString("download_sha",expectedSha);
        int requiredCode=getSharedPreferences(PREFS,MODE_PRIVATE).getInt("download_code",0);
        long id=getSharedPreferences(PREFS,MODE_PRIVATE).getLong("download_id",-1);
        if(localName==null||expected==null||id<0){report("No download to install.");return;}
        report("Verifying update signature and checksum…");
        installButton.setEnabled(false);
        new Thread(()->{
            try{
                if(!localName.matches("LumaShade-v[0-9]+\\.[0-9]+\\.[0-9]+\\.apk"))
                    throw new SecurityException("Unexpected APK name");
                File folder=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                File apk=new File(folder,localName);
                if(!apk.isFile()||!sha256(apk).equalsIgnoreCase(expected))
                    throw new SecurityException("APK checksum mismatch. Install cancelled.");
                PackageManager pm=getPackageManager();
                PackageInfo archive=pm.getPackageArchiveInfo(apk.getAbsolutePath(),
                        PackageManager.GET_SIGNING_CERTIFICATES);
                PackageInfo installed=pm.getPackageInfo(getPackageName(),
                        PackageManager.GET_SIGNING_CERTIFICATES);
                if(archive==null||!getPackageName().equals(archive.packageName))
                    throw new SecurityException("APK is not the LumaShade package");
                if(archive.getLongVersionCode()<=installed.getLongVersionCode()
                        ||archive.getLongVersionCode()!=requiredCode)
                    throw new SecurityException("APK version is not newer than the installed app");
                if(archive.applicationInfo!=null &&
                        (archive.applicationInfo.flags&ApplicationInfo.FLAG_TEST_ONLY)!=0)
                    throw new SecurityException("Test-only APK cannot use normal Android installer");
                if(!sameSigner(installed,archive))
                    throw new SecurityException(
                        "Different app signing key. The first stable version requires "
                        +"a one-time clean installation; future releases can update in-app.");
                MAIN.post(()->{
                    if(!active)return;
                    installButton.setEnabled(true);
                    if(!pm.canRequestPackageInstalls()){
                        report("Allow LumaShade to install updates on the next screen, then tap Install.");
                        startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                Uri.parse("package:"+getPackageName())));
                        return;
                    }
                    DownloadManager manager=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
                    Uri uri=manager.getUriForDownloadedFile(id);
                    if(uri==null){report("Downloaded APK URI unavailable.");return;}
                    Intent intent=new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri,"application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    report("Android will ask you to approve the LumaShade update.");
                    try{startActivity(intent);}
                    catch(Exception ex){report("Cannot open Android installer: "+ex.getMessage());}
                });
            }catch(Exception e){
                MAIN.post(()->{
                    if(!active)return;
                    installButton.setEnabled(true);
                    report("Update not installed: "+e.getMessage());
                });
            }
        },"LumaShade-VerifyApk").start();
    }
    /** At most twice daily; alerts once per new version; no background polling service. */
    public static void checkOnAppOpen(Context context){
        Context app=context.getApplicationContext();
        android.content.SharedPreferences p=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        long now=System.currentTimeMillis();
        if(now-p.getLong("last_checked",0)<CHECK_INTERVAL)return;
        p.edit().putLong("last_checked",now).apply();
        new Thread(()->{
            try{
                JSONObject data=metadata();
                int newerCode=data.getInt("versionCode");
                if(newerCode<=BuildConfig.VERSION_CODE ||
                   newerCode==p.getInt("notified_version",-1))return;
                NotificationManager manager=(NotificationManager)
                        app.getSystemService(Context.NOTIFICATION_SERVICE);
                if(manager==null)return;
                if(Build.VERSION.SDK_INT>=33 &&
                   app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                         !=PackageManager.PERMISSION_GRANTED)return;
                NotificationChannel channel=new NotificationChannel(
                        UPDATE_CHANNEL,"LumaShade updates",NotificationManager.IMPORTANCE_DEFAULT);
                manager.createNotificationChannel(channel);
                Intent open=new Intent(app,UpdateActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent intent=PendingIntent.getActivity(app,298,open,
                        PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                Notification note=new Notification.Builder(app,UPDATE_CHANNEL)
                        .setSmallIcon(R.drawable.ic_lumashade_mono)
                        .setContentTitle("LumaShade update available")
                        .setContentText("Version "+data.getString("versionName")+" is ready to install")
                        .setContentIntent(intent)
                        .setAutoCancel(true)
                        .build();
                manager.notify(298,note);
                p.edit().putInt("notified_version",newerCode).apply();
            }catch(Exception ignored) { /* No published OTA or offline. */ }
        },"LumaShade-BackgroundCheck").start();
    }
}
