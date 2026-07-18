#!/usr/bin/env bash
# Nightly Android E2E + instrumented device tests, run inside the booted AVD.
#
# Invoked from .github/workflows/nightly.yml (maestro-android job) as the
# android-emulator-runner `script`. That action executes its `script` input via
# `sh -c <script>` — /bin/sh is dash on the ubuntu runner, which both rejects
# `set -o pipefail` and (empirically) mis-parses this multi-line body when it is
# inlined in YAML, aborting with "Syntax error: end of file unexpected
# (expecting fi)" before any gradle task runs. Keeping the logic in a committed
# file and calling it as a one-line `bash scripts/ci/nightly-android-e2e.sh`
# sidesteps dash entirely: the real work runs in bash with pipefail intact.
#
# `overall` accumulates failures so a red flow doesn't skip the later workloads
# (each still uploads its artifacts in the workflow's always() steps).
set -uo pipefail

overall=0

# 1) Online E2E suite — the dominant flake source (AVD + Firebase emulator +
#    full flow set). Auto-retry once before giving up so a single flaky flow
#    doesn't cost a whole night of signal.
run_online() { ./gradlew qaMaestroAndroid --stacktrace; }
if ! run_online; then
  echo "::warning title=E2E flake guard::qaMaestroAndroid failed — retrying once."
  run_online || overall=1
fi

# 2) Offline golden flow (issue #172 §4 bonus). Cheap single flow that otherwise
#    ran nowhere in CI — reuse the booted AVD.
./gradlew qaMaestroOfflineAndroid --stacktrace || overall=1

# 3) Instrumented device tests (issue #172 §5). `:integration-test` needs the
#    Firebase emulator, so wrap the whole device scope in emulators:exec
#    (harmless for the modules that don't use it). Deterministic — not retried;
#    a retry would just double a real failure.
firebase emulators:exec --project demo-moneysurfer --only auth,firestore \
  "./gradlew qaAndroidDevice --continue --stacktrace" || overall=1

exit "$overall"
