#!/usr/bin/env bash
set -euo pipefail

# Resize raw simulator screenshots to a target App Store size,
# preserving aspect ratio and padding with white.
# Usage: resize.sh [src_dir] [out_dir] [WIDTHxHEIGHT]
# Defaults: raw 6_5 1242x2688

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC_DIR="$SCRIPT_DIR/${1:-raw}"
OUT_DIR="$SCRIPT_DIR/${2:-6_5}"
SIZE="${3:-1242x2688}"
W="${SIZE%x*}"
H="${SIZE#*x}"

mkdir -p "$OUT_DIR"

shopt -s nullglob
count=0
for src in "$SRC_DIR"/*.png "$SRC_DIR"/*.jpg "$SRC_DIR"/*.jpeg; do
  base="$(basename "$src")"
  out="$OUT_DIR/${base%.*}.png"
  magick "$src" \
    -resize "${W}x${H}" \
    -background white -gravity center \
    -extent "${W}x${H}" \
    "$out"
  echo "→ $out"
  count=$((count + 1))
done

echo "Done: $count file(s) → $OUT_DIR"
