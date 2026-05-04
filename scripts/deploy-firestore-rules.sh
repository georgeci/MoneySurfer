#!/usr/bin/env bash
# Deploy firestore.rules via the Firebase Rules REST API.
#
# Why REST and not `firebase deploy`: the project may have only gcloud
# configured (no firebase-tools npm install). Avoids a second CLI footprint.
#
# Usage:
#   scripts/deploy-firestore-rules.sh [PROJECT_ID]
#
# Defaults to PROJECT_ID = `gcloud config get-value project`.
#
# Requires: gcloud (authenticated), jq, curl.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RULES_FILE="$REPO_ROOT/firestore.rules"

PROJECT="${1:-$(gcloud config get-value project 2>/dev/null)}"
if [ -z "$PROJECT" ]; then
    echo "ERROR: no project — pass as arg or set via 'gcloud config set project'" >&2
    exit 1
fi

if [ ! -f "$RULES_FILE" ]; then
    echo "ERROR: $RULES_FILE not found" >&2
    exit 1
fi

TOKEN="$(gcloud auth print-access-token 2>/dev/null)"
if [ -z "$TOKEN" ]; then
    echo "ERROR: gcloud auth print-access-token returned empty — run 'gcloud auth login'" >&2
    exit 1
fi

echo "Deploying $RULES_FILE → project=$PROJECT"

# Step 1: create a Ruleset.
RULES_CONTENT="$(jq -Rs . < "$RULES_FILE")"
RULESET_BODY="$(cat <<EOF
{
  "source": {
    "files": [
      {
        "name": "firestore.rules",
        "content": $RULES_CONTENT
      }
    ]
  }
}
EOF
)"

RULESET_RESPONSE="$(curl -sS -X POST \
  "https://firebaserules.googleapis.com/v1/projects/${PROJECT}/rulesets" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Goog-User-Project: ${PROJECT}" \
  -H "Content-Type: application/json" \
  -d "$RULESET_BODY")"

# The response body contains literal newlines from the rules file in the
# `content` field, which standard JSON disallows. Extract `name` via grep.
RULESET_NAME="$(echo "$RULESET_RESPONSE" | grep -E '^\s+"name":\s+"projects/[^/]+/rulesets/' | head -1 | sed -E 's/.*"name": *"([^"]+)".*/\1/')"

if [ -z "$RULESET_NAME" ]; then
    echo "ERROR: failed to create ruleset" >&2
    echo "Response was:" >&2
    echo "$RULESET_RESPONSE" >&2
    exit 1
fi

echo "Created ruleset: $RULESET_NAME"

# Step 2: point the `cloud.firestore` release at the new ruleset.
RELEASE_BODY="$(cat <<EOF
{
  "release": {
    "name": "projects/${PROJECT}/releases/cloud.firestore",
    "rulesetName": "${RULESET_NAME}"
  }
}
EOF
)"

RELEASE_RESPONSE="$(curl -sS -w '\nHTTP_STATUS:%{http_code}' -X PATCH \
  "https://firebaserules.googleapis.com/v1/projects/${PROJECT}/releases/cloud.firestore" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Goog-User-Project: ${PROJECT}" \
  -H "Content-Type: application/json" \
  -d "$RELEASE_BODY")"

HTTP_STATUS="$(echo "$RELEASE_RESPONSE" | tail -1 | sed 's/HTTP_STATUS://')"
if [ "$HTTP_STATUS" = "404" ]; then
    echo "Release cloud.firestore does not exist yet; creating it."
    CREATE_RELEASE_BODY="$(cat <<EOF
{
  "name": "projects/${PROJECT}/releases/cloud.firestore",
  "rulesetName": "${RULESET_NAME}"
}
EOF
)"
    RELEASE_RESPONSE="$(curl -sS -w '\nHTTP_STATUS:%{http_code}' -X POST \
      "https://firebaserules.googleapis.com/v1/projects/${PROJECT}/releases" \
      -H "Authorization: Bearer $TOKEN" \
      -H "X-Goog-User-Project: ${PROJECT}" \
      -H "Content-Type: application/json" \
      -d "$CREATE_RELEASE_BODY")"
    HTTP_STATUS="$(echo "$RELEASE_RESPONSE" | tail -1 | sed 's/HTTP_STATUS://')"
fi

if [ "$HTTP_STATUS" != "200" ]; then
    echo "ERROR: release update failed (HTTP $HTTP_STATUS)" >&2
    echo "$RELEASE_RESPONSE" >&2
    exit 1
fi

echo "OK — release cloud.firestore now points to $RULESET_NAME"
