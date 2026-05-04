#!/usr/bin/env bash
# Boots the Firebase Emulator Suite for local development + tests.
#
# Auth + Firestore only — that's what `:data` and `:sync` actually call.
# UI lives at http://localhost:4000.
#
# Usage:
#   scripts/firebase/start.sh                  # foreground (Ctrl-C to stop)
#   scripts/firebase/start.sh --background     # detached, prints PID
#
# All emulator data is in-memory by default. Add `--export-on-exit=...` if you
# want to snapshot state between runs.

set -euo pipefail

cd "$(dirname "$0")/../.."

PROJECT_ID="${FIREBASE_PROJECT_ID:-moneysurfer-test}"

if [[ "${1:-}" == "--background" ]]; then
    nohup firebase emulators:start \
        --project "$PROJECT_ID" \
        --only auth,firestore,ui \
        > build/firebase-emulator.log 2>&1 &
    echo "$!" > build/firebase-emulator.pid
    echo "Emulator started (PID $(cat build/firebase-emulator.pid))"
    echo "Logs: build/firebase-emulator.log"
    echo "UI:   http://localhost:4000"
else
    exec firebase emulators:start \
        --project "$PROJECT_ID" \
        --only auth,firestore,ui
fi
