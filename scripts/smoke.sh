#!/usr/bin/env bash
# Starts the app on a running emulator (debug and release APK) and records what happens.
# Used by the "smoke" job in .github/workflows/android.yml.
API="${1:-?}"
PKG=com.shdurazi.todo
OUT=smoke-out
mkdir -p "$OUT"

echo "Emulator: Android $(adb shell getprop ro.build.version.release | tr -d '\r') (API $API), $(adb shell wm size | tr -d '\r' | tail -1)"

for variant in debug release; do
  APK="dist/ToDo-$variant.apk"
  echo "::group::[$variant] install and start (API $API)"
  adb uninstall "$PKG" > /dev/null 2>&1 || true
  adb install -r "$APK" || { echo "RESULT[$variant]: install failed"; echo "::endgroup::"; continue; }
  adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS > /dev/null 2>&1 || true
  adb logcat -c
  adb shell am start -W -n "$PKG/.MainActivity"
  sleep 8
  PID=$(adb shell pidof "$PKG" | tr -d '\r')
  if [ -n "$PID" ]; then echo "RESULT[$variant]: running (pid $PID)"; else echo "RESULT[$variant]: NOT running"; fi
  adb logcat -d > "$OUT/logcat-$variant.txt"
  adb exec-out screencap -p > "$OUT/$variant-start.png"
  echo "--- crash report ($variant)"
  grep -nE "FATAL EXCEPTION" -B2 -A45 "$OUT/logcat-$variant.txt" | head -160
  grep -nE "Process: $PKG|ANR in|Fatal signal" -A6 "$OUT/logcat-$variant.txt" | head -40
  echo "::endgroup::"

  if [ -n "$PID" ]; then
    echo "::group::[$variant] monkey test (API $API)"
    adb logcat -c
    adb shell monkey -p "$PKG" -s 7 --throttle 150 --pct-syskeys 0 --pct-appswitch 0 --pct-nav 0 --pct-majornav 0 -v 300 > "$OUT/monkey-$variant.txt" 2>&1 || true
    tail -8 "$OUT/monkey-$variant.txt"
    adb logcat -d > "$OUT/logcat-monkey-$variant.txt"
    grep -nE "FATAL EXCEPTION" -B2 -A45 "$OUT/logcat-monkey-$variant.txt" | head -160
    adb exec-out screencap -p > "$OUT/$variant-after-monkey.png"
    echo "::endgroup::"
  fi
done
exit 0
