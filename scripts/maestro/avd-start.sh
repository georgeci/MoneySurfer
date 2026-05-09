#!/usr/bin/env bash
# Boot the dedicated Maestro AVD headless and wait until it's responsive.
#
# Usage: scripts/maestro/avd-start.sh [--foreground]
#
# Foreground mode is mostly for debugging — by default the emulator runs
# detached and the script returns once `sys.boot_completed=1`.
set -euo pipefail

AVD_NAME="${MAESTRO_AVD_NAME:-MoneySurferMaestro}"
case "$(uname -s)" in
    Darwin) DEFAULT_SDK="$HOME/Library/Android/sdk" ;;
    Linux)  DEFAULT_SDK="$HOME/Android/Sdk" ;;
    *)      DEFAULT_SDK="$HOME/Android/Sdk" ;;
esac
ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$DEFAULT_SDK}}"
EMULATOR="$ANDROID_HOME/emulator/emulator"
ADB="$ANDROID_HOME/platform-tools/adb"

[[ -x "$EMULATOR" ]] || { echo "emulator binary missing at $EMULATOR (set ANDROID_HOME or ANDROID_SDK_ROOT)" >&2; exit 1; }
[[ -x "$ADB"      ]] || { echo "adb binary missing at $ADB (set ANDROID_HOME or ANDROID_SDK_ROOT)"          >&2; exit 1; }

if ! "$EMULATOR" -list-avds | grep -qx "$AVD_NAME"; then
    echo "AVD '$AVD_NAME' not found. Run scripts/maestro/avd-create.sh first." >&2
    exit 1
fi

# If a device is attached, reuse only if it's *our* AVD; otherwise refuse to
# launch a second one on top — picking the wrong target would silently break
# the run.
existing_serials=$("$ADB" devices | awk 'NR>1 && $2=="device"{print $1}')
if [[ -n "$existing_serials" ]]; then
    for s in $existing_serials; do
        running_avd=$("$ADB" -s "$s" emu avd name 2>/dev/null | head -n1 | tr -d '\r')
        if [[ "$running_avd" == "$AVD_NAME" ]]; then
            echo "AVD '$AVD_NAME' already running on $s — reusing." >&2
            exit 0
        fi
    done
    echo "Other devices/emulators are attached: $existing_serials" >&2
    echo "Refusing to launch '$AVD_NAME' alongside them — kill or unplug first." >&2
    exit 1
fi

OPTS=(
    -avd "$AVD_NAME"
    -no-window -no-audio -no-boot-anim
    -no-snapshot-save -no-snapshot-load
    -gpu swiftshader_indirect
    -camera-back none -camera-front none
)

case "${1:-}" in
    --foreground|-f)
        exec "$EMULATOR" "${OPTS[@]}"
        ;;
esac

LOG_DIR="${MAESTRO_AVD_LOG_DIR:-build/logs/maestro}"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/emulator-${AVD_NAME}.log"

echo "Booting '$AVD_NAME' headless. Logs → $LOG_FILE" >&2
nohup "$EMULATOR" "${OPTS[@]}" >"$LOG_FILE" 2>&1 &
EMU_PID=$!

cleanup_on_fail() { kill "$EMU_PID" 2>/dev/null || true; }
trap cleanup_on_fail ERR

# 1. wait (up to 120s) for adb to see the device
adb_visible=0
for _ in $(seq 1 60); do
    if "$ADB" devices | awk 'NR>1 && $2=="device"{found=1} END{exit !found}'; then
        adb_visible=1
        break
    fi
    sleep 2
done
if [[ "$adb_visible" != "1" ]]; then
    echo "Emulator never appeared on adb after 120s — check $LOG_FILE." >&2
    cleanup_on_fail
    exit 1
fi

# 2. wait (up to 240s) for full boot
booted=""
for _ in $(seq 1 120); do
    booted="$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    [[ "$booted" == "1" ]] && break
    sleep 2
done
[[ "$booted" == "1" ]] || { echo "Emulator did not boot in time — check $LOG_FILE." >&2; cleanup_on_fail; exit 1; }

trap - ERR
echo "Emulator '$AVD_NAME' is up (pid $EMU_PID)." >&2
