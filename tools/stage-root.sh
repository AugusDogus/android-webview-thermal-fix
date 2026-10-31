#!/system/bin/sh
set -eu
module=/data/adb/modules/px6_webview_thermal
source_dir=${0%/*}
[ "$(id -u)" = 0 ] || { echo 'Run this staging script as root through Magisk.'; exit 1; }
[ "$(getprop ro.build.version.sdk)" = 29 ] || { echo 'Only Android 10 has been tested; installation stopped.'; exit 1; }
service check thermalservice | grep -q 'not found' || { echo 'The thermal service is present or could not be checked; this fix does not apply.'; exit 1; }
stock=$(pm path com.google.android.webview | sed -n 's/^package://p')
case "$stock" in
    /data/app/*/base.apk) ;;
    *) echo 'Expected a single WebView base APK under /data/app; installation stopped.'; exit 1 ;;
esac
[ "$(printf '%s\n' "$stock" | wc -l)" -eq 1 ] || { echo 'Split WebView APKs are unsupported.'; exit 1; }
expected=d31986dfbf12ebda43d7da44c9b6b78cf6ae21203d5b068f30cc07606e1f4cc5
actual=$(sha256sum "$stock" | cut -d ' ' -f 1)
[ "$actual" = "$expected" ] || { echo 'Stock WebView changed. Installation stopped.'; exit 1; }
[ ! -e "$module" ] || { echo 'Module directory already exists. Installation stopped.'; exit 1; }
[ -s "$source_dir/patched.apk" ] || { echo 'Place the locally generated patched.apk alongside this script first.'; exit 1; }
for name in apply-root.sh rollback-root.sh service.sh module.prop; do
    [ -f "$source_dir/$name" ] || { echo "Missing $name; installation stopped."; exit 1; }
done
mkdir -p "$module"
touch "$module/disable" "$module/skip_mount"
cp "$source_dir/patched.apk" "$module/patched.apk"
chmod 644 "$module/patched.apk"
chcon u:object_r:apk_data_file:s0 "$module/patched.apk"
sha256sum "$module/patched.apk" | cut -d ' ' -f 1 > "$module/patched.sha256"
echo "$stock" > "$module/stock-path"
echo "$expected" > "$module/stock.sha256"
cp "$source_dir/apply-root.sh" "$module/apply.sh"
cp "$source_dir/rollback-root.sh" "$module/rollback.sh"
cp "$source_dir/service.sh" "$source_dir/module.prop" "$module/"
chmod 755 "$module/apply.sh" "$module/rollback.sh" "$module/service.sh"
echo 'Patch staged with boot activation disabled.'
