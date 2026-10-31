# Morphe Manager installation status

The patch is being prepared for **Morphe Manager 2.3.0** with **patcher 2.1.0** and Magisk root. The `.mpp` bundle builds and applies successfully with the matching Desktop patcher. **The complete Manager installation path has not yet been tested. Do not treat these steps as a verified installation guide.**

On-device validation is pending. Source is available for review; the downloadable release remains a draft until the installation is tested.

## Pending device validation

1. Reconnect the PX6 head unit running Hal9k Mod 5.0.0 and Android 10.
2. Official Morphe Manager 2.3.0 is installed and its version verified on the test unit. Confirm it starts and obtains Magisk root.
3. Preserve the stock WebView backup and the working prototype module. Disable/unmount the prototype before testing Manager so two modules do not mount over the same package. Confirm the original WebView hash is visible again.
4. Import this bundle as a local patch source. Select the original `com.google.android.webview` 156.0.8078.48 APK and enable **WebView: tolerate missing thermal service**.
5. Patch in Manager. Inspect the output for both rewritten thermal calls and valid APK/native-library alignment.
6. Install using Manager's root mount mechanism. Do not uninstall WebView, clear its data, or use a normal re-signed APK installation. The tested unit uses Magisk; NoMount-only mode is not the intended path.
7. Verify fresh Spotify launches, the compatibility log, preserved login, and absence of the original crash. Verify a second WebView client if available.
8. Verify Manager rollback restores the original WebView. Reapply and test a full reboot before claiming boot persistence works.
9. Record the actual UI steps, module behavior, and results here, then publish the release.

Manager's root installer differs from the manual prototype. A successful desktop patch does not prove Manager installation, native-library loading, package signature handling, rollback, or boot persistence works for WebView.

## Manual prototype

The [manual root guide](MANUAL-ROOT.md) documents the original approach and its limits. Its rollback command applies only to the `px6_webview_thermal` prototype module, not to a module created by Manager.
