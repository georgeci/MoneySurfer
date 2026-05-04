#!/usr/bin/env bash
# Wipes Auth users + Firestore documents from a running emulator.
#
# Use between tests to keep state hermetic. Faster than restarting the
# emulator process. Doesn't reload security rules — `firebase deploy` style
# reloads happen automatically when `firestore.rules` changes on disk.

set -euo pipefail

PROJECT_ID="${FIREBASE_PROJECT_ID:-moneysurfer-test}"
AUTH_HOST="${AUTH_EMULATOR_HOST:-localhost:9099}"
FIRESTORE_HOST="${FIRESTORE_EMULATOR_HOST:-localhost:8080}"

echo "Resetting auth users..."
curl -fsS -X DELETE \
    "http://${AUTH_HOST}/emulator/v1/projects/${PROJECT_ID}/accounts" \
    -H 'Authorization: Bearer owner' \
    > /dev/null

echo "Resetting firestore documents..."
curl -fsS -X DELETE \
    "http://${FIRESTORE_HOST}/emulator/v1/projects/${PROJECT_ID}/databases/(default)/documents" \
    -H 'Authorization: Bearer owner' \
    > /dev/null

echo "Emulator reset complete."
