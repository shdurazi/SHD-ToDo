#!/usr/bin/env bash
# Helpers shared by smoke.sh and probe.sh. Sourced, not run: API, PKG and OUT have to be set before.

# Every adb call gets a time limit: when the emulator dies, a hung call must not hold the whole job until the 45 minute limit.
ADB_BIN=$(command -v adb)
adb() { timeout "${ADB_TIMEOUT:-90}" "$ADB_BIN" "$@"; }

# A line every 5 seconds about the host (load, free memory, is the emulator process still there) in smoke-out/vitals.log,
# so that a vanished emulator can be placed in time and blamed on something.
vitals_loop() {
  while true; do
    echo "$(date +%T) load=$(cut -d' ' -f1-3 /proc/loadavg) free_mb=$(awk '/MemAvailable/{printf "%d", $2/1024}' /proc/meminfo)" \
      "emulator_procs=$(pgrep -fc 'qemu-system|emulator.*-avd' 2>/dev/null)"
    sleep 5
  done
}
vitals_loop >> "$OUT/vitals.log" 2>&1 &
VITALS_PID=$!
LOGCAT_PID=""
trap 'kill "$VITALS_PID" $LOGCAT_PID 2> /dev/null' EXIT

# Everything the phone logs, kept on the host: when the emulator goes away, this is what it said last.
start_logcat_stream() {
  "$ADB_BIN" logcat -b all -v threadtime > "$OUT/logcat-stream.txt" 2>&1 &
  LOGCAT_PID=$!
}

alive() { [ "$(timeout 25 "$ADB_BIN" shell echo up 2> /dev/null | tr -d '\r')" = up ]; }

lost_report() { # what is known about a vanished emulator
  echo "--- host vitals, the last 24 samples"
  tail -24 "$OUT/vitals.log"
  echo "--- host memory and disk"
  free -m | head -3
  df -h / 2> /dev/null | head -3
  echo "--- emulator processes"
  ps -eo pid,stat,etime,pcpu,rss,args --sort=-rss | grep -a -E "qemu|emulator" | grep -a -v grep | cut -c1-200 | head -5
  echo "--- kernel messages of the host about killed or crashed processes"
  sudo -n dmesg 2> /dev/null | grep -a -i -E "oom|killed process|segfault|qemu|emulator" | tail -10
  echo "--- the emulator's own output: its errors, then the last 40 lines"
  if [ -f /tmp/emulator.log ]; then
    grep -a -i -E "error|fatal|panic|abort|assert|crash|killed|segfault|hang|watchdog" /tmp/emulator.log | tail -25 | cut -c1-240
    echo "..."
    tail -40 /tmp/emulator.log | cut -c1-240
  else
    echo "(no /tmp/emulator.log)"
  fi
  echo "--- crash dumps of the emulator"
  find /tmp/android-runner -type f -newer "$OUT/vitals.log" 2> /dev/null | head -8
  find /tmp/android-runner -name '*.dmp' 2> /dev/null | head -5
  echo "--- what the phone logged before it went away: crash and restart lines"
  grep -a -E "Fatal signal|F DEBUG|F libc|FATAL EXCEPTION|has died|Watchdog|WATCHDOG|ANR in|init: .*(critical|eboot|killed|exited|Sending signal)|SurfaceFlinger.*(error|abort|crash)|am_crash|am_proc_died" \
    "$OUT/logcat-stream.txt" 2> /dev/null | tail -40 | cut -c1-260
  echo "--- the last 40 lines it logged"
  tail -40 "$OUT/logcat-stream.txt" 2> /dev/null | cut -c1-260
  echo "--- adb"
  timeout 20 "$ADB_BIN" devices -l 2>&1 | head -4
  echo "--- adb restart"
  timeout 20 "$ADB_BIN" kill-server > /dev/null 2>&1
  sleep 2
  timeout 20 "$ADB_BIN" start-server > /dev/null 2>&1
  timeout 60 "$ADB_BIN" wait-for-device 2>&1 | head -2
  timeout 20 "$ADB_BIN" devices -l 2>&1 | head -4
}

guard() { # guard <where>: stops the run, with a diagnosis, when the emulator has stopped answering
  alive && return 0
  echo "EMULATOR NOT ANSWERING before: $1"
  lost_report
  alive && { echo "(the emulator answers again, carrying on)"; return 0; }
  echo "RESULT: the emulator is gone, the run stops here"
  exit 1
}

stage() { # stage <heading>: a heading with the load of the host and of the emulator
  guard "$1"
  echo "--- $1  [host load $(cut -d' ' -f1 /proc/loadavg), $(awk '/MemAvailable/{printf "%d", $2/1024}' /proc/meminfo) MB free;" \
    "emulator $(adb shell cat /proc/meminfo 2> /dev/null | awk '/MemAvailable/{printf "%d", $2/1024}') MB free]"
}

wait_boot() {
  echo "waiting for the system services to be ready ..."
  local i
  for i in $(seq 1 120); do
    if adb shell pm path android 2> /dev/null | grep -q "package:"; then echo "ready after $i checks"; break; fi
    sleep 3
  done
  sleep 5
  echo "Emulator: Android $(adb shell getprop ro.build.version.release | tr -d '\r') (API $API), $(adb shell wm size | tr -d '\r' | tail -1)"
  adb shell settings put system screen_off_timeout 1800000 > /dev/null 2>&1 || true
  adb shell svc power stayon true > /dev/null 2>&1 || true
  echo "Build: $(adb shell getprop ro.build.fingerprint | tr -d '\r')"
  start_logcat_stream
}

island_geometry() { adb logcat -d -s ToDoIsland:I | python3 scripts/island_xy.py; }

CUT=com.android.internal.display.cutout.emulation

cutout_set() { # cutout_set <name|none>: shows one of the emulator's camera cutouts (or none) and prints what the display reports
  local o
  for o in corner double hole tall emu01 waterfall; do adb shell cmd overlay disable "$CUT.$o" > /dev/null 2>&1; done
  if [ "$1" != none ]; then adb shell cmd overlay enable "$CUT.$1" 2>&1 | head -1; fi
  sleep 4
  echo "cutout '$1': $(adb shell dumpsys display | grep -a -m1 -o 'DisplayCutout{.*' | cut -c1-420)"
}
