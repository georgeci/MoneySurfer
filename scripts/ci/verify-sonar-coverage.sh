#!/usr/bin/env bash
# Asks SonarCloud what coverage it actually stored, and fails when a module the Kover
# aggregation covers arrived with none.
#
# Why this is not paranoia: the analysis is green either way. `sonar.coverage.exclusions`
# is resolved server-side whenever the scanner omits the key, and the value SonarCloud
# serves this project is `**/*` — so every module that sent no value of its own
# (`:navigation`, `:utils`, `:sync-surfer`, `:app-config:*`, most of `:feature:*`) had its
# whole coverage dropped on import while the quality gate reported "0.0% Coverage on New
# Code" and passed. Nothing on the build side could see it: the Kover report was complete
# and Codecov measured those modules normally — only Sonar's copy was empty, which is
# exactly what makes the 80% new-code gate unable to fail there. `build.gradle.kts` now
# sends the key for every module; this step is what notices if that ever stops working.
#
# Expected modules arrive on stdin, one repository-relative directory per line:
#   ./gradlew -q printSonarCoveredModules | scripts/ci/verify-sonar-coverage.sh
#
# Branch analyses only. A pull-request analysis stores components for the changed files
# alone, so there "module X has no coverage" is indistinguishable from "module X was not
# touched" — running it on a PR would fail on every small change.
#
# Infrastructure trouble (no report-task.txt, a compute engine still working, an HTTP
# hiccup) warns and passes: this guards a wiring regression, it is not a health check for
# SonarCloud.
set -uo pipefail

REPORT_TASK="${REPORT_TASK:-build/sonar/report-task.txt}"
API_HOST="${SONAR_HOST_URL:-https://sonarcloud.io}"
# The compute engine queues behind every other analysis in the organization, so this is a
# "give up and stay quiet" bound rather than a deadline anything depends on.
POLL_TIMEOUT_SECONDS="${POLL_TIMEOUT_SECONDS:-600}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-10}"

warn() {
  echo "::warning::$*"
  echo "⚠️ $*" >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
}

# Reads are public for a public project; the token is used when present so this also works
# if the project is ever made private.
sonar_get() {
  if [[ -n "${SONAR_TOKEN:-}" ]]; then
    curl -sS --fail-with-body -u "${SONAR_TOKEN}:" "$@"
  else
    curl -sS --fail-with-body "$@"
  fi
}

mapfile -t expected_modules < <(grep -v '^[[:space:]]*$' || true)
if [[ ${#expected_modules[@]} -eq 0 ]]; then
  warn "No expected modules on stdin — nothing to verify. Pipe \`./gradlew -q printSonarCoveredModules\` in."
  exit 0
fi

if [[ ! -f "$REPORT_TASK" ]]; then
  warn "No $REPORT_TASK — the scanner did not run, so there is no analysis to verify."
  exit 0
fi

project_key=$(sed -n 's/^projectKey=//p' "$REPORT_TASK")
ce_task_url=$(sed -n 's/^ceTaskUrl=//p' "$REPORT_TASK")
if [[ -z "$project_key" || -z "$ce_task_url" ]]; then
  warn "$REPORT_TASK carries no projectKey/ceTaskUrl — cannot verify the analysis."
  exit 0
fi

# The scanner returns as soon as the report is uploaded; the measures this checks only
# exist once the compute engine has processed it.
#
# When the task cannot be followed at all — `api/ce/task` needs a token the fork/local
# case does not have — the check still runs against whatever SonarCloud last stored. That
# is one analysis behind at worst, and a wiring regression shows up in it just the same.
deadline=$((SECONDS + POLL_TIMEOUT_SECONDS))
unreadable=0
status=""
while [[ $SECONDS -lt $deadline ]]; do
  status=$(sonar_get "$ce_task_url" 2>/dev/null | python3 -c 'import json,sys; print(json.load(sys.stdin)["task"]["status"])' 2>/dev/null)
  case "$status" in
    SUCCESS) break ;;
    FAILED | CANCELED)
      warn "SonarCloud reported the analysis as $status — no measures to verify."
      exit 0
      ;;
    "")
      unreadable=$((unreadable + 1))
      if [[ $unreadable -ge 3 ]]; then
        warn "Could not read the compute-engine task at $ce_task_url — checking the measures SonarCloud last stored."
        break
      fi
      sleep "$POLL_INTERVAL_SECONDS"
      ;;
    *)
      unreadable=0
      sleep "$POLL_INTERVAL_SECONDS"
      ;;
  esac
done

if [[ "$status" != "SUCCESS" && $unreadable -lt 3 ]]; then
  warn "SonarCloud was still processing the analysis after ${POLL_TIMEOUT_SECONDS}s — skipping the coverage check."
  exit 0
fi

# One page holds every directory of this project several times over; `total` is checked
# below rather than assumed, so a repository that outgrows it says so instead of silently
# verifying a prefix.
tree_json=$(sonar_get \
  "$API_HOST/api/measures/component_tree?component=$project_key&metricKeys=lines_to_cover&qualifiers=DIR&ps=500")
if [[ -z "$tree_json" ]]; then
  warn "SonarCloud returned no component tree for $project_key — skipping the coverage check."
  exit 0
fi

covered_dirs=$(printf '%s' "$tree_json" | python3 -c '
import json, sys

payload = json.load(sys.stdin)
paging = payload.get("paging", {})
if paging.get("total", 0) > paging.get("pageSize", 0):
    print("::PAGED::")
for component in payload.get("components", []):
    if component.get("measures"):
        print(component["path"])
') || {
  warn "Could not read the component tree for $project_key — skipping the coverage check."
  exit 0
}

if grep -q '^::PAGED::$' <<< "$covered_dirs"; then
  warn "SonarCloud returned more directories than one page holds — the check below sees only the first 500."
  covered_dirs=$(grep -v '^::PAGED::$' <<< "$covered_dirs")
fi

missing=()
for module in "${expected_modules[@]}"; do
  # A module is covered when at least one directory under its own sources carries
  # `lines_to_cover`. Anchored on `<module>/src/` so `sync/api` cannot be satisfied by
  # `sync/api-something`, nor `feature/account` by `feature/accounts`.
  if ! grep -q "^${module}/src/" <<< "$covered_dirs"; then
    missing+=("$module")
  fi
done

if [[ ${#missing[@]} -gt 0 ]]; then
  echo "::error::SonarCloud stored no coverage for ${#missing[@]} module(s) that Kover covers: ${missing[*]}"
  {
    echo "### SonarCloud coverage import"
    echo
    echo "No coverage arrived for ${#missing[@]} module(s) the Kover aggregation covers:"
    echo
    # shellcheck disable=SC2016 # `%s` is printf's placeholder, not a shell expansion.
    printf -- '- `%s`\n' "${missing[@]}"
    echo
    echo "Check \`sonar.coverage.exclusions\` for those modules in \`build.gradle.kts\` —"
    echo "a module that sends no value inherits SonarCloud's server-side \`**/*\`."
  } >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
  exit 1
fi

echo "SonarCloud holds coverage for all ${#expected_modules[@]} expected modules."
echo "✅ SonarCloud coverage import verified for ${#expected_modules[@]} modules." >> "${GITHUB_STEP_SUMMARY:-/dev/null}"
