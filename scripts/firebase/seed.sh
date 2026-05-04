#!/usr/bin/env bash
# Seeds the Firebase Emulator Suite with a baseline state for Maestro flows.
#
# Idempotent: safe to call repeatedly. Test users are paired with Maestro
# credentials from scripts/e2e-test-user.properties so flows can sign in without
# going through the UI sign-up path on every run.
#
# Usage:
#   scripts/firebase/seed.sh                                # default project demo-moneysurfer
#   FIREBASE_PROJECT_ID=foo scripts/firebase/seed.sh        # override project
#
# Add new fixtures by appending more `signup_user` / `firestore_set` calls.

set -euo pipefail

PROJECT_ID="${FIREBASE_PROJECT_ID:-demo-moneysurfer}"
AUTH_HOST="${AUTH_EMULATOR_HOST:-localhost:9099}"
FIRESTORE_HOST="${FIRESTORE_EMULATOR_HOST:-localhost:8080}"
TEST_USER_FILE="${TEST_USER_FILE:-scripts/e2e-test-user.properties}"

if [[ -f "${TEST_USER_FILE}" ]]; then
    # shellcheck disable=SC1090
    source "${TEST_USER_FILE}"
fi

TEST_EMAIL="${TEST_EMAIL:-e2e+owner@test.local}"
TEST_PASSWORD="${TEST_PASSWORD:-password}"
TEST_VIEWER_EMAIL="${TEST_VIEWER_EMAIL:-e2e+viewer@test.local}"

# REST: signUp creates an Auth user without going through the sign-up UI.
# Returns 200 with idToken if new, 400 if email exists. Either is fine for seed.
signup_user() {
    local email="$1"
    local password="$2"
    curl -fsS -o /dev/null -X POST \
        "http://${AUTH_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key" \
        -H "Content-Type: application/json" \
        -d "{\"email\":\"${email}\",\"password\":\"${password}\",\"returnSecureToken\":true}" \
        > /dev/null 2>&1 || true # collision (already exists) is OK
    echo "  user: ${email}"
}

echo "Seeding Firebase Emulator (project=${PROJECT_ID})..."
echo "[users]"
signup_user "${TEST_EMAIL}" "${TEST_PASSWORD}"
signup_user "${TEST_VIEWER_EMAIL}" "${TEST_PASSWORD}"

# Seeding `appConfig/mobile` via REST is blocked by Firestore rules
# (`appConfig` is read-only for clients). The static rule
# `hasValidClientVersion() >= 1` doesn't depend on this doc — DTO defaults
# satisfy it. When the rule moves to dynamic via `get(/appConfig/mobile)`,
# add a setup that imports the doc through the Admin SDK or bakes it into
# rules-loading itself.

echo "Done."
