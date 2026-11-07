#!/usr/bin/env bash
# Starts the app on a running emulator (debug and release APK), clicks through a few things, runs a short monkey
# test and records everything in the job log (crash reports, UI dumps and screenshots as base64 JPEGs).
# Used by the "smoke" job in .github/workflows/android.yml.
API="${1:-?}"
PKG=com.shdurazi.todo
OUT=smoke-out
mkdir -p "$OUT"

echo "Emulator: Android $(adb shell getprop ro.build.version.release | tr -d '\r') (API $API), $(adb shell wm size | tr -d '\r' | tail -1)"

shot() { # shot <name>: saves a screenshot and prints it into the log as base64 so it can be read from the job log
  adb exec-out screencap -p > "$OUT/$1.png"
  if command -v convert > /dev/null; then
    convert "$OUT/$1.png" -resize 540x -quality 80 "$OUT/$1.jpg" && F="$OUT/$1.jpg" || F="$OUT/$1.png"
  else
    F="$OUT/$1.png"
  fi
  echo "SHOT-BEGIN $1"; base64 -w0 "$F"; echo; echo "SHOT-END $1"
}

dump() { adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1; adb exec-out cat /sdcard/ui.xml > "$OUT/ui.xml"; }

tap_text() { # tap_text <text or description>
  dump
  XY=$(python3 scripts/ui_find.py "$1" < "$OUT/ui.xml")
  if [ -n "$XY" ]; then echo "tap '$1' at $XY"; adb shell input tap $XY; else echo "no node containing '$1'"; fi
  sleep 1.5
}

crash_report() { # crash_report <logcat file>
  grep -E "AndroidRuntime" "$1" | grep -E "FATAL EXCEPTION|Process: $PKG|Caused by|Exception|Error:|at com\.shdurazi" | cut -c1-260 | head -70
  grep -nE "ANR in $PKG|Fatal signal" -A6 "$1" | head -30
}

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
  echo "--- crash report ($variant)"
  crash_report "$OUT/logcat-$variant.txt"
  echo "::endgroup::"

  if [ -n "$PID" ]; then
    echo "::group::[$variant] click through (API $API)"
    shot "$variant-1-home"
    dump; echo "--- UI nodes (home)"; python3 scripts/ui_dump.py < "$OUT/ui.xml" | head -70
    tap_text "New To Do"
    shot "$variant-2-typing"
    adb shell input text "Call%sSara%stomorrow%s5pm%s!!"
    sleep 1.5
    shot "$variant-3-typed"
    dump; echo "--- UI nodes (typing)"; python3 scripts/ui_dump.py < "$OUT/ui.xml" | head -50
    adb shell input keyevent 66
    sleep 2
    shot "$variant-4-added"
    adb shell input keyevent 4
    sleep 1.5
    shot "$variant-5-after"
    dump; echo "--- UI nodes (after)"; python3 scripts/ui_dump.py < "$OUT/ui.xml" | head -70
    PID2=$(adb shell pidof "$PKG" | tr -d '\r')
    if [ -n "$PID2" ]; then echo "RESULT[$variant]: still running after click-through"; else echo "RESULT[$variant]: DIED during click-through"; fi
    adb logcat -d > "$OUT/logcat-click-$variant.txt"
    crash_report "$OUT/logcat-click-$variant.txt"
    echo "::endgroup::"

    echo "::group::[$variant] monkey test (API $API)"
    adb logcat -c
    adb shell monkey -p "$PKG" -s 7 --throttle 150 --pct-syskeys 0 --pct-appswitch 0 --pct-nav 0 --pct-majornav 0 -v 300 > "$OUT/monkey-$variant.txt" 2>&1 || true
    tail -8 "$OUT/monkey-$variant.txt"
    adb logcat -d > "$OUT/logcat-monkey-$variant.txt"
    crash_report "$OUT/logcat-monkey-$variant.txt"
    shot "$variant-6-after-monkey"
    echo "::endgroup::"
  fi
done
exit 0
