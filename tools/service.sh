#!/system/bin/sh
module=${0%/*}
attempt=0
while [ "$(getprop sys.boot_completed)" != 1 ]; do
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 120 ]; then
        echo 'Boot did not finish in 120 seconds; original WebView remains active.' > "$module/status.log"
        exit 1
    fi
    sleep 1
done
sh "$module/apply.sh" > "$module/status.log" 2>&1
