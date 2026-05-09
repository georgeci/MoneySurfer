#!/usr/bin/env bash
# Render AppIcon-Dev.appiconset PNGs by overlaying a red "DEV" ribbon on the
# bottom of each AppIcon.appiconset PNG. Mirrors the easylauncher ribbon used
# by the Android `.dev` build type (KmpAppConventionPlugin).
#
# Run once after the launcher icon changes:
#   brew install imagemagick
#   ./scripts/generate-ios-dev-icons.sh
set -euo pipefail

if ! command -v magick >/dev/null 2>&1; then
  echo "ImageMagick (magick) not found. Install with: brew install imagemagick" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"

# Homebrew ImageMagick can't resolve macOS font *names* (no fontconfig wiring),
# so we hand it an absolute .ttc/.ttf path. Try a few candidates.
FONT=""
for candidate in \
  "/System/Library/Fonts/Helvetica.ttc" \
  "/System/Library/Fonts/HelveticaNeue.ttc" \
  "/System/Library/Fonts/Supplemental/Arial Bold.ttf" \
  "/System/Library/Fonts/SFNSRounded.ttf"; do
  if [ -f "$candidate" ]; then FONT="$candidate"; break; fi
done
if [ -z "$FONT" ]; then
  echo "No usable system font found for the DEV ribbon. Edit FONT in $0." >&2
  exit 1
fi

render_set() {
  local src="$1"
  local dst="$2"
  local label="$3"
  echo "→ $dst  [$label]"
  shopt -s nullglob
  for icon in "$src"/*.png; do
    local name; name="$(basename "$icon")"
    local out="$dst/$name"
    local w; w=$(magick identify -format "%w" "$icon")
    local ribbon_h=$(( w * 22 / 100 ))
    # `caption:` auto-fits the label to the ribbon's WxH, so longer labels
    # (e.g. "OFF DEV") still render legibly on the smallest icons.
    magick "$icon" \
      \( -background "#C0FF1744" -fill white -font "$FONT" \
         -gravity center -size "${w}x${ribbon_h}" caption:"$label" \) \
      -gravity south -composite \
      "$out"
  done
  shopt -u nullglob
}

render_set \
  "$ROOT/iosApp/iosApp/Assets.xcassets/AppIcon.appiconset" \
  "$ROOT/iosApp/iosApp/Assets.xcassets/AppIcon-Dev.appiconset" \
  "DEV"

render_set \
  "$ROOT/iosAppOffline/iosAppOffline/Assets.xcassets/AppIcon.appiconset" \
  "$ROOT/iosAppOffline/iosAppOffline/Assets.xcassets/AppIcon-Dev.appiconset" \
  "OFF DEV"

echo "Done. Commit the regenerated AppIcon-Dev.appiconset PNGs."
