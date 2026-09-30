#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"

./gradlew testDebugUnitTest assembleDebug

echo "Verification complete."
echo "APK: app/build/outputs/apk/debug/app-debug.apk"
