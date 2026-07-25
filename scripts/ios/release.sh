#!/usr/bin/env bash
set -euo pipefail

# Build a Release archive of an iOS target, export an App Store .ipa, and
# upload it to App Store Connect (TestFlight).
#
# Usage:
#   scripts/ios/release.sh main      # iosApp        → MoneySurfer
#   scripts/ios/release.sh offline   # iosAppOffline → MoneySurferOffline
#   scripts/ios/release.sh all       # main, then offline (sequential)
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
#       ASC_TEAM_ID         optional, default 92SLHZAN8L. Used to substitute into ExportOptions.plist.
#       ASC_BUILD_NUMBER    optional. If set, passed as APP_VERSION_CODE=<n> build-setting
#                           override to xcodebuild archive so CFBundleVersion is unique
#                           (TestFlight rejects duplicates). Working tree is not modified —
#                           the xcconfig variable resolves at build time. Recommended in
#                           CI: ASC_BUILD_NUMBER=$(date +%s).
#     Env vars override local.properties. The .p8 is copied into
#     ~/.appstoreconnect/private_keys/ with mode 0600 so altool can find it.

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

target="${1:-}"
upload=true
case "${2:-}" in
  --no-upload) upload=false ;;
  "") ;;
  *) echo "Unknown option: $2"; exit 2 ;;
esac

case "$target" in
  main|offline|all) ;;
  ""|-h|--help) sed -n '3,25p' "$0"; exit 0 ;;
  *) echo "Unknown target: $target (expected: main | offline | all)"; exit 2 ;;
esac

# Load fallbacks from local.properties (env vars take precedence). Done up
# front so ASC_TEAM_ID can be substituted into ExportOptions.plist below.
local_props="$REPO_ROOT/local.properties"
read_prop() {
  [[ -f "$local_props" ]] || return 0
  awk -F= -v k="$1" '$1==k { sub(/^[ \t]+/,"",$2); sub(/[ \t]+$/,"",$2); print $2; exit }' "$local_props"
}
: "${ASC_API_KEY_ID:=$(read_prop ASC_API_KEY_ID)}"
: "${ASC_API_ISSUER_ID:=$(read_prop ASC_API_ISSUER_ID)}"
: "${ASC_API_KEY_PATH:=$(read_prop ASC_API_KEY_PATH)}"
: "${ASC_TEAM_ID:=$(read_prop ASC_TEAM_ID)}"
: "${ASC_TEAM_ID:=92SLHZAN8L}"
: "${ASC_BUILD_NUMBER:=$(read_prop ASC_BUILD_NUMBER)}"

stage_api_key() {
  [[ "$upload" == true ]] || return 0
  : "${ASC_API_KEY_ID:?ASC_API_KEY_ID missing (set in local.properties or env)}"
  : "${ASC_API_ISSUER_ID:?ASC_API_ISSUER_ID missing (set in local.properties or env)}"
  [[ -n "${ASC_API_KEY_PATH:-}" ]] || return 0
  case "$ASC_API_KEY_PATH" in
    "~"|"~/"*) ASC_API_KEY_PATH="$HOME${ASC_API_KEY_PATH#\~}" ;;
  esac
  [[ "$ASC_API_KEY_PATH" = /* ]] || ASC_API_KEY_PATH="$REPO_ROOT/$ASC_API_KEY_PATH"
  [[ -f "$ASC_API_KEY_PATH" ]] || { echo "ASC_API_KEY_PATH not found: $ASC_API_KEY_PATH"; exit 1; }
  local keys_dir="$HOME/.appstoreconnect/private_keys"
  mkdir -p "$keys_dir"
  chmod 700 "$keys_dir"
  local dest="$keys_dir/AuthKey_${ASC_API_KEY_ID}.p8"
  cp -f "$ASC_API_KEY_PATH" "$dest"
  chmod 600 "$dest"
  ASC_STAGED_API_KEY_PATH="$dest"
}

run_target() {
  local t="$1" project scheme
  case "$t" in
    main)
      project="$REPO_ROOT/iosApp/iosApp.xcodeproj"
      scheme="iosApp"
      ;;
    offline)
      project="$REPO_ROOT/iosAppOffline/iosAppOffline.xcodeproj"
      scheme="iosAppOffline"
      ;;
  esac

  local ts build_dir archive_path export_dir export_options_template export_options
  ts="$(date +%Y%m%d-%H%M%S)"
  build_dir="$REPO_ROOT/build/ios/$scheme/$ts"
  archive_path="$build_dir/$scheme.xcarchive"
  export_dir="$build_dir/export"
  export_options_template="$REPO_ROOT/scripts/ios/ExportOptions.plist"
  export_options="$build_dir/ExportOptions.plist"

  mkdir -p "$build_dir"
  sed "s/__TEAM_ID__/$ASC_TEAM_ID/" "$export_options_template" > "$export_options"

  local -a version_override=()
  if [[ -n "${ASC_BUILD_NUMBER:-}" ]]; then
    echo "▸ [$scheme] Overriding APP_VERSION_CODE → $ASC_BUILD_NUMBER (xcodebuild build-setting)…"
    version_override=("APP_VERSION_CODE=$ASC_BUILD_NUMBER")
  fi
  local -a authentication_args=()
  if [[ -n "${ASC_STAGED_API_KEY_PATH:-}" ]]; then
    authentication_args=(
      -authenticationKeyPath "$ASC_STAGED_API_KEY_PATH"
      -authenticationKeyID "$ASC_API_KEY_ID"
      -authenticationKeyIssuerID "$ASC_API_ISSUER_ID"
    )
  fi

  echo "▸ [$scheme] Archiving (Release)…"
  xcodebuild \
    -project "$project" \
    -scheme "$scheme" \
    -configuration Release \
    -destination 'generic/platform=iOS' \
    -archivePath "$archive_path" \
    -allowProvisioningUpdates \
    ${authentication_args[@]+"${authentication_args[@]}"} \
    ${version_override[@]+"${version_override[@]}"} \
    archive

  echo "▸ [$scheme] Exporting App Store .ipa…"
  xcodebuild \
    -exportArchive \
    -archivePath "$archive_path" \
    -exportPath "$export_dir" \
    -exportOptionsPlist "$export_options" \
    -allowProvisioningUpdates \
    ${authentication_args[@]+"${authentication_args[@]}"}

  local ipa_path
  ipa_path="$(ls "$export_dir"/*.ipa | head -n1)"
  echo "▸ [$scheme] IPA: $ipa_path"

  if [[ "$upload" != true ]]; then
    echo "▸ [$scheme] --no-upload set, skipping upload."
    return 0
  fi

  echo "▸ [$scheme] Uploading to App Store Connect…"
  xcrun altool --upload-app \
    -f "$ipa_path" \
    -t ios \
    --apiKey "$ASC_API_KEY_ID" \
    --apiIssuer "$ASC_API_ISSUER_ID"

  echo "✓ [$scheme] Uploaded $(basename "$ipa_path"). Check App Store Connect → TestFlight in a few minutes."
}

stage_api_key

case "$target" in
  main|offline) run_target "$target" ;;
  all) run_target main; run_target offline ;;
esac
