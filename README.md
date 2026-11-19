# ExtraDimNative

Native Android Extra Dim controller for Xiaomi phones and tablets. Uses Android's `reduce_bright_colors_*` secure settings rather than drawing an overlay. No root required, but it needs a one-time ADB grant of `android.permission.WRITE_SECURE_SETTINGS` on each device. Device compatibility is not guaranteed.

This is an **experimental** project. The primary test devices are Xiaomi 15T, Xiaomi 12T Pro, and a Xiaomi tablet. Keep normal brightness high during testing; the first build is not yet tested on these devices.

The app includes an on/off switch, dimming intensity, persistence setting, optional Quick Settings tile and accessibility shortcut, and a recovery control.

## Build

GitHub Actions will run the Android build and attach a debug APK to the workflow run. Android 12+ (`minSdk 31`).

## One-time permission grant

After installing and connecting a single USB phone with USB debugging enabled:

```sh
adb -d shell pm grant com.strokedot.extradim android.permission.WRITE_SECURE_SETTINGS
```

To turn off dimming at any time:

```sh
adb -d shell settings put secure reduce_bright_colors_activated 0
```

Do not run commands on an unidentified emulator/phone; use `adb -s SERIAL` if multiple are connected.

MIT license. Development project, not a completed production release.


## v0.7 test: detecting settings that revert

Extra Dim Native v0.7 retains the requested UI value while a secure-setting write
is processed. A settings observer will no longer immediately snap the slider back.
The app checks the saved value after 300 ms and 1600 ms, without retrying or
fighting any system service that changes it.

If a control reverts or the screen does not change despite a saved value, tap
"Copy Extra Dim diagnostics" in the app. This produces a local short report
with setting values, the user ID of the app, requests, and observed changes.
No analytics or remote service receives this report.

The ADB recovery command remains:
adb -d shell settings put secure reduce_bright_colors_activated 0

## v0.8: Android 16 hidden-key read fix (development APK)

The v0.7 device report showed that all three Extra Dim `Settings.Secure.getInt`
reads returned the fallback -1 even after `Settings.Secure.putInt` succeeded
and the screen visibly dimmed. These AOSP hidden settings are not @Readable.
Android 12+ Settings.java blocks ordinary third-party apps from reading them.
Android 16 exempts apps carrying the FLAG_TEST_ONLY application flag.

v0.8 adds `android:testOnly=true` in `app/src/debug/AndroidManifest.xml` only.
This is a DEBUG/DEVELOPMENT workaround, **not a production release design**.
It is not distributable via Play Store and cannot be installed by tapping the APK.

With the APK saved to Downloads/Apps in Windows PowerShell:

    & $adb -d install -r -t "$env:USERPROFILE\Downloads\Apps\ExtraDimNative-v0.8-testonly-debug.apk"
    & $adb -d shell pm grant com.strokedot.extradim android.permission.WRITE_SECURE_SETTINGS

If the debug signature differs, disable Extra Dim through the recovery command,
uninstall the old app, then repeat install with `-t` and grant permission.
If reads remain -1, use Copy Extra Dim diagnostics: it now includes the
read exception type/message. A future production edition requires a suitable
native state access or companion bridge; do not rely on the test-only exemption.

## LumaShade in-app updater (v0.11)

The Updates screen checks public GitHub Releases for a newer semantic version and
can download the APK through Android DownloadManager. If no GitHub release has
been published, it checks the source version and points to GitHub Actions.
Before offering Android installation, it verifies that the APK package name and
signer match the installed app. It never silently installs or uninstalls an app.

**Important development limitation:** The current debug APK uses the
`android:testOnly=true` workaround for reading hidden Android 16 Extra Dim
settings. Standard Android package installation normally cannot install
test-only builds; ADB `install -r -t` is required. Also, ephemeral CI debug
signing certificates prevent upgrades unless a persistent keystore is set up.
For production self-updating releases we must configure stable private signing
credentials in GitHub Actions secrets and replace the test-only read workaround
with a production-compatible solution. Do not commit a private keystore.

## v0.12: pinned notification mode and touch targets

An enabled notification defaults to **Keep notification pinned**. This sets
ongoing and no-clear flags to reduce accidental clearing. Android 14+ explicitly
allows people to dismiss even some ongoing notifications while unlocked, so this
is a best-effort request, not a system-level lock. The setting is optional, and
the notification itself can be turned off in the application at any time.

The thin intensity track in the custom notification is a bitmap with **tappable
regions**; it is *not* a draggable Android SeekBar. Try a single tap, not a
horizontal swipe. Large minus/plus controls change the selected intensity in
5% steps. For true continuous dragging use the real sliders in the application.


## Signed in-app updates, no manual GitHub Releases (v0.13+)

Once configured, GitHub Actions automatically publishes a normal signed APK
on a public updates branch with version metadata (latest.json). New versions
are discovered by the app, downloaded, verified for checksum, package/version
and signing certificate, then sent to Android's installer for user approval.

First-time signing setup on Windows (one time only):

1. Run scripts/setup-signing.ps1 in PowerShell from the downloaded repository.
   It generates a permanent private .p12 keystore in Documents/LumaShadeSigning
   (or reuses the existing keystore) and copies its base64 content to clipboard.
2. At https://github.com/StrokeDot/ExtraDimNative/settings/secrets/actions
   create LUMASHADE_KEYSTORE_BASE64 containing the copied base64 key.
3. Create LUMASHADE_SIGNING_PASSWORD with your chosen keystore password.
4. In GitHub Actions, manually run Publish signed LumaShade update once.
   Subsequent versioned app changes automatically publish newer signed APKs.
   No formal GitHub Release is needed.

The FIRST stable signed app will require replacing the old test-only APK,
which used an unrelated temporary debug signature. This is a one-time
installation and WRITE_SECURE_SETTINGS grant by ADB. Later signed updates can
install from within the app without ADB. Android still asks for confirmation
and permission to install unknown apps.

An update check runs when the app opens (at most once per six hours).
It notifies for a newer published version if Android notifications are allowed.

SECURITY: Do not commit your .p12, password, or base64 key publicly. Back up
the signing key securely. If the signing key is lost, future APKs cannot
update the existing app in place.

COMPATIBILITY: The standard installable build is not marked testOnly. Android
16 restricts reading hidden Extra Dim settings from third-party apps, so
LumaShade remembers the last setting that this app successfully wrote. Those
remembered values may be stale if changed by other applications, Xiaomi
Settings or the original accessibility shortcut. This is a known limitation.

Until secrets are configured, the signed OTA workflow intentionally skips
publishing APKs; the old development artifacts still require ADB -t.

## v0.15.10: smooth notification icons

Removed the notification button boxes and press flash. The existing moon and
filter icons now fade in place using Android animated state drawables, including
a reversible ring-to-sun fade for the filter. SystemUI renders animation frames
locally and follows the system animator duration scale (including animations off).
Dim feedback takes 300 ms; filter feedback follows Android Night Light's standard
3000 ms easing. Captions remain steady and neutral. Native screen settings and
notification pinning are unchanged. OEM screen transition timing can differ.
