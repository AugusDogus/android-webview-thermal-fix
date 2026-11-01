# Morphe WebView thermal compatibility patch

A custom [Morphe](https://github.com/MorpheApp/morphe-manager) patch that fixes a WebView crash observed in Spotify on a rooted PX6 head unit running **Hal9k Mod 5.0.0 / Android 10**. This ROM has no Android `thermalservice`. Newer WebView code calls that missing service and crashes instead of treating thermal status as unavailable.

The patch changes two WebView calls. The intended user workflow is to import the `.mpp` bundle into Morphe Manager, patch WebView, and use Manager's Magisk root installer. The original prototype was installed with a separate reversible bind-mount script, preserving the installed package, original APK, and app data. **Manager installation is still awaiting device validation.**

**Experimental, tested on one head unit with one exact WebView APK.** This is not a general fix for every Spotify or WebView crash. Read the compatibility checks before installing.

## Does this fix your crash?

The observed exception contains:

```text
java.lang.NullPointerException: Attempt to invoke interface method
'boolean android.os.IThermalService.registerThermalStatusListener(...)'
on a null object reference
    at android.os.PowerManager.addThermalStatusListener(...)
    at org.chromium.base.PowerMonitor...
```

Check the service from a computer connected through ADB:

```sh
adb shell service check thermalservice
```

On the affected unit, the result is `Service thermalservice: not found`. A missing service alone is not enough: the app's crash must also match this failure. If the service exists or your crash has another cause, this patch does not apply.

## Tested hardware and ROM

- Hardware: PX6, Rockchip RK3399, 4 GB RAM.
- ROM: Hal9k Mod **5.0.0**, identified by `persist.hal9k.version_tuned=5.0.0`.
- Android: **10 / API 29**.
- Base firmware build: `rk3399-userdebug 10 QQ2A.200305.004.A1 eng.hct2.20210311.174916 test-keys`.
- Base firmware date: March 11, 2021. Reported security patch: May 5, 2020.
- MCU: `MTCE_HA_V3.57_1`. Included for identification, not established as a cause of this Android service failure.
- Root: Magisk **22.1**.
- WebView: `com.google.android.webview` **156.0.8078.48**, single APK. The matching **156.0.8078.49** variant has also been patched and mounted after an update; fresh playback verification is pending. See [the recurrence and repair notes](docs/WEBVIEW-49-REPAIR.md).
- Stock APK SHA-256: `d31986dfbf12ebda43d7da44c9b6b78cf6ae21203d5b068f30cc07606e1f4cc5`.

These are the device's reported identifiers. Other Hal9k versions, stock HCT firmware, other CPU architectures, split APKs, and other WebView versions have not been validated. A matching ROM name alone does not establish compatibility. The supplied scripts refuse a different APK checksum.

## Install and remove

The target is [Morphe Manager 2.3.0](https://github.com/MorpheApp/morphe-manager/releases/tag/v2.3.0), the latest stable release checked on October 31, 2026, with Magisk root. Its minimum Android API is 26, below the test unit's API 29. Read [installation status and validation steps](docs/INSTALL.md) before using the bundle.

The [manual root guide](docs/MANUAL-ROOT.md) records the separate prototype route. Building from source requires Java 21 and Android SDK platform 36; the optional desktop APK script additionally needs Linux, Python 3, and ADB for transfer.

The repository and releases contain the patch and tools, not Google's original or patched WebView APK. Generate the patched file from your own installed APK. **Do not uninstall WebView or install the generated APK as an ordinary update.** It is intended only for the root mount described in the guide.

The supplied manual module validates the package path and both APK checksums before mounting. A WebView update causes that module to skip activation until compatibility is reviewed and the patch rebuilt. Morphe Manager has its own root module; its WebView-specific installation, update, and rollback behavior still needs on-device testing. Keep WebView security updates enabled; this project does not make an old ROM or an old browser engine secure.

For the manual prototype only, disable **PX6 WebView thermal compatibility** in Magisk and reboot. Immediate rollback, without replacing the original APK:

```sh
adb shell su -mm -c 'sh /data/adb/modules/px6_webview_thermal/rollback.sh'
```

Restart apps that were using WebView afterward. More manual recovery details are in the [manual root guide](docs/MANUAL-ROOT.md). Do not use that module path to uninstall a Manager-managed patch.

## How it works

The patch redirects `PowerMonitor`'s `addThermalStatusListener` and `getCurrentThermalStatus` calls to a small Java helper. It catches only the observed missing-`IThermalService` null-pointer failure originating in the expected `PowerManager` method. Other exceptions propagate; working thermal services retain their normal behavior.

When the service is absent, listener registration is skipped and the status query returns Chromium's existing `-1` unavailable sentinel. It does not fake a temperature or disable a working thermal service. Vehicle outside temperature is unrelated to this Android service.

`DexOverlay.java` appends replacement DEX entries to a copy of the original APK and writes a new ZIP directory. Native-library bytes and offsets stay intact. The result's APK signature is not valid for a normal package installation, which is why the root mount is required.

## Validation and limitations

- Reproduced the original Spotify crash and confirmed the missing Binder service.
- Three helper tests cover the observed failures and rejection of unrelated null-pointer exceptions.
- Decompiled the patched WebView and verified both thermal calls use the helper.
- Ran the helper on the head unit: the original registration crashed, patched registration returned, and patched status was `-1`.
- Tested two fresh Spotify launches after mounting. Home and library loaded, login was preserved, and the compatibility handler logged execution without the original fatal exception.
- Executed the original boot script twice successfully without rebooting. **A physical reboot has not been tested.**
- Rebuilt the bundle against current Morphe patcher **2.1.0** using Desktop **1.20.0**. The current patcher successfully applied it to the actual WebView manifest and DEX recovered from the stock backup, and both rewritten call sites were inspected again. The interrupted APK copy is not a complete installable APK.
- Official Manager **2.3.0** has been installed on the head unit, and its installed version was verified. The current bundle was transferred and its checksum verified. Importing, patching, and root installation through Manager have **not** yet been tested.
- The public staging script adds portable package-path discovery and preflight checks. Its new installation path has not been exercised on a second head unit.

## Development

```sh
./prepare-deps.sh
./gradlew :extension:test :patches:build
python3 tools/test_dex_overlay.py
```

The source build pins [Morphe Desktop 1.20.0](https://github.com/MorpheApp/morphe-desktop/releases/tag/v1.20.0) / [patcher API 2.1.0](https://github.com/MorpheApp/morphe-patcher/releases/tag/v2.1.0), matching Manager 2.3.0's patcher dependency. Morphe supplies the bytecode patching engine and Manager supplies the intended user installation workflow. Magisk supplies the root access for mounting. The helper running inside WebView does not call Morphe or require Manager to stay open.

The build produces `patches/build/libs/thermal-webview-1.0.0.mpp`. See [the manual root guide](docs/MANUAL-ROOT.md) for the desktop command that builds a mountable APK from the verified stock file. Published bundle availability and Manager validation status are tracked in [the installation guide](docs/INSTALL.md).

## Reporting compatibility

Include hardware, Android version, ROM version/build, WebView version and APK hash, the `service check thermalservice` result, and the relevant crash stack. State whether a full reboot was tested. Remove account details, device serials, network addresses, and other personal information from logs. Do not upload WebView APKs to issues.

## License

GPL-3.0-or-later for this project's code. Third-party build dependencies retain their own licenses. This project is not affiliated with Google, Spotify, or Hal9k.
