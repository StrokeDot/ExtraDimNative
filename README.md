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
