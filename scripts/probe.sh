#!/usr/bin/env bash
# The emulators went away (the guest stops answering and, about a minute later, the emulator process is gone) in the
# accessibility window's part of the island test, with animations on. This script looks for the cause: one scenario per CI
# leg, the emulator is watched for two minutes and, if it vanishes, explained as far as that is possible (see common.sh).
# Used by the "probe" job in .github/workflows/android.yml (only when the commit message says [probe]).
#
# The scenario name is a list of words: a11y | overlay | none (which island window, or no app at all), anim | noanim
# (the phone's animation speed), idle (service on, but no focus session), taps, shots (a tap / a screenshot every 10 / 5 s),
# settled (wait until the guest is quiet before starting), notb (the emulator image's TalkBack is switched off first).
# The emulator is watched for four minutes ($WATCH_S): with a tap every 10 s that is about twenty openings of the card.
API="${1:-?}"
SCEN="${2:-a11y-anim}"
GPU="${3:-swiftshader_indirect}" # only for the heading: the emulator was started with it by the workflow
PKG=com.shdurazi.todo
OUT=smoke-out
mkdir -p "$OUT"
source scripts/common.sh

MODE=a11y SCALE=1 SHOTS=0 TAPS=0 ISLAND=1 SETTLE=0 NOTB=0 WATCH_S=240
case "$SCEN" in overlay*) MODE=overlay ;; none*) MODE=none ISLAND=0 ;; esac
case "$SCEN" in *noanim*) SCALE=0 ;; esac
case "$SCEN" in *idle*) ISLAND=0 ;; esac
case "$SCEN" in *shots*) SHOTS=1 ;; esac
case "$SCEN" in *taps*) TAPS=1 ;; esac
case "$SCEN" in *settled*) SETTLE=1 ;; esac
case "$SCEN" in *notb*) NOTB=1 ;; esac
echo "PROBE $SCEN on API $API (gpu $GPU): window=$MODE animations=$SCALE screenshots=$SHOTS taps=$TAPS island=$ISLAND settle=$SETTLE notalkback=$NOTB"

wait_boot
if [ "$SETTLE" = 1 ]; then settle 150; fi
echo "accessibility-related packages: $(adb shell pm list packages 2> /dev/null | grep -i -E 'accessib|talkback|marvin' | tr -d '\r' | tr '\n' ' ')"
if [ "$NOTB" = 1 ]; then
  adb shell pm disable-user --user 0 com.google.android.marvin.talkback 2>&1 | head -2
fi
if [ "$MODE" != none ]; then
  adb install -r dist/ToDo-debug.apk > /dev/null
  adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS > /dev/null 2>&1 || true
  adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
fi
cutout_set emu01
adb shell settings put global animator_duration_scale "$SCALE"
if [ "$MODE" != none ]; then
  adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1
  sleep 3
fi
if [ "$MODE" = a11y ]; then
  adb shell settings put secure enabled_accessibility_services "$PKG/com.shdurazi.todo.island.IslandAccessibilityService"
  adb shell settings put secure accessibility_enabled 1
  sleep 4
fi
adb logcat -c
if [ "$MODE" != none ]; then
  adb shell am start -n "$PKG/.MainActivity" -a com.shdurazi.todo.action.START_FOCUS --es block_id b-deep > /dev/null 2>&1
  sleep 3
fi
if [ "$ISLAND" = 1 ]; then adb shell input keyevent 3; fi
echo "accessibility state: $(adb shell dumpsys accessibility 2> /dev/null | grep -a -i -E 'Enabled services|Bound services|Binding services' | tr -d '\r' | head -4 | tr '\n' ' ')"
echo "set up, watching for $((WATCH_S / 60)) minutes"

T0=$(date +%s)
for i in $(seq 1 $((WATCH_S / 5))); do
  sleep 5
  now=$(( $(date +%s) - T0 ))
  if alive; then
    ADB_TIMEOUT=20 guest_stats > "$OUT/guest-last.txt"
    { echo "== +${now}s"; cat "$OUT/guest-last.txt"; } >> "$OUT/guest.log"
    echo "t=+${now}s the emulator answers ($(tail -1 "$OUT/vitals.log" | cut -d' ' -f1-6); guest: $(sed -n 1p "$OUT/guest-last.txt" | cut -c1-40), $(sed -n 4p "$OUT/guest-last.txt" | cut -c1-60))"
  else
    echo "t=+${now}s THE EMULATOR DOES NOT ANSWER"
    lost_report
    echo "PROBE RESULT $SCEN/$GPU/API$API: the emulator went away at about +${now}s"
    exit 1
  fi
  if [ "$SHOTS" = 1 ]; then ADB_TIMEOUT=20 adb exec-out screencap -p > "$OUT/probe.png" 2> /dev/null; fi
  if [ "$TAPS" = 1 ] && [ $((i % 2)) = 0 ]; then
    read -r PX PY PB CX CY DENS <<< "$(ADB_TIMEOUT=20 island_geometry)"
    if [ -n "$PX" ]; then
      if [ "$MODE" = a11y ]; then
        ADB_TIMEOUT=20 adb shell input tap "$PX" "$PY"
      else
        ADB_TIMEOUT=20 adb shell input tap "$PX" "$(awk "BEGIN{printf \"%d\", $PB - 3 * $DENS}")"
      fi
    fi
  fi
done
echo "ToDoIsland log of the run:"
adb logcat -d -v time -s ToDoIsland:I | cut -c1-260 | tail -30
echo "processes that died, and TalkBack, in the phone's log:"
adb logcat -d -b all -v time 2> /dev/null | grep -a -E "am_proc_died|am_crash|am_anr|has died|WATCHDOG|marvin.talkback" | cut -c1-200 | tail -25
echo "the guest's numbers at the end:"
cat "$OUT/guest-last.txt" 2> /dev/null
echo "PROBE RESULT $SCEN/$GPU/API$API: the emulator is still there after $((WATCH_S / 60)) minutes"
exit 0
