#!/usr/bin/env bash
# Runs before the emulator is started (the emulator runner's pre-emulator-launch-script): puts a small wrapper in place of
# the emulator launcher that writes down how the emulator process ended (its exit status, or 128 + the signal that killed it).
# The emulator itself is installed by the runner, so this cannot be done any earlier. Only the probe job uses it.
set -e
EMU="${ANDROID_HOME:-$ANDROID_SDK_ROOT}/emulator/emulator"
ls -la "$EMU"
sudo mv "$EMU" "$EMU.real"
sudo tee "$EMU" > /dev/null <<'WRAP'
#!/bin/bash
D="$(cd "$(dirname "$0")" && pwd)"
echo "emulator launcher started at $(date +%T): $*" >> /tmp/emulator-exit.log
"$D/emulator.real" "$@"
S=$?
echo "emulator launcher ended with status $S at $(date +%T)" >> /tmp/emulator-exit.log
exit $S
WRAP
sudo chmod +x "$EMU"
ls -la "$EMU" "$EMU.real"
: > /tmp/emulator-exit.log # the runner starts the emulator after this script
