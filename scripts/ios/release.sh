#!/usr/bin/env bash
set -euo pipefail

# Build a Release archive of an iOS target, export an App Store .ipa, and
# upload it to App Store Connect (TestFlight).
#
# Usage:
#   scripts/ios/release.sh main      # iosApp        → MoneySurfer
#   scripts/ios/release.sh offline   # iosAppOffline → MoneySurferOffline
#   scripts/ios/release.sh main --no-upload   # archive + export only
#
# Requirements:
#   - Xcode + command line tools
#   - App Store Connect API key for upload. Provide via local.properties at
#     repo root (gitignored) or via env vars:
#       ASC_API_KEY_ID      e.g. ABCDE12345
#       ASC_API_ISSUER_ID   uuid from App Store Connect → Users and Access → Keys
#       ASC_API_KEY_PATH    path to AuthKey_<id>.p8 (relative to repo root or absolute).
#                           Recommended: keystore/AuthKey_<id>.p8 (the keystore/ dir is gitignored).
#     Env vars override local.properties. The .p8 is copied into
#     ~/.appstoreconnect/private_keys/ so altool can find it.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

target="${1:-}"
upload=true
case "${2:-}" in
  --no-upload) upload=false ;;
  "") ;;
  *) echo "Unknown option: $2"; exit 2 ;;
esac

case "$target" in
  main)
    project="$REPO_ROOT/iosApp/iosApp.xcodeproj"
    scheme="iosApp"
    ;;
  offline)
    project="$REPO_ROOT/iosAppOffline/iosAppOffline.xcodeproj"
    scheme="iosAppOffline"
    ;;
  ""|-h|--help)
    sed -n '3,20p' "$0"; exit 0 ;;
  *)
    echo "Unknown target: $target (expected: main | offline)"; exit 2 ;;
esac

ts="$(date +%Y%m%d-%H%M%S)"
build_dir="$REPO_ROOT/build/ios/$scheme/$ts"
archive_path="$build_dir/$scheme.xcarchive"
export_dir="$build_dir/export"
export_options="$REPO_ROOT/scripts/ios/ExportOptions.plist"

mkdir -p "$build_dir"

echo "▸ Archiving $scheme (Release)…"
xcodebuild \
  -project "$project" \
  -scheme "$scheme" \
  -configuration Release \
  -destination 'generic/platform=iOS' \
  -archivePath "$archive_path" \
  -allowProvisioningUpdates \
  archive

echo "▸ Exporting App Store .ipa…"
xcodebuild \
  -exportArchive \
  -archivePath "$archive_path" \
  -exportPath "$export_dir" \
  -exportOptionsPlist "$export_options" \
  -allowProvisioningUpdates

ipa_path="$(ls "$export_dir"/*.ipa | head -n1)"
echo "▸ IPA: $ipa_path"

if [[ "$upload" != true ]]; then
  echo "▸ --no-upload set, skipping upload."
  exit 0
fi

# Load fallbacks from local.properties (env vars take precedence).
local_props="$REPO_ROOT/local.properties"
read_prop() {
  [[ -f "$local_props" ]] || return 0
  awk -F= -v k="$1" '$1==k { sub(/^[ \t]+/,"",$2); sub(/[ \t]+$/,"",$2); print $2; exit }' "$local_props"
}
: "${ASC_API_KEY_ID:=$(read_prop ASC_API_KEY_ID)}"
: "${ASC_API_ISSUER_ID:=$(read_prop ASC_API_ISSUER_ID)}"
: "${ASC_API_KEY_PATH:=$(read_prop ASC_API_KEY_PATH)}"

: "${ASC_API_KEY_ID:?ASC_API_KEY_ID missing (set in local.properties or env)}"
: "${ASC_API_ISSUER_ID:?ASC_API_ISSUER_ID missing (set in local.properties or env)}"

if [[ -n "${ASC_API_KEY_PATH:-}" ]]; then
  # Expand leading ~ and resolve relative paths against repo root.
  case "$ASC_API_KEY_PATH" in
    "~"|"~/"*) ASC_API_KEY_PATH="$HOME${ASC_API_KEY_PATH#\~}" ;;
  esac
  [[ "$ASC_API_KEY_PATH" = /* ]] || ASC_API_KEY_PATH="$REPO_ROOT/$ASC_API_KEY_PATH"
  [[ -f "$ASC_API_KEY_PATH" ]] || { echo "ASC_API_KEY_PATH not found: $ASC_API_KEY_PATH"; exit 1; }
  keys_dir="$HOME/.appstoreconnect/private_keys"
  mkdir -p "$keys_dir"
  cp -f "$ASC_API_KEY_PATH" "$keys_dir/AuthKey_${ASC_API_KEY_ID}.p8"
fi

echo "▸ Uploading to App Store Connect…"
xcrun altool --upload-app \
  -f "$ipa_path" \
  -t ios \
  --apiKey "$ASC_API_KEY_ID" \
  --apiIssuer "$ASC_API_ISSUER_ID"

echo "✓ Uploaded $(basename "$ipa_path"). Check App Store Connect → TestFlight in a few minutes."
