#!/usr/bin/env bash
# Starts the app on a running emulator (debug and release APK), clicks through a few things, runs a short monkey
# test and records everything in the job log (crash reports, UI dumps and screenshots as base64 JPEGs).
# Used by the "smoke" job in .github/workflows/android.yml.
API="${1:-?}"
PKG=com.shdurazi.todo
OUT=smoke-out
mkdir -p "$OUT"

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
trap 'kill "$VITALS_PID" 2> /dev/null' EXIT

alive() { [ "$(timeout 25 "$ADB_BIN" shell echo up 2> /dev/null | tr -d '\r')" = up ]; }

lost_report() { # what is known about a vanished emulator
  echo "--- host vitals, the last 24 samples"
  tail -24 "$OUT/vitals.log"
  echo "--- host memory and disk"
  free -m | head -3
  df -h / /tmp 2> /dev/null | head -4
  echo "--- emulator processes"
  ps -eo pid,stat,etime,pcpu,rss,args --sort=-rss | grep -a -E "qemu|emulator" | grep -a -v grep | cut -c1-200 | head -5
  echo "--- kernel messages about killed or crashed processes"
  sudo -n dmesg 2> /dev/null | grep -a -i -E "oom|killed process|segfault|qemu|emulator|kvm" | tail -15
  echo "--- crash dumps of the emulator"
  ls -la /tmp/android-runner 2> /dev/null | head -8
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

echo "waiting for the system services to be ready ..."
for i in $(seq 1 120); do
  if adb shell pm path android 2>/dev/null | grep -q "package:"; then echo "ready after $i checks"; break; fi
  sleep 3
done
sleep 5
echo "Emulator: Android $(adb shell getprop ro.build.version.release | tr -d '\r') (API $API), $(adb shell wm size | tr -d '\r' | tail -1)"
adb shell settings put system screen_off_timeout 1800000 > /dev/null 2>&1 || true
adb shell svc power stayon true > /dev/null 2>&1 || true
echo "Build: $(adb shell getprop ro.build.fingerprint | tr -d '\r')"
echo "--- feature flags about Live Updates in this system image"
adb shell aflags list 2>&1 | grep -i -E "rich_ongoing|promoted|status_bar_chips|live_update" | cut -c1-200 | head -20 || true

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

TAPPED=0
tap_text() { # tap_text <text or description>; sets TAPPED to 1 when a node was found and tapped
  dump
  XY=$(python3 scripts/ui_find.py "$1" < "$OUT/ui.xml")
  TAPPED=0
  if [ -n "$XY" ]; then echo "tap '$1' at $XY"; TAPPED=1; adb shell input tap $XY; else echo "no node containing '$1'"; fi
  sleep 1.5
}

crash_report() { # crash_report <logcat file>
  grep -E "AndroidRuntime" "$1" | grep -E "FATAL EXCEPTION|Process: $PKG|Caused by|Exception|Error:|at com\.shdurazi" | cut -c1-260 | head -70
  grep -nE "ANR in $PKG|Fatal signal" -A6 "$1" | head -30
}


top_shot() { # top_shot <png> <name>: the top of a screenshot (where the island is) as a small JPEG in the log
  local F=$1 W
  W=$(identify -format %w "$1" 2>/dev/null || echo 1080)
  if command -v convert > /dev/null; then
    convert "$1" -crop "${W}x560+0+0" +repage -resize 720x -quality 82 "${1%.png}.jpg" && F="${1%.png}.jpg"
  fi
  echo "SHOT-BEGIN $2"; base64 -w0 "$F"; echo; echo "SHOT-END $2"
}

sheet() { # sheet <mp4> <jpg> <start s> <length s> <fps> <label>: a contact sheet (4 x 4 frames) of the top of the screen
  # the film is 720 x 1600: its top 7/30 (a 1080 x 2400 screen's top 560 px) is where the island is
  nice -n 10 ffmpeg -loglevel error -threads 2 -y -ss "$3" -t "$4" -i "$1" \
    -vf "fps=$5,crop=iw:trunc(ih*7/60)*2:0:0,scale=448:-2,tile=4x4" -frames:v 1 -q:v 4 "$2" || return
  [ -s "$2" ] || return
  echo "SHOT-BEGIN $6"; base64 -w0 "$2"; echo; echo "SHOT-END $6"
}

film() { # film <name> <label> <seconds> <command...>: records the screen around the command, prints contact sheets
  local name=$1 label=$2 secs=$3 rp st i=1
  shift 3
  if ! command -v ffmpeg > /dev/null; then "$@"; sleep $((secs - 1)); return; fi
  adb shell rm -f /sdcard/film.mp4 > /dev/null 2>&1
  # a small picture and a modest bit rate: the emulator encodes in software
  adb shell screenrecord --size 720x1600 --bit-rate 6000000 --time-limit "$secs" /sdcard/film.mp4 > /dev/null 2>&1 &
  rp=$!
  sleep 1.5
  "$@"
  wait $rp
  sleep 1
  if adb pull /sdcard/film.mp4 "$OUT/$name.mp4" > /dev/null 2>&1; then
    echo "recording $name: $(stat -c %s "$OUT/$name.mp4") bytes, $(ffprobe -v error -show_entries format=duration -of csv=p=0 "$OUT/$name.mp4" 2>/dev/null) s"
    for st in 1.6 4.8 8.0 11.2 14.4 17.6 20.8; do
      awk "BEGIN{exit !($st < $secs)}" || break
      sheet "$OUT/$name.mp4" "$OUT/$name-$i.jpg" "$st" 3.2 5 "$label-$i"
      i=$((i + 1))
    done
  else
    echo "no recording for $name"
  fi
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

island_preview() { # island_preview <test name>: taps "Preview" in Settings > Island and looks at the island over the app itself
  local t=$1 XY="" i
  # start the app afresh: it opens on its home screen, whatever the earlier steps left on the screen (the keyboard, the focus screen)
  adb shell am force-stop "$PKG"
  adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1
  sleep 3
  tap_text "Settings"
  for i in 1 2 3 4 5 6 7 8; do
    dump
    XY=$(python3 scripts/ui_find.py "Show the island" < "$OUT/ui.xml")
    [ -n "$XY" ] && break
    adb shell input swipe 540 1900 540 800 250
    sleep 0.8
  done
  if [ -z "$XY" ]; then
    echo "no Preview row found in Settings; the screen shows:"
    python3 scripts/ui_dump.py < "$OUT/ui.xml" | head -30
    shot "$t-settings-no-preview"
    return
  fi
  echo "tap Preview at $XY"
  shot "$t-settings-island"
  adb shell input tap $XY
  sleep 2.4
  adb exec-out screencap -p > "$OUT/$t-preview-a.png"
  top_shot "$OUT/$t-preview-a.png" "$t-island-preview-a"
  sleep 1.6
  adb exec-out screencap -p > "$OUT/$t-preview-b.png"
  top_shot "$OUT/$t-preview-b.png" "$t-island-preview-b"
  sleep 8
  adb exec-out screencap -p > "$OUT/$t-preview-c.png"
  top_shot "$OUT/$t-preview-c.png" "$t-island-preview-c-after-the-end"
  adb shell input keyevent 4
  sleep 1
}

SLOW=5 # how much slower the slow-motion films run every animation

# The island introduces itself once per focus session, so every look at the intro needs a new session: start one on the
# other one of two blocks (starting the block that is already running changes nothing).
SESSION_BLOCK=b-wrap
fresh_session() {
  if [ "$SESSION_BLOCK" = b-deep ]; then SESSION_BLOCK=b-wrap; else SESSION_BLOCK=b-deep; fi
  echo "new focus session on $SESSION_BLOCK"
  adb shell am start -n "$PKG/.MainActivity" -a com.shdurazi.todo.action.START_FOCUS --es block_id "$SESSION_BLOCK" > /dev/null 2>&1
}

island_slowmo() { # island_slowmo <test name> <label>: a new session, and its intro with every animation $SLOW times slower
  local t=$1 name=$2
  adb shell settings put global animator_duration_scale "$SLOW"
  fresh_session
  sleep 3
  film "$t-$name" "$t-island-slow-$name" 20 adb shell input keyevent 3
  adb shell settings put global animator_duration_scale 1
  adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1
  sleep 2
}

home_and_look() { # home_and_look <file name> <label>: Home, then two screenshots of the top of the screen while the island opens
  adb shell input keyevent 3
  sleep 2.2
  adb exec-out screencap -p > "$OUT/$1-a.png"
  top_shot "$OUT/$1-a.png" "$2-a"
  sleep 1.2
  adb exec-out screencap -p > "$OUT/$1-b.png"
  top_shot "$OUT/$1-b.png" "$2-b"
  sleep 5.5
}

island_test() { # island_test <variant> <cutout> <lock screen: yes|no> <motion: yes|no>
  local v=$1 cfg=$2 full=$3 motion=$4 t="$1-$2" PX PY PB CX CY DENS
  echo "::group::[$v] island, cutout '$cfg' (API $API)"
  guard "island test $t"
  # the emulator action switches all animations off; the island's own animations need to run
  adb shell settings put global animator_duration_scale 1
  cutout_set "$cfg"
  adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
  echo "overlay permission: $(adb shell appops get "$PKG" SYSTEM_ALERT_WINDOW | tr -d '\r')"
  adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1
  sleep 2
  adb logcat -c
  stage "1. a session started inside the app: the island must stay away while the app is in front"
  fresh_session
  sleep 3
  adb exec-out screencap -p > "$OUT/$t-island-app.png"
  top_shot "$OUT/$t-island-app.png" "$t-island-1-app-in-front"
  if [ "$motion" = yes ]; then
    stage "1b. the Preview row in Settings: the island over the app itself"
    island_preview "$t"
    adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1
    sleep 2
  fi
  stage "2. Home: the island should pop in, open, turn the dial and close again (plain overlay window)"
  home_and_look "$t-ov" "$t-island-2-overlay"
  adb exec-out screencap -p > "$OUT/$t-ov-closed.png"
  top_shot "$OUT/$t-ov-closed.png" "$t-island-3-overlay-closed"
  echo "--- ToDoIsland log (overlay)"
  adb logcat -d -v time -s ToDoIsland:I | cut -c1-300 | tail -40
  read -r PX PY PB CX CY DENS <<< "$(island_geometry)"
  echo "geometry: pill centre $PX,$PY bottom $PB card tap point $CX,$CY density $DENS"
  if [ -n "$PX" ]; then
    stage "3. taps on the closed pill: its middle (under the status bar) and the strip below the bar"
    adb logcat -c
    adb shell input tap "$PX" "$PY"
    sleep 1
    echo "taps on the pill after the middle tap: $(adb logcat -d -s ToDoIsland:I | grep -c 'tap on the pill')"
    adb shell input tap "$PX" "$(awk "BEGIN{printf \"%d\", $PB - 3 * $DENS}")"
    sleep 1.6
    echo "taps on the pill after the strip tap: $(adb logcat -d -s ToDoIsland:I | grep -c 'tap on the pill')"
    adb exec-out screencap -p > "$OUT/$t-ov-tap.png"
    top_shot "$OUT/$t-ov-tap.png" "$t-island-4-overlay-after-tap"
    stage "4. tap on the open card: the app should come to the front"
    adb shell input tap "$CX" "$CY"
    sleep 2.5
    echo "resumed: $(adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity' | cut -c1-200)"
    adb logcat -d -s ToDoIsland:I | grep -E "tap on the card|fade out|detached" | tail -4
  fi

  if [ "$motion" = yes ]; then
    stage "4b. slow motion ($SLOW times), plain overlay window"
    island_slowmo "$t" ov-slow
  fi

  stage "5. the accessibility window (above the status bar, on the lock screen)"
  adb shell settings put secure enabled_accessibility_services "$PKG/com.shdurazi.todo.island.IslandAccessibilityService"
  adb shell settings put secure accessibility_enabled 1
  sleep 4
  adb logcat -d -s ToDoIsland:I | grep -i "accessibility" | tail -3
  fresh_session
  sleep 3
  adb logcat -c
  home_and_look "$t-a11y" "$t-island-5-a11y"
  adb exec-out screencap -p > "$OUT/$t-a11y-closed.png"
  top_shot "$OUT/$t-a11y-closed.png" "$t-island-6-a11y-closed"
  echo "--- ToDoIsland log (accessibility)"
  adb logcat -d -v time -s ToDoIsland:I | cut -c1-300 | tail -30
  read -r PX PY PB CX CY DENS <<< "$(island_geometry)"
  if [ -n "$PX" ]; then
    adb logcat -c
    adb shell input tap "$PX" "$PY"
    sleep 1.6
    echo "taps on the pill after the middle tap: $(adb logcat -d -s ToDoIsland:I | grep -c 'tap on the pill')"
    adb exec-out screencap -p > "$OUT/$t-a11y-tap.png"
    top_shot "$OUT/$t-a11y-tap.png" "$t-island-7-a11y-after-tap"
    adb shell input tap "$CX" "$CY"
    sleep 2.5
    echo "resumed: $(adb shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity' | cut -c1-200)"
    adb logcat -d -s ToDoIsland:I | grep -E "tap on the card|fade out|detached" | tail -4
  fi

  if [ "$motion" = yes ]; then
    stage "5b. slow motion ($SLOW times), accessibility window"
    island_slowmo "$t" a11y-slow
  fi

  if [ "$full" = yes ]; then
    stage "6. lock screen (with a PIN, so that the keyguard is a real one)"
    adb shell input keyevent 3
    sleep 4
    adb shell locksettings set-pin 1234 2>&1 | head -2
    adb shell input keyevent 26
    sleep 2
    adb shell input keyevent 26
    sleep 3
    adb exec-out screencap -p > "$OUT/$t-a11y-lock.png"
    top_shot "$OUT/$t-a11y-lock.png" "$t-island-8-a11y-lock-screen"
    adb shell input keyevent 82
    sleep 1
    adb shell input text 1234
    adb shell input keyevent 66
    sleep 2
    adb shell locksettings clear --old 1234 2>&1 | head -2
    adb shell wm dismiss-keyguard > /dev/null 2>&1
    sleep 1
  fi

  adb shell settings put secure enabled_accessibility_services "" > /dev/null 2>&1
  adb shell settings put secure accessibility_enabled 0 > /dev/null 2>&1
  adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW default > /dev/null 2>&1
  adb shell settings put global animator_duration_scale 0
  adb shell am force-stop "$PKG"
  adb logcat -d > "$OUT/logcat-island-$t.txt"
  crash_report "$OUT/logcat-island-$t.txt"
  echo "::endgroup::"
}

for variant in debug release; do
  APK="dist/ToDo-$variant.apk"
  guard "the $variant build"
  echo "::group::[$variant] install and start (API $API)"
  adb uninstall "$PKG" > /dev/null 2>&1 || true
  adb install -r "$APK" || { echo "RESULT[$variant]: install failed"; echo "::endgroup::"; continue; }
  adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS > /dev/null 2>&1 || true
  adb shell dumpsys package "$PKG" | grep -i -E "POST_PROMOTED|POST_NOTIFICATIONS" | head -4
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

    echo "::group::[$variant] focus session and Live Update (API $API)"
    adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1
    sleep 2
    adb logcat -c
    dump
    XY=""
    for name in "Deep work" "Lunch" "Wrap up"; do
      XY=$(python3 scripts/ui_find.py "$name" < "$OUT/ui.xml")
      if [ -n "$XY" ]; then echo "tap block '$name' at $XY"; break; fi
    done
    if [ -n "$XY" ]; then
      adb shell input tap $XY
    else
      echo "no block visible: starting the session through the intent instead"
      adb shell am start -n "$PKG/.MainActivity" -a com.shdurazi.todo.action.START_FOCUS --es block_id b-lunch > /dev/null 2>&1
    fi
    sleep 4
    shot "$variant-7-focus"
    dump; echo "--- UI nodes (focus screen)"; python3 scripts/ui_dump.py < "$OUT/ui.xml" | head -30
    echo "--- notification record of the focus session"
    adb shell dumpsys notification --noredact > "$OUT/notif-$variant.txt" 2>&1
    grep -A70 "NotificationRecord.*pkg=$PKG.*id=103" "$OUT/notif-$variant.txt" | cut -c1-260 | head -80
    echo "--- promoted-related lines of the notification dump"
    grep -i -n "promot" "$OUT/notif-$variant.txt" | cut -c1-200 | head -15
    echo "--- ToDoLive log"
    adb logcat -d -s ToDoLive:I | tail -5
    echo "--- ToDoProbe log"
    adb logcat -d -s ToDoProbe:I | cut -c1-200 | tail -20
    adb shell input keyevent 3
    sleep 1
    adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME > /dev/null 2>&1
    sleep 2
    shot "$variant-8-home-with-focus"
    adb shell cmd statusbar expand-notifications
    sleep 3
    shot "$variant-9-shade"
    dump; echo "--- UI nodes (shade)"; python3 scripts/ui_dump.py < "$OUT/ui.xml" | grep -i -E "todo|left|until|stop|focus|deep|lunch|wrap" | head -20
    adb shell cmd statusbar collapse
    echo "waiting 75 s to see the notification being redrawn ..."
    sleep 75
    echo "--- ToDoLive log (after a minute)"
    adb logcat -d -s ToDoLive:I | tail -5
    adb shell input keyevent 224
    adb shell cmd statusbar expand-notifications
    sleep 3
    dump
    if [ -z "$(python3 scripts/ui_find.py "Stop" < "$OUT/ui.xml")" ]; then
      adb shell cmd statusbar expand-notifications
      sleep 3
    fi
    shot "$variant-10-shade-later"
    tap_text "Stop"
    sleep 2
    if [ "$TAPPED" != 1 ]; then
      echo "RESULT[$variant]: no Stop button in the shade, nothing was tapped (a test problem, not an app problem)"
    elif adb shell dumpsys notification --noredact | grep -q "NotificationRecord.*pkg=$PKG.*id=103"; then
      echo "RESULT[$variant]: focus notification still there after Stop"
    else
      echo "RESULT[$variant]: focus notification gone after Stop"
    fi
    adb shell cmd statusbar collapse
    adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1
    sleep 2
    tap_text "Back"
    adb shell input keyevent 4
    sleep 1.5
    dump
    if [ -z "$(python3 scripts/ui_find.py "Settings" < "$OUT/ui.xml")" ]; then
      echo "Settings not visible: reopening the app"
      adb shell am start -n "$PKG/.MainActivity" > /dev/null 2>&1
      sleep 2
    fi
    tap_text "Settings"
    sleep 1.5
    shot "$variant-11-settings"
    dump; echo "--- UI nodes (settings)"; python3 scripts/ui_dump.py < "$OUT/ui.xml" | grep -i -E "notif|live|alarm|exact|settings|summary" | head -20
    adb shell input keyevent 4
    sleep 1
    adb logcat -d > "$OUT/logcat-focus-$variant.txt"
    crash_report "$OUT/logcat-focus-$variant.txt"
    PID3=$(adb shell pidof "$PKG" | tr -d '\r')
    if [ -n "$PID3" ]; then echo "RESULT[$variant]: still running after the focus session"; else echo "RESULT[$variant]: DIED during the focus session"; fi
    echo "::endgroup::"

    guard "the island tests of the $variant build"
    if [ "$variant" = debug ]; then
      # a camera hole like a real phone's: the motion (slow-motion films, the Preview row) is looked at here, first
      island_test "$variant" emu01 no yes
      # an imaginary hole at the top centre, and the lock screen
      island_test "$variant" hole yes no
      island_test "$variant" tall no no
    else
      island_test "$variant" emu01 no no
      island_test "$variant" hole yes no
    fi
    cutout_set none

    guard "the monkey test of the $variant build"
    echo "::group::[$variant] monkey test (API $API)"
    adb logcat -c
    ADB_TIMEOUT=400 adb shell monkey -p "$PKG" -s 7 --throttle 150 --pct-syskeys 0 --pct-appswitch 0 --pct-nav 0 --pct-majornav 0 -v 300 > "$OUT/monkey-$variant.txt" 2>&1 || true
    tail -8 "$OUT/monkey-$variant.txt"
    adb logcat -d > "$OUT/logcat-monkey-$variant.txt"
    crash_report "$OUT/logcat-monkey-$variant.txt"
    shot "$variant-6-after-monkey"
    echo "::endgroup::"
  fi
done
exit 0
