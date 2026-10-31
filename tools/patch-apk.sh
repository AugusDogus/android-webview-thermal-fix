#!/usr/bin/env bash
set -euo pipefail
if [[ $# != 2 ]]; then
  echo 'Usage: tools/patch-apk.sh STOCK.apk OUTPUT.apk' >&2
  exit 1
fi
project=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
stock=$(realpath -- "$1")
output=$(realpath -m -- "$2")
[[ ! -e "$output" ]] || { echo 'Output exists; no files changed.' >&2; exit 1; }
expected=d31986dfbf12ebda43d7da44c9b6b78cf6ae21203d5b068f30cc07606e1f4cc5
actual=$(sha256sum "$stock" | cut -d ' ' -f 1)
[[ "$actual" == "$expected" ]] || {
  echo 'Unsupported stock APK. This release requires the exact tested WebView 156.0.8078.48 variant. Do not bypass this check.' >&2
  exit 1
}
work=$(mktemp -d)
trap 'rm -rf -- "$work"' EXIT
cd -- "$project"
./prepare-deps.sh
./gradlew :extension:test :patches:build
# Patching only the manifest and DEX avoids repacking WebView's large native libraries.
python3 - "$stock" "$work/input.apk" <<'PY'
import re
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1]) as source, zipfile.ZipFile(sys.argv[2], 'x') as target:
    for entry in source.infolist():
        if entry.filename in ('AndroidManifest.xml', 'resources.arsc') or re.fullmatch(r'classes(?:[2-9]|[1-9][0-9]+)?\.dex', entry.filename):
            target.writestr(entry.filename, source.read(entry))
PY
java -jar deps/morphe-desktop-1.20.0-all.jar patch "$work/input.apk" \
  -p patches/build/libs/thermal-webview-1.0.0.mpp \
  --bytecode-mode FULL --unsigned -o "$work/patched-code.apk" -t "$work/patcher"
java tools/DexOverlay.java "$stock" "$work/patched-code.apk" "$output"
echo 'Created an APK for the root bind-mount installer. Do not install it as an ordinary app update.'
