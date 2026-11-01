# WebView 156.0.8078.49 recurrence, 2026-10-31

Spotify began crashing again after Play Store updated WebView from 156.0.8078.48 to 156.0.8078.49. The package path changed at approximately 16:51. The earlier bind mount still covered the old path, so the new unpatched version executed.

The new crash identifies `com.spotify.music`, WebView `807804903`, and a null `IThermalService.registerThermalStatusListener` called by `PowerManager.addThermalStatusListener` from Chromium's `PowerMonitor`. This is the same firmware compatibility failure as before.

## Repair performed

- New stock SHA-256: `10bc3a9c4a97e184a4b55dac8090b670cd1b6b16a365090b45c2947457fd99a7`.
- Added explicit .49 compatibility to the Morphe patch; its exact two-call guard remains intact.
- Morphe Desktop 1.20.0 applied the bundle to manifest/resources/DEX extracted from the actual installed .49 APK. Decompiled `PowerMonitor` confirms both calls now route through `ThermalCompatibility`.
- Helper tests and DEX overlay regression pass.
- To avoid transferring 262 MB twice over the mobile connection, ran the existing `DexOverlay` through Android's `app_process` using a temporary D8-compiled copy. It preserved the original native-library offsets and assembled the patched APK on the device.
- Patched APK SHA-256: `9577659b4ea153b109c8351b18cad145fe86f02c64c4fb0293a2b4a32609a608`.
- Backed up the previous module outside Magisk's active modules directory at `/data/adb/px6_webview_thermal-backups/156.0.8078.48-20261031`.
- Backed up the stock .49 APK at `/sdcard/Download/webview-156.0.8078.49-stock.apk`, verifying its hash.
- Ran the old rollback, updated the module's APK/path/checksums, and applied the new mount in global and zygote namespaces.
- The active .49 package path reads back with the patched checksum. No Spotify data or logins were cleared.

## Still pending

The device viewer failed twice despite its prescribed refresh, and the Spotify relaunch request timed out. The root mount is verified, but a fresh Spotify launch/playback test could not be completed through the required UI tools. Do not describe this repair as playback-verified yet.

Per-app WebView auto-update remains unchanged because the UI became unavailable. Another WebView update can replace the patched path again. Future versions require a newly validated patch; do not silently mount .49 over a different version. Avoid pinning WebView indefinitely. Manager installation and reboot/suspend testing also remain pending.

The original full .49 APK is backed up on-device, not yet off-device. The interrupted compressed transfer was discarded; local code-only archives are not full APK backups.

## WebView .48 restored after startup, 2026-11-01

After the next vehicle startup, PackageManager reported .48 at a different APK
path. The .49 module refused to mount, recording `WebView updated; patch not
mounted. Repatch the new version.` The reason the installed version returned to
.48 has not been established. Qute (`com.ddm.qute`) and Gmail crashed in the same
missing-thermal-service calls, independently of the launcher.

The installed .48 APK matched the original stock SHA-256 above in the main README.
The saved .48 patched APK matched
`94f67ef7ab021c296df895d644be2d93bc8f12565518f8a251a6a0d720dff5d6`.
The .49 module was backed up outside Magisk's active modules directory, then the
verified .48 patch and checksum files were restored with the current package path.
The mount script succeeded in global and zygote namespaces, and the installed
path read back with the patched checksum. Original APK bytes and app data remain
preserved.

A fresh Qute launch reached its welcome screen and stayed alive. Its process
logged the compatibility handler instead of the thermal exception. No agreement
was accepted and no terminal command was executed in Qute. This verifies Qute
startup after the repair, not a physical reboot or future WebView updates.
