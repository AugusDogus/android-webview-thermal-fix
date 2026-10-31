#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
mkdir -p deps
dependency=deps/morphe-desktop-1.20.0-all.jar
if [[ ! -f "$dependency" ]]; then
  curl --fail --location --retry 3 \
    https://github.com/MorpheApp/morphe-desktop/releases/download/v1.20.0/morphe-desktop-1.20.0-all.jar \
    --output "$dependency"
fi
echo '679f579b3ad743ac0ca697ef71e57430d00be59411b04494afd1845c43fb79f0  deps/morphe-desktop-1.20.0-all.jar' | sha256sum --check
