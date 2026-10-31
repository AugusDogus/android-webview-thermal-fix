#!/system/bin/sh
set -eu
module=/data/adb/modules/px6_webview_thermal
[ ! -e "$module/disable" ] && [ ! -e "$module/remove" ] || { echo 'Module is disabled or pending removal; no mounts changed.'; exit 1; }
stock=$(cat "$module/stock-path")
expected=$(cat "$module/stock.sha256")
patched=$(cat "$module/patched.sha256")
[ "$(sha256sum "$module/patched.apk" | cut -d ' ' -f 1)" = "$patched" ] || { echo 'Patched APK checksum changed; installation stopped.'; exit 1; }
current=$(pm path com.google.android.webview | sed -n 's/^package://p')
[ "$current" = "$stock" ] || { echo 'WebView updated; patch not mounted. Repatch the new version.'; exit 1; }
actual=$(sha256sum "$stock" | cut -d ' ' -f 1)
if [ "$actual" != "$patched" ]; then
    [ "$actual" = "$expected" ] || { echo 'WebView checksum changed; patch not mounted.'; exit 1; }
    mount -o bind "$module/patched.apk" "$stock"
fi
for pid in $(pidof zygote64) $(pidof zygote) $(pidof webview_zygote); do
    actual=$(nsenter -t "$pid" -m -- sha256sum "$stock" | cut -d ' ' -f 1)
    if [ "$actual" != "$patched" ]; then
        [ "$actual" = "$expected" ] || { echo "Unexpected WebView in namespace $pid; stopped."; exit 1; }
        nsenter -t "$pid" -m -- mount -o bind "$module/patched.apk" "$stock"
    fi
done
echo 'Patched WebView mounted. Original APK remains underneath the mount.'
