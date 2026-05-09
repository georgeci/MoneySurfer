#!/usr/bin/env bash
# Build-time guard for iosAppOffline.
#
# The offline build must never ship with networking entitlements or Info.plist
# keys that imply network access. This script greps the resolved Info.plist and
# any entitlements file Xcode produced for the current configuration and fails
# the build on hit. It is invoked from a Run Script build phase on the
# iosAppOffline target, so it has full access to BUILT_PRODUCTS_DIR /
# CODE_SIGN_ENTITLEMENTS / INFOPLIST_FILE.
#
# It is also safe to run standalone from CI against the source tree — in that
# mode it falls back to the source Info.plist + any *.entitlements file under
# iosAppOffline/.
set -euo pipefail

forbidden_entitlements=(
    "com.apple.developer.networking.HotspotConfiguration"
    "com.apple.developer.networking.multipath"
    "com.apple.developer.networking.networkextension"
    "com.apple.developer.networking.vpn.api"
    "com.apple.developer.networking.wifi-info"
    "com.apple.developer.associated-domains"
)

forbidden_info_plist_keys=(
    "NSAppTransportSecurity"
    "NSLocalNetworkUsageDescription"
    "NSBonjourServices"
)

fail() {
    echo "error: iosAppOffline guard: $1" >&2
    exit 1
}

check_plist_file() {
    local file="$1"
    local kind="$2"      # "info" or "entitlement"
    [[ -f "$file" ]] || return 0

    local keys=()
    if [[ "$kind" == "info" ]]; then
        keys=("${forbidden_info_plist_keys[@]}")
    else
        keys=("${forbidden_entitlements[@]}")
    fi

    for key in "${keys[@]}"; do
        # `PlistBuddy -c "Print :key"` exits non-zero if the key is absent, which
        # is the success path. We want to fail if the key is present.
        if /usr/libexec/PlistBuddy -c "Print :${key}" "$file" >/dev/null 2>&1; then
            fail "forbidden $kind key '${key}' found in ${file}"
        fi
    done
}

if [[ -n "${BUILT_PRODUCTS_DIR:-}" && -n "${INFOPLIST_PATH:-}" ]]; then
    # Running inside Xcode — check the resolved/baked artifacts.
    resolved_info_plist="${BUILT_PRODUCTS_DIR}/${INFOPLIST_PATH}"
    check_plist_file "$resolved_info_plist" "info"

    if [[ -n "${CODE_SIGN_ENTITLEMENTS:-}" ]]; then
        # CODE_SIGN_ENTITLEMENTS is relative to SRCROOT.
        entitlements_path="${SRCROOT}/${CODE_SIGN_ENTITLEMENTS}"
        check_plist_file "$entitlements_path" "entitlement"
    fi
else
    # CI / standalone mode — work against the source tree.
    repo_root="$(cd "$(dirname "$0")/.." && pwd)"
    check_plist_file "${repo_root}/iosAppOffline/iosAppOffline/Info.plist" "info"
    while IFS= read -r -d '' ent; do
        check_plist_file "$ent" "entitlement"
    done < <(find "${repo_root}/iosAppOffline" -name '*.entitlements' -print0)
fi

echo "iosAppOffline networking guard: OK"
