#!/usr/bin/env bash
# Helpers shared by smoke.sh and probe.sh. Sourced, not run: API, PKG and OUT have to be set before.

# Every adb call gets a time limit: when the emulator dies, a hung call must not hold the whole job until the 45 minute limit.
ADB_BIN=$(command -v adb)
adb() { timeout "${ADB_TIMEOUT:-90}" "$ADB_BIN" "$@"; }

# A line every 5 seconds about the host (load, free memory, is the emulator process still there) in smoke-out/vitals.log,
# so that a vanished emulator can be placed in time and blamed on something.
qemu_pid() { pgrep -f 'qemu-system-x86_64' | head -1; }

vitals_loop() {
  local P
  while true; do
    P=$(qemu_pid)
    echo "$(date +%T) load=$(cut -d' ' -f1-3 /proc/loadavg) free_mb=$(awk '/MemAvailable/{printf "%d", $2/1024}' /proc/meminfo)" \
      "emulator_procs=$(pgrep -fc 'qemu-system|emulator.*-avd' 2>/dev/null)" \
      "emu_log_lines=$(wc -l < /tmp/emulator.log 2>/dev/null || echo 0)" \
      "qemu_cpu=$([ -n "$P" ] && ps -o cputime= -p "$P" | tr -d ' ')"
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
  echo "--- the emulator process and its busiest threads over 2 s"
  local P
  P=$(qemu_pid)
  if [ -n "$P" ]; then
    ps -o pid,stat,etime,cputime,pcpu,rss,nlwp -p "$P"
    top -H -b -n 2 -d 2 -p "$P" 2> /dev/null | awk '/^top -/{n++} n==2' | sed -n '7,22p' | cut -c1-110
  else
    echo "(no emulator process any more)"
  fi
  echo "--- the emulator console"
  timeout 15 "$ADB_BIN" emu avd status 2>&1 | head -3
  echo "--- how the emulator launcher ended"
  cat /tmp/emulator-exit.log 2> /dev/null || echo "(no /tmp/emulator-exit.log)"
  echo "--- kernel messages of the host about killed or crashed processes"
  sudo -n dmesg 2> /dev/null | grep -a -i -E "oom|killed process|segfault|qemu|emulator" | tail -10
  echo "--- the host kernel's last 12 lines, whatever they are"
  sudo -n dmesg 2>&1 | tail -12 | cut -c1-200
  echo "--- the emulator's own output: its errors, then the last 40 lines"
  if [ -f /tmp/emulator.log ]; then
    grep -a -i -E "error|fatal|panic|abort|assert|crash|killed|segfault|\bhang|\bhung|watchdog|soft lockup|rcu.*stall|\bOops\b|BUG:" /tmp/emulator.log \
      | grep -a -v -E "ColorBuffer|loop[0-9]+: detected|error_report_end|map_offload_tether_error_map|keystore2: .*error" | tail -25 | cut -c1-240
    echo "... the last 40 lines without the graphics buffer noise"
    grep -a -v -E "ColorBuffer|RenderThread|loop[0-9]+: detected" /tmp/emulator.log | tail -40 | cut -c1-240
    echo "... graphics lines at the very end"
    tail -6 /tmp/emulator.log | cut -c1-200
  else
    echo "(no /tmp/emulator.log)"
  fi
  echo "--- crash dumps of the emulator"
  find /tmp/android-runner -type f -newer "$OUT/vitals.log" 2> /dev/null | head -8
  find /tmp/android-runner -name '*.dmp' 2> /dev/null | head -5
  echo "--- what the phone logged before it went away: crash and restart lines"
  grep -a -E "Fatal signal|F DEBUG|F libc|FATAL EXCEPTION|has died|Watchdog|WATCHDOG|ANR in|init: .*(critical|eboot|killed|exited|Sending signal)|SurfaceFlinger.*(error|abort|crash)|am_crash|am_proc_died" \
    "$OUT/logcat-stream.txt" 2> /dev/null | tail -40 | cut -c1-260
  echo "--- the guest's own numbers, the last samples before it went quiet"
  tail -40 "$OUT/guest.log" 2> /dev/null
  echo "--- what the island logged"
  grep -a "ToDoIsland" "$OUT/logcat-stream.txt" 2> /dev/null | tail -14 | cut -c1-260
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

guest_cpu_busy() { # the guest's CPU use over about 2 s, in percent (from /proc/stat)
  local a b
  a=$(adb shell head -1 /proc/stat 2> /dev/null | tr -d '\r')
  sleep 2
  b=$(adb shell head -1 /proc/stat 2> /dev/null | tr -d '\r')
  awk -v a="$a" -v b="$b" 'BEGIN {
    split(a, x, " "); split(b, y, " "); ta = 0; tb = 0
    for (i = 2; i <= 9; i++) { ta += x[i]; tb += y[i] }
    idle = (y[5] + y[6]) - (x[5] + x[6]); tot = tb - ta
    if (tot <= 0) print 100; else printf "%d", 100 * (1 - idle / tot)
  }'
}

settle() { # settle [max seconds]: waits until the guest has been quiet for two readings in a row (the boot is not over when sys.boot_completed says so)
  local limit=${1:-150} t0 busy calm=0 n=0
  t0=$(date +%s)
  while [ $(( $(date +%s) - t0 )) -lt "$limit" ]; do
    busy=$(guest_cpu_busy)
    n=$((n + 1))
    [ $((n % 5)) = 1 ] && echo "settling: the guest is $busy% busy (host load $(cut -d' ' -f1 /proc/loadavg))"
    if [ "${busy:-100}" -lt 25 ]; then calm=$((calm + 1)); else calm=0; fi
    [ "$calm" -ge 2 ] && { echo "settled after $(( $(date +%s) - t0 )) s (guest $busy% busy)"; return 0; }
  done
  echo "not settled after $limit s (guest $busy% busy), going on"
}

guest_stats() { # one line-block about the guest: memory, pressure, load, the busiest processes (for the probe)
  adb shell 'echo "loadavg $(cat /proc/loadavg)"; grep -E "MemAvailable|SwapFree" /proc/meminfo | tr "\n" " "; echo; for f in cpu memory io; do echo "psi-$f $(head -1 /proc/pressure/$f 2>/dev/null)"; done; top -b -n 1 -m 6 2>/dev/null | tail -n +5 | cut -c1-110' 2> /dev/null
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
