#!/usr/bin/env bash
# Stops a `--background` emulator started via start.sh.
# No-op if no PID file exists.

set -euo pipefail

cd "$(dirname "$0")/../.."

PID_FILE="build/firebase-emulator.pid"
if [[ ! -f "$PID_FILE" ]]; then
    echo "No PID file at $PID_FILE — emulator not started in background?"
    exit 0
fi

PID="$(cat "$PID_FILE")"
if kill -0 "$PID" 2>/dev/null; then
    kill "$PID"
    echo "Sent SIGTERM to emulator (PID $PID)"
else
    echo "PID $PID not running — cleaning up stale PID file"
fi
rm -f "$PID_FILE"
