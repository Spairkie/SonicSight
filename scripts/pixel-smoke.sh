#!/usr/bin/env bash
set -euo pipefail

PACKAGE="com.sonicsight.app"
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
STAMP="$(date +%Y%m%d-%H%M%S)"
OUT="captures/device-$STAMP"
mkdir -p "$OUT"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is not on PATH" >&2
  exit 1
fi

SERIAL="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
if [[ -z "$SERIAL" ]]; then
  echo "No authorized Android device found. Check USB debugging and the RSA prompt." >&2
  adb devices -l
  exit 1
fi

echo "Using device: $SERIAL"
adb -s "$SERIAL" devices -l | tee "$OUT/adb-device.txt"
{
  echo "manufacturer=$(adb -s "$SERIAL" shell getprop ro.product.manufacturer | tr -d '\r')"
  echo "model=$(adb -s "$SERIAL" shell getprop ro.product.model | tr -d '\r')"
  echo "device=$(adb -s "$SERIAL" shell getprop ro.product.device | tr -d '\r')"
  echo "android=$(adb -s "$SERIAL" shell getprop ro.build.version.release | tr -d '\r')"
  echo "sdk=$(adb -s "$SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
  echo "fingerprint=$(adb -s "$SERIAL" shell getprop ro.build.fingerprint | tr -d '\r')"
} | tee "$OUT/device.txt"

adb -s "$SERIAL" shell dumpsys media.camera > "$OUT/camera-dumpsys.txt" || true

if [[ -f "$APK" ]]; then
  echo "Installing $APK"
  adb -s "$SERIAL" install -r "$APK"
else
  echo "APK not found at $APK; collecting diagnostics without installing."
fi

adb -s "$SERIAL" shell am force-stop "$PACKAGE" || true
adb -s "$SERIAL" shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 2
adb -s "$SERIAL" shell dumpsys package "$PACKAGE" > "$OUT/package-dumpsys.txt" || true

echo
echo "SonicSight launched. Diagnostics saved to $OUT"
echo "Confirm the app requested CAMERA only, then follow TESTING.md for the 100/200/440 Hz baseline."
