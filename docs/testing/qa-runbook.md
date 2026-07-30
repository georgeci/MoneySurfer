# QA Runbook

<!-- DOCS:TOC -->
## Contents
- [QA Runbook](#qa-runbook)
- [TL;DR for agents](#tldr-for-agents)
- [Setup](#setup)
  - [Dedicated AVD for Maestro](#dedicated-avd-for-maestro)
- [Firebase bootstrap (emulator)](#firebase-bootstrap-emulator)
- [QA tasks](#qa-tasks)
- [Plain test/Maestro tasks (no Allure)](#plain-testmaestro-tasks-no-allure)
  - [iOS scope: launch smoke only (issue #297)](#ios-scope-launch-smoke-only-issue-297)
- [Desktop UI tests (:composeApp:jvmTest)](#desktop-ui-tests-composeappjvmtest)
- [Integration tests (:integration-test)](#integration-tests-integration-test)
  - [Running locally](#running-locally)
  - [Hermetic single-shot runs](#hermetic-single-shot-runs)
- [Firebase test suite (firestore-tests)](#firebase-test-suite-firestore-tests)
- [Standalone Allure generators](#standalone-allure-generators)
- [Report Paths](#report-paths)
- [Published reports (GitHub Pages)](#published-reports-github-pages)
- [Manual CLI Equivalent](#manual-cli-equivalent)
<!-- DOCS:END -->

The operational half of testing: how to install the tooling, run each QA scope,
and find the resulting reports. For *what to test and in which layer*, read
[testing-strategy](testing-strategy.md) first — it is the entry point.

## TL;DR for agents

- All QA scopes follow one shape: `qa<Scope>` runs the scope's tests, attaches
  Kover where it applies, and **always** finalizes by generating an Allure
  report into `build/reports/allure/<scope>/` — even when tests fail.
- `qaCommon` is the JVM scope and needs no device and no emulator; everything
  Maestro or device-related does.
- Don't run broad scopes by default — pick the narrowest one covering the
  edited module.

READ WHEN:
- running or debugging a QA task locally
- looking for a test report, log, or artifact path
- setting up Maestro, Allure, or the dedicated AVD
- wiring a new scope into Allure/Kover

Related: [testing-strategy](testing-strategy.md),
[firebase-emulator](firebase-emulator.md), [screenshot-tests](screenshot-tests.md),
[sonarcloud](sonarcloud.md).

<!-- AI:SECTION id=qa-runbook task=testing,qa,reports,tooling -->
## Setup

```bash
brew install maestro
brew install allure
cp scripts/maestro/.env.example scripts/maestro/.env
cp scripts/e2e-test-user.properties.example scripts/e2e-test-user.properties
```

For Gradle QA/Maestro tasks, credentials are loaded from
`scripts/e2e-test-user.properties` (`TEST_EMAIL`, `TEST_PASSWORD`,
`TEST_VIEWER_EMAIL`). The file is gitignored — copy from the `.example`
template above and adjust if needed. `scripts/maestro/.env` is only for direct
CLI runs if you use it.

If `allure`/`maestro` aren't on the Gradle daemon's PATH (common when daemon was started by an IDE), set `ALLURE_BIN` / `MAESTRO_BIN` to absolute paths or run `./gradlew --stop` and re-launch from a shell that has them. The build script also probes `/opt/homebrew/bin`, `/usr/local/bin`, `/usr/bin`.

### Dedicated AVD for Maestro

Maestro Android needs a clean AVD whose IME does not interfere with
`inputText` (Gboard on Pixel images can drift into its own theme picker
mid-flow). One-time setup creates a headless AOSP-ATD AVD that mirrors the
image used by `reactivecircus/android-emulator-runner` in nightly CI:

```bash
# One-time: create the AVD (named MoneySurferMaestro). Idempotent.
scripts/maestro/avd-create.sh

# Before each Maestro run: boot it headless. Detached, returns once booted.
scripts/maestro/avd-start.sh

# Run flows (firebase emulator + APK install + maestro all in one).
./gradlew qaMaestroAndroid
```

The AVD has `hw.keyboard=yes`, so `inputText` is delivered as hardware key
events without showing a soft keyboard. Override the name/api/device with
`MAESTRO_AVD_NAME`, `MAESTRO_AVD_API`, `MAESTRO_AVD_DEVICE` if you ever need
a second profile — defaults match nightly CI (`pixel_6`, API 34, `aosp_atd`).
CI does the same thing through the `reactivecircus` action, so no extra
wiring is needed there.

## Firebase bootstrap (emulator)

Full reference — install, configuration, per-platform switching, troubleshooting
— is in [firebase-emulator](firebase-emulator.md). Bootstrap scripts live in
`scripts/firebase/` (`start.sh`, `stop.sh`, `reset.sh`, `seed.sh`).

Every entry point shares one emulator namespace: the Gradle wrappers,
`firestore-tests`, all four `scripts/firebase/*` scripts and the desktop host
resolve to `--project demo-moneysurfer`. Override with `FIREBASE_PROJECT_ID`
only if you deliberately want a separate namespace — and then set it for *every*
process in the run, or seeded state becomes invisible to whatever reads it.

## QA tasks

| Task | Tests | Kover | Allure output |
|---|---|---|---|
| `./gradlew qaCommon` | `testCommon` (JVM) | yes | `build/reports/allure/common/` |
| `./gradlew qaAndroidHost` | `testAndroidHost` | yes | `build/reports/allure/android-host/` |
| `./gradlew qaAndroidDevice` | `testAndroidDevice` (instrumented) | — | `build/reports/allure/android-device/` |
| `./gradlew qaMaestroAndroid` / `qaMaestro` | Android flows via `firebase emulators:exec` + seed | — | `build/reports/allure/maestro/` |
| `./gradlew qaMaestroIos` | iOS Simulator **launch smoke only** (see below) via `firebase emulators:exec` + seed | — | `build/reports/allure/maestro-ios/` |
| `./gradlew qaFirestoreRules` | Mocha (`firestore-tests/`) via `firebase emulators:exec --only firestore` | — | `build/reports/allure/firestore/` |
| `./gradlew qaJvmAndAndroid` | `testAllScopes` (common + Android host + Android device; no Maestro/Firestore-rules run) | yes | `build/reports/allure/all/` |

`qaAll` is a deprecated compatibility alias for `qaJvmAndAndroid`; it is not
an exhaustive run of every QA scope.

The common and Android-host aggregates discover test owners from
`commonTest`, `jvmTest`, and `androidHostTest` source directories rather than a
maintained module list. Adding one of those source sets is therefore enough to
join the corresponding aggregate.

## Plain test/Maestro tasks (no Allure)

> **Note**: Android Maestro APK builds use `BuildConfig.USE_EMULATOR=true`; iOS Maestro simulator builds set `MS_USE_EMULATOR=YES`. Flows always talk to the local Firebase Emulator, never to the production project. `qaMaestroAndroid` / `qaMaestroIos` boot and tear down the emulator automatically. Standalone `maestroRunAll*` tasks require the emulator to already be running (`scripts/firebase/start.sh`).

```bash
./gradlew testCommon
./gradlew testAndroidHost
./gradlew testAndroidDevice
./gradlew testAllScopes

./gradlew maestroRunAll
./gradlew maestroRunOne -PmaestroFlow=05_sign_out.yaml
./gradlew maestroRunAllJunit
./gradlew maestroRunOneJunit -PmaestroFlow=05_sign_out.yaml

./gradlew maestroRunAllAndroid
./gradlew maestroRunOneAndroid -PmaestroFlow=05_sign_out.yaml
./gradlew maestroRunAllIos
./gradlew maestroRunAllIosJunit
```

iOS defaults to simulator name `iPhone 17`. Override with
`-PiosSimulatorName="<name>"`; pass `-PiosSimulatorUdid=<udid>` when multiple
simulators are visible to Maestro.

### iOS scope: launch smoke only (issue #297)

The iOS E2E suites were non-deterministically red, so the two iOS entry points
are cut back to a single flow — [`scripts/maestro/ios/app-open.yaml`](../../scripts/maestro/ios/app-open.yaml):
install → launch with `clearState` → assert onboarding renders. It is
build-agnostic (`appId: ${APP_ID}`), so the same flow covers both apps:

| Task | App | Flow |
|---|---|---|
| `./gradlew qaMaestroIos` | online `.dev` build | launch smoke |
| `./gradlew qaMaestroOfflineIos` | offline `.dev` build | launch smoke |

Nothing was deleted: the online suite (`scripts/maestro/*.yaml`) and the offline
golden path (`scripts/maestro/offline/offline-golden.yaml`) still run in full on
Android via `qaMaestroAndroid` / `qaMaestroOfflineAndroid`. To drive the whole
suite on iOS while the flake work is in progress, use `maestroRunAllIos` /
`maestroRunAllIosJunit` (Firebase Emulator must already be running). Restoring
the suite in the QA tasks is a one-line change back to the `scripts/maestro/`
target in [`gradle/qa.gradle.kts`](../../gradle/qa.gradle.kts).

## Desktop UI tests (`:composeApp:jvmTest`)

Compose screen-state tests that render the real composables in-process via
`runComposeUiTest` (CMP 1.11 v2 API) inside kotest `StringSpec` blocks. They are
**headless** — no window, no display, no `xvfb` — so they run anywhere, including
display-less CI runners.

```bash
./gradlew :composeApp:jvmTest
```

Result XMLs land at `composeApp/build/test-results/jvmTest/*.xml`. No separate QA
entry point: `qaCommon` already includes this module, so Kover and Allure pick
them up automatically.

Conventions for writing them (mount the stateless content composable, address
nodes by `*TestTags`, mind the `StandardTestDispatcher` default) are in
[testing-strategy](testing-strategy.md#desktop-ui-tests-compose-jvmtest).

## Integration tests (`:integration-test`)

Module that exercises the full **domain → sync → data → Firebase** stack against
the local Firebase Emulator Suite. Two targets:

- **JVM** (`jvmTest`) — Room-only integration. No emulator needed, runs anywhere.
- **Android device** (`connectedAndroidDeviceTest`) — real gitlive Firebase
  client driving Firestore + Auth emulators. Needs an Android emulator/device
  + Firebase Emulator Suite up on the host.

### Running locally

```bash
# Terminal 1 — boot Firebase emulators (firestore + auth, port 8080 / 9099).
firebase emulators:start --only firestore,auth --project demo-moneysurfer

# Terminal 2 — make sure an AVD is up (or a USB device with adb), then:
./gradlew :integration-test:connectedAndroidDeviceTest

# JVM-only suite (no emulator required):
./gradlew :integration-test:jvmTest
```

Result XMLs land at:

- `integration-test/build/test-results/jvmTest/*.xml`
- `integration-test/build/outputs/androidTest-results/connected/androidMain/*.xml`

`qaCommon` and `qaAndroidDevice` already include this module — Allure picks up
the XMLs automatically.

> **Note**: `qaAndroidDevice` requires the Firebase Emulator Suite running.
> Without it, the device-IT step fails with `PERMISSION_DENIED` /
> `Failed to connect to /10.0.2.2:9099`.

### Hermetic single-shot runs

Two ready-made wrappers boot the Firebase emulator around the Gradle invocation
so you don't have to keep a long-running emulator process. Both pin
`--project demo-moneysurfer` (matches `EmulatorEnv.EMULATOR_PROJECT_ID`).

```bash
# AVD already running (Studio or `emulator -avd <name> &`).
# Wraps `firebase emulators:exec` around :integration-test:connectedAndroidDeviceTest.
./gradlew qaIntegrationDeviceEmulator

# Fully hermetic — boots a Gradle-Managed AVD (Pixel 6 / API 34 / aosp-atd) on
# demand and tears it down. First run downloads the system image (~200 MB,
# cached afterwards under ~/.android/avd/gradle-managed/).
./gradlew qaIntegrationDeviceHermetic
```

The managed AVD is declared in [integration-test/build.gradle.kts](../../integration-test/build.gradle.kts)
as `integrationAvd`. AGP generates these companion tasks off it:

- `:integration-test:integrationAvdAndroidDeviceTest` — runs the suite
- `:integration-test:integrationAvdSetup` — provisions the system image (run once)


## Firebase test suite (`firestore-tests`)

Firestore rules suite (Mocha + `@firebase/rules-unit-testing`). Has both a
direct npm entry point and a Gradle wrapper that emits Allure.

```bash
cd firestore-tests
npm install
npm test            # human-readable spec reporter
npm run test:junit  # JUnit XML to ../build/test-results/firestore/firestore-report.xml
```

Both scripts wrap `firebase emulators:exec --only firestore` with project
`demo-moneysurfer`.

For an Allure report on top of the JUnit XML, use the Gradle wrapper — same
shape as `qaMaestro`:

```bash
./gradlew qaFirestoreRules
# → build/test-results/firestore/firestore-report.xml
# → build/reports/allure/firestore/index.html (always, even on red)
```

CI runs this on every PR as the `firestore-rules` job in
[.github/workflows/ci.yml](../../.github/workflows/ci.yml).

Watch mode (no Allure):

```bash
# terminal 1
firebase emulators:start --only firestore --project demo-moneysurfer

# terminal 2
cd firestore-tests
npm run test:watch
```

## Standalone Allure generators

Each can be run on its own; it consumes whatever results are already on disk and does not trigger tests:

```bash
./gradlew allureGenerateCommon
./gradlew allureGenerateAndroidHost
./gradlew allureGenerateAndroidDevice
./gradlew allureGenerateMaestro
./gradlew allureGenerateMaestroIos
./gradlew allureGenerateFirestore
./gradlew allureGenerateAll
```

## Report Paths

- Maestro Android JUnit XML: `build/test-results/maestro/maestro-report.xml` (all flows), `build/test-results/maestro/maestro-<flow>.xml` (single flow)
- Maestro iOS JUnit XML: `build/test-results/maestro-ios/maestro-ios-report.xml`
- Maestro native Allure results (generated from JUnit + debug artifacts): `build/allure-results/maestro/`
- Maestro iOS native Allure results: `build/allure-results/maestro-ios/`
- Maestro logs (when Gradle task runs): `build/logs/maestro/maestroRunAllJunit.out.log`, `build/logs/maestro/maestroRunAllJunit.err.log`
- Maestro debug output (hierarchy, run diagnostics): `build/maestro-debug/`
- Maestro screenshots/artifacts: `build/maestro-artifacts/`
- Maestro iOS debug output/artifacts: `build/maestro-ios-debug/`, `build/maestro-ios-artifacts/`
- Firestore rules JUnit XML: `build/test-results/firestore/firestore-report.xml`
- Allure (per-scope):
  - `build/reports/allure/common/index.html`
  - `build/reports/allure/android-host/index.html`
  - `build/reports/allure/android-device/index.html`
  - `build/reports/allure/maestro/index.html`
  - `build/reports/allure/maestro-ios/index.html`
  - `build/reports/allure/firestore/index.html`
  - `build/reports/allure/all/index.html`
- Kover HTML coverage: `build/reports/kover/html/index.html`
- Kover XML coverage: `build/reports/kover/report.xml` (also published to
  SonarCloud — see [sonarcloud](sonarcloud.md))

## Published reports (GitHub Pages)

The same Allure and Kover HTML is published to GitHub Pages so you can browse it
without downloading CI artifacts: **https://georgeci.github.io/MoneySurfer/**

| Path | Contents | Published by | When |
|---|---|---|---|
| [`/allure/`](https://georgeci.github.io/MoneySurfer/allure/) | Allure — `common` + `firestore` scopes, with history | `ci.yml` → `publish` | push to `main` |
| [`/kover/`](https://georgeci.github.io/MoneySurfer/kover/) | Kover coverage (`common` scope) | `ci.yml` → `publish` | push to `main` |
| [`/nightly/allure/`](https://georgeci.github.io/MoneySurfer/nightly/allure/) | Allure — all five scopes (incl. Maestro Android/iOS, offline) | `nightly.yml` → `nightly-publish` | nightly cron + `workflow_dispatch` |

Both jobs push to the `gh-pages` branch with `keep_files: true`, so the per-push
`/allure/` + `/kover/` subtrees and the `/nightly/` subtree update independently.
PRs don't publish — they attach the aggregated Allure as a downloadable artifact
(`allure-report-all`) instead.

## Manual CLI Equivalent

```bash
maestro test scripts/maestro/ --exclude-tags setup --format junit --output build/test-results/maestro/maestro-report.xml
allure generate build -o build/reports/allure/maestro --clean
```
<!-- AI:END -->
