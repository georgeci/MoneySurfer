#!/usr/bin/env bash
# Create the dedicated Maestro AVD if it doesn't exist yet.
#
# AOSP ATD image (no Gboard, no Play Services UI) — matches the image used
# by `reactivecircus/android-emulator-runner` in nightly CI, so locally and
# on CI Maestro hits the same Android surface.
#
# `hw.keyboard=yes` is the important bit: it routes Maestro's `inputText`
# through hardware key events instead of opening Gboard, which on Pixel
# images can drift into the keyboard's own theme picker mid-flow.
set -euo pipefail

AVD_NAME="${MAESTRO_AVD_NAME:-MoneySurferMaestro}"
API_LEVEL="${MAESTRO_AVD_API:-34}"
DEVICE="${MAESTRO_AVD_DEVICE:-pixel_6}"

case "$(uname -m)" in
    arm64|aarch64) ARCH="arm64-v8a" ;;
    x86_64|amd64)  ARCH="x86_64"   ;;
    *) echo "Unsupported arch: $(uname -m)" >&2; exit 1 ;;
esac
SYSTEM_IMAGE="system-images;android-${API_LEVEL};aosp_atd;${ARCH}"

ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
AVDMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
[[ -x "$SDKMANAGER" ]] || { echo "sdkmanager missing at $SDKMANAGER" >&2; exit 1; }
[[ -x "$AVDMANAGER" ]] || { echo "avdmanager missing at $AVDMANAGER" >&2; exit 1; }

if "$AVDMANAGER" list avd 2>/dev/null | grep -q "Name: ${AVD_NAME}\$"; then
    echo "AVD '${AVD_NAME}' already exists — skipping creation." >&2
else
    # `yes |` always exits with SIGPIPE once the consumer closes stdin; that
    # would otherwise trip pipefail. Disable pipefail just for these calls.
    set +o pipefail
    echo "Accepting SDK licenses (no-op if already accepted)" >&2
    yes | "$SDKMANAGER" --licenses >/dev/null
    echo "Installing system image: ${SYSTEM_IMAGE}" >&2
    yes | "$SDKMANAGER" --install "$SYSTEM_IMAGE" >/dev/null
    set -o pipefail

    echo "Creating AVD '${AVD_NAME}' (${DEVICE}, API ${API_LEVEL}, ${ARCH})" >&2
    echo "no" | "$AVDMANAGER" create avd \
        --force \
        --name "$AVD_NAME" \
        --package "$SYSTEM_IMAGE" \
        --device "$DEVICE"
fi

CONFIG="$HOME/.android/avd/${AVD_NAME}.avd/config.ini"
[[ -f "$CONFIG" ]] || { echo "config.ini not found: $CONFIG" >&2; exit 1; }

# Idempotent key=value upserts. macOS sed needs the empty -i argument.
upsert() {
    local key="$1" value="$2"
    if grep -q "^${key}=" "$CONFIG"; then
        sed -i '' "s|^${key}=.*|${key}=${value}|" "$CONFIG"
    else
        echo "${key}=${value}" >>"$CONFIG"
    fi
}

upsert hw.keyboard yes
upsert hw.ramSize 4096
upsert disk.dataPartition.size 6144M
upsert hw.gpu.enabled yes
upsert hw.gpu.mode swiftshader_indirect

echo "AVD '${AVD_NAME}' is ready. config.ini → $CONFIG" >&2
