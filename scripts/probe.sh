#!/usr/bin/env bash
# The emulators went away (the process exits, after about a minute in which nothing answers) in the accessibility
# window's part of the island test, with animations on. This script looks for the cause: one scenario per CI leg, the
# emulator is watched for two minutes and, if it vanishes, explained as far as that is possible (see common.sh).
# Used by the "probe" job in .github/workflows/android.yml (only when the commit message says [probe]).
API="${1:-?}"
SCEN="${2:-a11y-anim}"
PKG=com.shdurazi.todo
OUT=smoke-out
mkdir -p "$OUT"
source scripts/common.sh

A11Y=1 SCALE=1 SHOTS=0 TAPS=0 ISLAND=1
case "$SCEN" in
  a11y-anim) ;;
  a11y-noanim) SCALE=0 ;;
  overlay-anim) A11Y=0 ;;
  overlay-anim-taps) A11Y=0 TAPS=1 ;;
  a11y-idle) ISLAND=0 ;;
  a11y-anim-shots) SHOTS=1 ;;
  a11y-anim-taps) TAPS=1 ;;
esac
echo "PROBE $SCEN on API $API: accessibility=$A11Y animations=$SCALE screenshots=$SHOTS taps=$TAPS island=$ISLAND"

wait_boot
adb install -r dist/ToDo-debug.apk > /dev/null
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS > /dev/null 2>&1 || true
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
cutout_set emu01
adb shell settings put global animator_duration_scale "$SCALE"
adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1
sleep 3
if [ "$A11Y" = 1 ]; then
  adb shell settings put secure enabled_accessibility_services "$PKG/com.shdurazi.todo.island.IslandAccessibilityService"
  adb shell settings put secure accessibility_enabled 1
  sleep 4
fi
adb logcat -c
adb shell am start -n "$PKG/.MainActivity" -a com.shdurazi.todo.action.START_FOCUS --es block_id b-deep > /dev/null 2>&1
sleep 3
if [ "$ISLAND" = 1 ]; then adb shell input keyevent 3; fi
echo "set up, watching for 2 minutes"

T0=$(date +%s)
for i in $(seq 1 24); do
  sleep 5
  now=$(( $(date +%s) - T0 ))
  if [ "$SHOTS" = 1 ]; then adb exec-out screencap -p > "$OUT/probe.png" 2> /dev/null; fi
  if [ "$TAPS" = 1 ] && [ $((i % 2)) = 0 ]; then
    read -r PX PY PB CX CY DENS <<< "$(island_geometry)"
    if [ -n "$PX" ]; then
      if [ "$A11Y" = 1 ]; then adb shell input tap "$PX" "$PY"; else adb shell input tap "$PX" "$(awk "BEGIN{printf \"%d\", $PB - 3 * $DENS}")"; fi
    fi
  fi
  if alive; then
    echo "t=+${now}s the emulator answers ($(tail -1 "$OUT/vitals.log"))"
  else
    echo "t=+${now}s THE EMULATOR DOES NOT ANSWER"
    lost_report
    echo "PROBE RESULT $SCEN: the emulator went away at about +${now}s"
    exit 1
  fi
done
echo "ToDoIsland log of the run:"
adb logcat -d -v time -s ToDoIsland:I | cut -c1-260 | tail -30
echo "PROBE RESULT $SCEN: the emulator is still there after 2 minutes"
exit 0
