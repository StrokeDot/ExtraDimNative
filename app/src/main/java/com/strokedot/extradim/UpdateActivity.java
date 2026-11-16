package com.strokedot.extradim;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
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
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** GitHub public release updater. Never installs silently or bypasses signatures. */
public final class UpdateActivity extends Activity {
    private static final String LATEST =
            "https://api.github.com/repos/StrokeDot/ExtraDimNative/releases/latest";
    private static final String VERSION_FILE =
            "https://raw.githubusercontent.com/StrokeDot/ExtraDimNative/main/app/build.gradle";
    private static final String BUILDS =
            "https://github.com/StrokeDot/ExtraDimNative/actions/workflows/android-debug.yml";
    private static final String RELEASES =
            "https://github.com/StrokeDot/ExtraDimNative/releases";
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView status,checkButton,downloadButton,installButton,openButton;
    private String availableVersion,assetUrl,fileName;
    private long activeDownload=-1;
    private final String PREFS="updates";
    private boolean destroyed;

    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private GradientDrawable bg(int color){
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(color);shape.setCornerRadius(dp(15));return shape;
    }
    private TextView label(String message,int size,boolean bold){
        TextView t=new TextView(this);
        t.setText(message);t.setTextSize(size);t.setTextColor(0xFFE9EBF5);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }
    private TextView button(String text, LinearLayout parent) {
        TextView t=label(text,15,true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(bg(0xFF283449));
        t.setPadding(dp(12),dp(14),dp(12),dp(14));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);
        p.topMargin=dp(12);
        parent.addView(t,p);
        return t;
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(0xFF08090D);
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(24),dp(42),dp(24),dp(28));
        scroll.addView(panel);
        setContentView(scroll);
        TextView back=label("‹  LumaShade",17,false);
        back.setOnClickListener(v->finish());
        panel.addView(back);
        TextView title=label("App updates",26,true);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);
        tp.topMargin=dp(25);
        panel.addView(title,tp);
        TextView current=label("Installed: v"+BuildConfig.VERSION_NAME,15,false);
        current.setTextColor(0xFF9DA9BC);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);
        cp.topMargin=dp(10);panel.addView(current,cp);
        status=label("Check for a newer published GitHub release.",14,false);
        status.setTextColor(0xFFC4CBD8);
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);
        sp.topMargin=dp(20);panel.addView(status,sp);
        checkButton=button("Check for updates",panel);
        checkButton.setOnClickListener(v->check());
        downloadButton=button("Download new APK",panel);
        downloadButton.setVisibility(View.GONE);
        downloadButton.setOnClickListener(v->download());
        installButton=button("Open Android installer",panel);
        installButton.setVisibility(View.GONE);
        installButton.setOnClickListener(v->install());
        openButton=button("View GitHub builds",panel);
        openButton.setOnClickListener(v->openUrl(BUILDS));
        TextView note=label(
            "Development limitation: current builds are marked test-only and "
            +"must be installed with ADB -t. Android's normal installer may reject "
            +"them. In-app installation also requires a stable signing certificate "
            +"across releases. LumaShade will never silently uninstall your app.",
            13,false);
        note.setTextColor(0xFF8E99AE);
        LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);
        np.topMargin=dp(20);panel.addView(note,np);
        activeDownload=getPreferences(MODE_PRIVATE).getLong("download_id",-1L);
        if(activeDownload>=0)inspectDownload();
    }
    @Override protected void onResume() {
        super.onResume();
        if(activeDownload>=0)inspectDownload();
    }
    @Override protected void onDestroy() {destroyed=true;super.onDestroy();}
    private void report(String message){
        if(!destroyed)status.setText(message);
    }
    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));
    }
    private static String readUrl(String url) throws Exception {
        HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent","LumaShade-Updater");
        conn.setRequestProperty("Accept","application/vnd.github+json");
        try {
            int code=conn.getResponseCode();
            if(code==404)throw new java.io.FileNotFoundException("No published releases");
            if(code<200||code>=300)throw new java.io.IOException("HTTP "+code);
            try(InputStream stream=conn.getInputStream();
                BufferedReader reader=new BufferedReader(
                    new InputStreamReader(stream,StandardCharsets.UTF_8))) {
                StringBuilder b=new StringBuilder();
                String line;
                while((line=reader.readLine())!=null){
                    b.append(line).append('\n');
                    if(b.length()>100000)throw new java.io.IOException("Response too large");
                }
                return b.toString();
            }
        } finally {conn.disconnect();}
    }
    private static int[] parts(String version) {
        Matcher m=Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)").matcher(version);
        if(!m.find())return null;
        return new int[]{Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3))};
    }
    private static boolean newer(String remote,String local) {
        int[] r=parts(remote),l=parts(local);
        if(r==null||l==null)return false;
        for(int i=0;i<3;i++) {
            if(r[i]>l[i])return true;
            if(r[i]<l[i])return false;
        }
        return false;
    }
    private void check() {
        report("Checking published releases…");
        checkButton.setEnabled(false);
        downloadButton.setVisibility(View.GONE);
        installButton.setVisibility(View.GONE);
        new Thread(()->{
            try {
                JSONObject release=new JSONObject(readUrl(LATEST));
                String tag=release.optString("tag_name");
                JSONArray assets=release.optJSONArray("assets");
                String selectedUrl=null,selectedName=null;
                if(assets!=null)for(int i=0;i<assets.length();i++) {
                    JSONObject item=assets.optJSONObject(i);
                    if(item==null)continue;
                    String name=item.optString("name");
                    String url=item.optString("browser_download_url");
                    if(name.startsWith("LumaShade-v")&&name.endsWith(".apk")
                            &&url.startsWith("https://github.com/StrokeDot/ExtraDimNative/releases/download/")) {
                        selectedUrl=url;selectedName=name;break;
                    }
                }
                final String url=selectedUrl;
                final String name=selectedName;
                main.post(()->{
                    checkButton.setEnabled(true);
                    if(!newer(tag,BuildConfig.VERSION_NAME)) {
                        report("You have v"+BuildConfig.VERSION_NAME+
                            ". Latest published release: "+tag+" (no newer release).");
                    } else if(url==null) {
                        report("A newer release ("+tag+") exists but does not include an APK.");
                    } else {
                        availableVersion=tag;
                        assetUrl=url;fileName=name;
                        report("New release "+tag+" found. The APK is available to download.");
                        downloadButton.setVisibility(View.VISIBLE);
                    }
                });
            } catch(java.io.FileNotFoundException noRelease) {
                // Public repo has no release. Give a useful development-build check.
                try {
                    String gradle=readUrl(VERSION_FILE);
                    Matcher matcher=Pattern.compile("versionName\\s*'([^']+)'").matcher(gradle);
                    String dev=matcher.find()?matcher.group(1):"unknown";
                    main.post(()->{
                        checkButton.setEnabled(true);
                        report(newer(dev,BuildConfig.VERSION_NAME)
                            ?"New development build v"+dev+" exists on GitHub Actions. "
                              +"It is not a published, in-app-installable release."
                            :"No published GitHub release yet. Current source version: "+dev+
                              ". Use View GitHub builds for development APKs.");
                    });
                } catch(Exception error) {
                    main.post(()->{
                        checkButton.setEnabled(true);
                        report("No published GitHub releases yet. Open GitHub builds below.");
                    });
                }
            } catch(Exception error) {
                main.post(()->{
                    checkButton.setEnabled(true);
                    report("Update check failed: "+error.getMessage());
                });
            }
        },"LumaShade-UpdateCheck").start();
    }
    private void download() {
        if(assetUrl==null||fileName==null)return;
        if(!fileName.matches("LumaShade-v[0-9A-Za-z._-]+\\.apk")) {
            report("Release APK filename was not recognized.");return;
        }
        try {
            File folder=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if(folder==null)throw new IllegalStateException("Download folder unavailable");
            File dest=new File(folder,fileName);
            if(dest.exists()&&!dest.delete())throw new IllegalStateException("Old APK cannot be replaced");
            DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            DownloadManager.Request req=new DownloadManager.Request(Uri.parse(assetUrl));
            req.setTitle("LumaShade "+availableVersion);
            req.setDescription("Downloading update APK");
            req.setAllowedOverMetered(true);
            req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            req.setDestinationInExternalFilesDir(this,Environment.DIRECTORY_DOWNLOADS,fileName);
            activeDownload=dm.enqueue(req);
            getPreferences(MODE_PRIVATE).edit()
                .putLong("download_id",activeDownload).putString("download_name",fileName).apply();
            report("Downloading update…");
            downloadButton.setVisibility(View.GONE);
            main.postDelayed(this::inspectDownload,1000);
        } catch(Exception e) {report("Download failed: "+e.getMessage());}
    }
    private void inspectDownload() {
        if(activeDownload<0||destroyed)return;
        DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
        try(android.database.Cursor cursor=dm.query(new DownloadManager.Query()
                .setFilterById(activeDownload))) {
            if(cursor==null||!cursor.moveToFirst())return;
            int state=cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if(state==DownloadManager.STATUS_SUCCESSFUL) {
                fileName=getPreferences(MODE_PRIVATE).getString("download_name",fileName);
                report("Update APK downloaded. Check the package and installer requirements before proceeding.");
                installButton.setVisibility(View.VISIBLE);
            } else if(state==DownloadManager.STATUS_FAILED) {
                report("Android could not download the update. Try GitHub builds.");
            } else {
                report("Downloading update…");
                main.postDelayed(this::inspectDownload,1500);
            }
        } catch(Exception e){report("Download status unavailable: "+e.getMessage());}
    }
    private boolean signaturesMatch(PackageInfo installed, PackageInfo archive) {
        if(installed==null||archive==null||installed.signingInfo==null||archive.signingInfo==null)
            return false;
        Signature[] expected=installed.signingInfo.getApkContentsSigners();
        Signature[] offered=archive.signingInfo.getApkContentsSigners();
        if(expected==null||offered==null||expected.length==0||offered.length==0)return false;
        for(Signature s:expected)for(Signature t:offered)if(s.equals(t))return true;
        return false;
    }
    private void install() {
        try {
            if(fileName==null)fileName=getPreferences(MODE_PRIVATE).getString("download_name",null);
            if(fileName==null) {report("No update APK was downloaded.");return;}
            File folder=getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            File apk=new File(folder,fileName);
            if(!apk.isFile()){report("Downloaded APK not found.");return;}
            PackageManager pm=getPackageManager();
            PackageInfo archive=pm.getPackageArchiveInfo(apk.getAbsolutePath(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
            PackageInfo installed=pm.getPackageInfo(getPackageName(),
                    PackageManager.GET_SIGNING_CERTIFICATES);
            if(archive==null||!getPackageName().equals(archive.packageName)) {
                report("APK package doesn't match LumaShade. Installation cancelled.");return;
            }
            if(!signaturesMatch(installed,archive)){
                report("Update has a different signing certificate. Android cannot update "
                        +"without uninstalling. No changes were made. "
                        +"A permanent signing key must be configured for future releases.");
                return;
            }
            if(archive.applicationInfo!=null &&
                    (archive.applicationInfo.flags&ApplicationInfo.FLAG_TEST_ONLY)!=0) {
                report("This is a test-only APK. Android normally requires 'adb install -r -t' "
                        +"from your PC. The downloaded file is in this app's external files. "
                        +"The ordinary installer cannot reliably install this test build.");
                return;
            }
            if(!pm.canRequestPackageInstalls()){
                report("Allow updates from LumaShade in Android's Install unknown apps settings, then tap again.");
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:"+getPackageName())));
                return;
            }
            DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            Uri uri=dm.getUriForDownloadedFile(activeDownload);
            if(uri==null){report("Android download URI unavailable.");return;}
            Intent intent=new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri,"application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch(Exception e){report("Cannot open installer: "+e.getMessage());}
    }
}
