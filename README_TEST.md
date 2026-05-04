# QA: Tests + Kover + Allure (common, Android host, Android device, Maestro Android/iOS, Firestore rules)

All QA scopes follow one shape: `qa<Scope>` runs the scope's tests, attaches Kover where it applies, and **always** finalizes by generating an Allure report into a per-scope directory under `build/reports/allure/<scope>/`. Allure runs even when tests fail.

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

## Firebase bootstrap (emulator)

Bootstrap scripts live in `scripts/firebase/`:

```bash
# Start emulator (foreground)
scripts/firebase/start.sh

# Start emulator (background)
scripts/firebase/start.sh --background

# Reset emulator state (Auth users + Firestore docs)
scripts/firebase/reset.sh

# Seed test users for Maestro/device IT
scripts/firebase/seed.sh

# Stop background emulator
scripts/firebase/stop.sh
```

Project id note:

- `qaIntegrationDeviceEmulator` / `qaIntegrationDeviceHermetic` and `firestore-tests` use `demo-moneysurfer`.
- `scripts/firebase/start.sh` / `reset.sh` default to `moneysurfer-test` unless `FIREBASE_PROJECT_ID` is set.

If you need one shared namespace for all Firebase test flows, run bootstrap scripts with:

```bash
FIREBASE_PROJECT_ID=demo-moneysurfer scripts/firebase/start.sh
```

## QA tasks

| Task | Tests | Kover | Allure output |
|---|---|---|---|
| `./gradlew qaCommon` | `testCommon` (JVM) | yes | `build/reports/allure/common/` |
| `./gradlew qaAndroidHost` | `testAndroidHost` | yes | `build/reports/allure/android-host/` |
| `./gradlew qaAndroidDevice` | `testAndroidDevice` (instrumented) | — | `build/reports/allure/android-device/` |
| `./gradlew qaMaestroAndroid` / `qaMaestro` | Android flows via `firebase emulators:exec` + seed | — | `build/reports/allure/maestro/` |
| `./gradlew qaMaestroIos` | iOS Simulator flows via `firebase emulators:exec` + seed | — | `build/reports/allure/maestro-ios/` |
| `./gradlew qaFirestoreRules` | Mocha (`firestore-tests/`) via `firebase emulators:exec --only firestore` | — | `build/reports/allure/firestore/` |
| `./gradlew qaAll` | `testAllScopes` (common + Android host + Android device; no Maestro/Firestore-rules run) | yes | `build/reports/allure/all/` |

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

The managed AVD is declared in [integration-test/build.gradle.kts](integration-test/build.gradle.kts)
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
[.github/workflows/ci.yml](.github/workflows/ci.yml).

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
- Kover XML coverage: `build/reports/kover/report.xml`

TODO: Publish the generated Allure HTML report in GitHub without requiring artifact download.
Candidate: GitHub Pages from `build/reports/allure/common/` on protected-branch
pushes, once repository Pages policy is decided.

## Manual CLI Equivalent

```bash
maestro test scripts/maestro/ --exclude-tags setup --format junit --output build/test-results/maestro/maestro-report.xml
allure generate build -o build/reports/allure/maestro --clean
```
