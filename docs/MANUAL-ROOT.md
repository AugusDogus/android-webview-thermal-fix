# Manual root installation (prototype route)

This is the separate prototype route, not the planned Morphe Manager installation. It is an advanced, experimental root installation for the exact configuration described in the [README](../README.md). Run computer commands from the repository root. No manager app is needed to apply the fix.

## 1. Check and back up

Requirements: a Linux computer with Bash, ADB, Java 21, Python 3, `curl`, `sha256sum`, and Android SDK platform 36. Set `ANDROID_HOME` if the SDK is not at `~/Android/Sdk`. The head unit needs Magisk root, `su -mm`, `nsenter`, and working ADB access. Keep a working route to Magisk or recovery so you can disable the module.

If several devices are attached, set `ANDROID_SERIAL` to your head unit's ADB serial. None of the commands below should target your phone.

Confirm the missing thermal service and the matching crash described in the README:

```sh
adb shell service check thermalservice
adb shell dumpsys package com.google.android.webview | grep versionName
adb shell pm path com.google.android.webview
```

There must be one `package:/data/app/.../base.apk` result and version `156.0.8078.48`. Pull that exact path, substituting the output from the previous command:

```sh
mkdir -p build/local
adb pull '/data/app/REPLACE_WITH_ACTUAL_DIRECTORY/base.apk' build/local/stock.apk
sha256sum build/local/stock.apk
```

The checksum must be:

```text
d31986dfbf12ebda43d7da44c9b6b78cf6ae21203d5b068f30cc07606e1f4cc5
```

Stop on a mismatch. Another architecture, version, or an already-mounted patch needs separate review. Do not weaken the checksum checks. Keep this unmodified backup outside the device too.

## 2. Build the patched file locally

```sh
bash tools/patch-apk.sh build/local/stock.apk build/local/patched.apk
```

The script verifies the input hash, downloads its pinned build dependency with a checksum check, builds/tests the helper, patches the two call sites, and overlays the resulting DEX onto a copy of the stock APK. It does not connect to the head unit or install anything. First use downloads Gradle and build dependencies as well.

## 3. Stage the disabled module

The following commands copy the files to a staging directory and then create a disabled Magisk module. They do not overwrite an existing module. Review the scripts before running them as root.

```sh
adb shell mkdir -p /data/local/tmp/webview-thermal
adb push build/local/patched.apk /data/local/tmp/webview-thermal/patched.apk
adb push tools/stage-root.sh tools/apply-root.sh tools/rollback-root.sh tools/service.sh tools/module.prop /data/local/tmp/webview-thermal/
adb shell su -mm -c 'sh /data/local/tmp/webview-thermal/stage-root.sh'
```

Staging requires Android 10, no thermal service, the expected stock APK hash, and no existing `/data/adb/modules/px6_webview_thermal` directory. It leaves `disable` and `skip_mount` markers in place. If staging fails after creating the directory, leave it disabled and inspect the error before retrying.

## 4. Enable and apply

This changes the APK visible to processes through a bind mount. The original file remains underneath. Run while parked, then restart affected apps:

```sh
adb shell su -mm -c 'rm /data/adb/modules/px6_webview_thermal/disable && sh /data/adb/modules/px6_webview_thermal/apply.sh'
```

The script checks global and zygote mount namespaces. Any error needs investigation; a partial mount is possible if a later namespace operation fails. Use the rollback command below if necessary. Do not uninstall or clear WebView/Spotify data.

Launch Spotify normally and verify that your original crash is gone. For subsequent boots, `service.sh` waits up to 120 seconds for `sys.boot_completed` and records its result at:

```text
/data/adb/modules/px6_webview_thermal/status.log
```

Automatic boot activation is implemented, but a physical reboot has not yet been validated on the test unit. A reboot should only be tested when you can recover through Magisk or ADB.

After a successful installation, the staging copies can be removed:

```sh
adb shell rm -r /data/local/tmp/webview-thermal
```

## Roll back

Disable the module and unmount the patched APK:

```sh
adb shell su -mm -c 'sh /data/adb/modules/px6_webview_thermal/rollback.sh'
```

Restart apps using WebView. The rollback sets the module's `disable` marker and checks that the original APK hash is restored. It does not delete app data or the stock APK.

Alternatively, disable **PX6 WebView thermal compatibility** in Magisk and reboot. If the saved package path has disappeared after an update, disable the module in Magisk and reboot instead of forcing the old mount onto the new package. If normal startup fails, use your device's supported Magisk module recovery procedure.

## Updating WebView

An updated path or checksum causes the module to stop applying. This release does not support automatically patching a new WebView version. Disable the module, reboot to restore the stock view, and validate the new version before producing another patch. Do not pin an old WebView indefinitely just to keep this workaround active.
