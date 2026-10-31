#!/system/bin/sh
set -eu
module=/data/adb/modules/px6_webview_thermal
stock=$(cat "$module/stock-path")
patched=$(cat "$module/patched.sha256")
touch "$module/disable"
for pid in $(pidof zygote64) $(pidof zygote) $(pidof webview_zygote); do
    actual=$(nsenter -t "$pid" -m -- sha256sum "$stock" | cut -d ' ' -f 1)
    if [ "$actual" = "$patched" ]; then
        nsenter -t "$pid" -m -- umount "$stock"
    fi
done
actual=$(sha256sum "$stock" | cut -d ' ' -f 1)
if [ "$actual" = "$patched" ]; then umount "$stock"; fi
actual=$(sha256sum "$stock" | cut -d ' ' -f 1)
expected=$(cat "$module/stock.sha256")
[ "$actual" = "$expected" ] || { echo 'Original APK checksum did not match; inspect mounts before proceeding.'; exit 1; }
echo 'Original WebView restored. Restart affected apps to reload it.'
