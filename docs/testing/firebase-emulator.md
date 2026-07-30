# Firebase Emulator Suite

<!-- DOCS:TOC -->
## Contents
- [Firebase Emulator Suite](#firebase-emulator-suite)
- [TL;DR for agents](#tldr-for-agents)
- [Install](#install)
- [Run, reset, stop](#run-reset-stop)
  - [Wipe state between runs](#wipe-state-between-runs)
- [Configuration](#configuration)
  - [firebase.json](#firebasejson)
  - [.firebaserc](#firebaserc)
  - [--project demo-moneysurfer for emulator runs](#--project-demo-moneysurfer-for-emulator-runs)
  - [firestore.rules](#firestorerules)
  - [firestore.indexes.json](#firestoreindexesjson)
- [Switching the app to the emulator](#switching-the-app-to-the-emulator)
  - [Desktop / JVM](#desktop--jvm)
  - [Android (debug build from terminal)](#android-debug-build-from-terminal)
  - [iOS](#ios)
  - [Tests](#tests)
  - [What the switch does technically](#what-the-switch-does-technically)
  - [Safety against accidental emulator-in-prod](#safety-against-accidental-emulator-in-prod)
- [Test fixtures and tagging](#test-fixtures-and-tagging)
  - [Default vs emulator-tagged tests](#default-vs-emulator-tagged-tests)
  - [:data-test-fixtures](#data-test-fixtures)
  - [EmulatorTag + Gradle task](#emulatortag--gradle-task)
  - [:integration-test](#integration-test)
- [Maestro against the emulator](#maestro-against-the-emulator)
  - [Build flag](#build-flag)
  - [Wiring through MoneySurferApplication](#wiring-through-moneysurferapplication)
  - [Gradle tasks](#gradle-tasks)
  - [Seed fixtures](#seed-fixtures)
- [JVM Firebase bootstrap](#jvm-firebase-bootstrap)
- [Troubleshooting](#troubleshooting)
  - [Port 8080 is not open on 0.0.0.0](#port-8080-is-not-open-on-0000)
  - [MSUSEEMULATOR=true does not work in Android Studio Run config](#msuseemulatortrue-does-not-work-in-android-studio-run-config)
  - [Could not reach Firestore emulator at 10.0.2.2:8080](#could-not-reach-firestore-emulator-at-100228080)
  - [firebase-tools no longer supports Java version before 21](#firebase-tools-no-longer-supports-java-version-before-21)
  - [PERMISSIONDENIED: Missing or insufficient permissions locally](#permissiondenied-missing-or-insufficient-permissions-locally)
  - [Emulator re-downloads on every start](#emulator-re-downloads-on-every-start)
  - [CI: Connection refused](#ci-connection-refused)
  - [firebase emulators:exec hangs after the test](#firebase-emulatorsexec-hangs-after-the-test)
- [Cheat sheet](#cheat-sheet)
<!-- DOCS:END -->

## TL;DR for agents

- Local stand-in for Firebase Auth + Firestore. Used for `:data` tests against the real Firestore protocol, `:integration-test` Room round-trips, and Maestro Android/iOS flows on seeded state.
- Production builds never reach the emulator — switching is gated by the per-platform `MS_USE_EMULATOR` env / system property + `BuildConfig.USE_EMULATOR` flag.
- Same `firestore.rules` are used in emulator and prod, so tests catch rules regressions alongside code regressions.
- All emulator surfaces share `--project demo-moneysurfer` — no real GCP project, no auth checks.
- JVM **can** drive the gitlive Firebase SDK, but only after `initializeDesktopFirebase()` builds the default `FirebaseApp` by hand — see [JVM Firebase bootstrap](#jvm-firebase-bootstrap). JVM test suites do not currently do this; they use REST.

READ WHEN:
- adding tests that need real Firestore protocol semantics
- enabling the emulator on a new platform
- editing `scripts/firebase/*`, `qaMaestroAndroid`, or `qaMaestroIos`
- debugging emulator boot or test connectivity
- reviewing the JVM Firebase bootstrap when planning new test suites

Related: [testing-strategy](testing-strategy.md), [persistence](../architecture/persistence.md), [app-version-gate](../architecture/app-version-gate.md), [firestore-rules-bugs](../architecture/firestore-rules-bugs.md).

<!-- AI:SECTION id=emulator-install task=testing,emulator,setup -->
## Install

```bash
brew install firebase-cli      # or: npm i -g firebase-tools
firebase --version             # verify
```

CI: pull `firebase-tools` via a Node action / Docker image.
<!-- AI:END -->

<!-- AI:SECTION id=emulator-run task=testing,emulator,scripts -->
## Run, reset, stop

Foreground (Ctrl-C to stop):

```bash
scripts/firebase/start.sh
```

Background (PID in `build/firebase-emulator.pid`, log in `build/firebase-emulator.log`):

```bash
scripts/firebase/start.sh --background
scripts/firebase/stop.sh
```

After start:

| Component       | Address                  |
|-----------------|--------------------------|
| Authentication  | `localhost:9099`         |
| Firestore       | `localhost:8080`         |
| UI              | `http://localhost:4000`  |

The UI shows Auth users, Firestore docs, request log + rules logs in real time. Cold boot takes 5–15 s on first run (CLI downloads emulator JARs into `~/.cache/firebase/emulators/`).

### Wipe state between runs

```bash
scripts/firebase/reset.sh
```

REST calls underneath:
- `DELETE /emulator/v1/projects/{pid}/accounts` — drops all Auth users.
- `DELETE /emulator/v1/projects/{pid}/databases/(default)/documents` — drops all Firestore docs.

Faster than restarting the process. Security rules reload automatically when `firestore.rules` changes on disk.
<!-- AI:END -->

<!-- AI:SECTION id=emulator-config task=testing,emulator,config -->
## Configuration

### `firebase.json`

Emulator config + paths to rules / indexes. `singleProjectMode: true` so the CLI does not require a project alias on every start.

### `.firebaserc`

Aliases for real GCP projects:
- `default` / `dev` → `moneysurfer-dev`
- `prod` → `moneysurfer-release`

There is no `test` alias — test suites only target the emulator; there has never been a real `moneysurfer-test` GCP project.

### `--project demo-moneysurfer` for emulator runs

Emulator runs **never** use the aliases above. Every Gradle wrapper and npm script pins `--project demo-moneysurfer` directly. The `demo-` prefix activates Firebase "demo project mode" — no real project required, auth checks skipped. The same projectId is shared across `firestore-tests/` (mocha rules suite), `:integration-test` (`EmulatorEnv.EMULATOR_PROJECT_ID`), `qaMaestroAndroid`, and `qaMaestroIos`. All test surfaces hit one emulator namespace.

### `firestore.rules`

Same file used in production. The emulator hot-reloads on save. This is **deliberate** — tests should catch regressions in rules together with regressions in code.

### `firestore.indexes.json`

The emulator does not require composite indexes (queries work without them). The file exists so `firebase deploy` against the real project picks up the same set.
<!-- AI:END -->

<!-- AI:SECTION id=emulator-switch task=testing,emulator,configuration,build -->
## Switching the app to the emulator

A per-platform flag `MS_USE_EMULATOR` drives a single `FirebaseConfig.useEmulator` toggle.

### Desktop / JVM

```bash
MS_USE_EMULATOR=true ./gradlew :composeApp:run
```

> This is the zero-setup desktop flow: the emulator branch supplies demo-project Firebase options, so no credentials are needed. Running desktop **without** `MS_USE_EMULATOR` requires `MS_FIREBASE_APP_ID`, `MS_FIREBASE_API_KEY` and `MS_FIREBASE_PROJECT_ID` in the environment — desktop has no `google-services.json`. See [JVM Firebase bootstrap](#jvm-firebase-bootstrap).

### Android (debug build from terminal)

```bash
MS_USE_EMULATOR=true ./gradlew :androidApp:installDebug
adb shell am start -n com.georgeci.moneysurfer/.MainActivity
```

> Android emulator host is `10.0.2.2`, not `localhost`. Already wired in `data/src/androidMain/kotlin/com/georgeci/moneysurfer/data/remote/FirebaseConfigImpl.android.kt`. Real device over USB/Wi-Fi needs the LAN IP — out of scope for now.

> Running from Android Studio does not inherit shell env vars. Use the productflavor route (Maestro pipeline) or set the env var inside the Run config.

### iOS

```bash
./gradlew maestroBuildIosSimulator
```

iOS Simulator host is `localhost` — works directly. The Maestro build injects
`MS_USE_EMULATOR=YES` into `Info.plist`; `iOSApp.swift` configures Firebase with
demo options when that flag is present, so a local `GoogleService-Info.plist` is
not required for emulator runs.

### Tests

Tests do not need `MS_USE_EMULATOR`. The test `FirebaseConfig` is bound directly via Koin / harness setup.

### What the switch does technically

`DataModule` factories `firebaseAuth` / `firebaseFirestore` call `useEmulator(host, port)` BEFORE the first SDK read/write — exactly when Koin lazy-instantiates the single. Once set, the SDK is locked to the emulator for the lifetime of the process.

```kotlin
@Single
fun firebaseFirestore(config: FirebaseConfig): FirebaseFirestore = Firebase.firestore.also {
    if (config.useEmulator) it.useEmulator(config.emulatorHost, config.firestorePort)
}
```

Production binding does not see the env var → `useEmulator = false` → branch never taken → SDK targets real Firestore.

### Safety against accidental emulator-in-prod

1. `FirebaseConfigImpl.defaultUseEmulator()` reads only the env var / platform test flag. Production release builds never have that env var set (Play Store / App Store do not propagate shell env).
2. Productflavor (Maestro pipeline): Android `maestroAssembleDebug` sets `BuildConfig.USE_EMULATOR = true`; iOS `maestroBuildIosSimulator` sets `MS_USE_EMULATOR=YES` only for Debug simulator builds.
3. Same `firestore.rules` in emulator and prod. If a test passes against non-prod rules in the emulator, that is a config bug, not an emulator bug.
<!-- AI:END -->

<!-- AI:SECTION id=emulator-fixtures task=testing,emulator,kotest,gradle -->
## Test fixtures and tagging

### Default vs emulator-tagged tests

`:data:jvmTest` does **not** require the emulator — emulator-tagged tests are excluded automatically. To run only the emulator-tagged ones:

Locally — start the emulator first:

```bash
scripts/firebase/start.sh --background
./gradlew :data:emulatorTest
scripts/firebase/stop.sh
```

Hermetic (start, run, tear down):

```bash
firebase emulators:exec --project demo-moneysurfer \
    "./gradlew :data:emulatorTest"
```

### `:data-test-fixtures`

Reusable test fixtures:

- `EmulatorFirebaseConfig` (`commonMain`) — `FirebaseConfig` impl with static `useEmulator = true`, default ports (9099/8080), and projectId `demo-moneysurfer` (shared across all test surfaces).
- `EmulatorClient` (`jvmMain`) — REST client over `java.net.HttpURLConnection`:
  - `isReady()` / `waitUntilReady(timeout)` — pings both endpoints.
  - `reset()` — `DELETE /accounts` + `DELETE /documents` for state cleanup between tests.
- `ensureEmulatorReady(client)` — Kotest `beforeSpec` hook with a clear failure message if the emulator is not running.

### `EmulatorTag` + Gradle task

`EmulatorTag` (`data/src/jvmTest/.../emulator/EmulatorTag.kt`) is `NamedTag("emulator")`. Used via Kotest annotation:

```kotlin
@Tags("emulator")
class FooEmulatorSpec : StringSpec({
    val client = EmulatorClient()
    beforeSpec { ensureEmulatorReady(client) }
    beforeEach { client.reset() }

    "..." { ... }
})
```

Kotest 6.x's built-in `SystemPropertyOrEnvTagExtension` reads `kotest.tags.include` / `kotest.tags.exclude`. `data/build.gradle.kts` splits the tasks:

| Task                  | System property                | Behaviour                                       |
|-----------------------|--------------------------------|-------------------------------------------------|
| `:data:jvmTest`       | `kotest.tags.exclude=emulator` | Unit tests; no emulator required.               |
| `:data:emulatorTest`  | `kotest.tags.include=emulator` | Only emulator-tagged tests; needs the emulator. |

### `:integration-test`

JVM-only KMP module (`jvm()` only) for real-impl integration tests (Room + `:data` + `:sync` + `:domain`), without Firestore/Auth on JVM. `IntegrationHarness` brings up an in-memory `MoneySurferDatabase` (real Room schema) plus DAO accessors. `PendingMutationQueueIntegrationIT` validates the outbox state machine.

```bash
./gradlew :integration-test:integrationTest
```

(Alias for `:integration-test:jvmTest`.)

The emulator is not required for `:integration-test` — Phase 3 covers Room only, see the JVM Firebase bootstrap below.
<!-- AI:END -->

<!-- AI:SECTION id=emulator-maestro task=testing,emulator,maestro,android,ios -->
## Maestro against the emulator

End-to-end coverage of user flows: real APK on Android emulator/device or real
iOS Simulator app → platform Firebase SDK → Firebase Emulator Suite. Bypasses
the JVM gap because the code runs on Android/iOS, not on the JVM host.

### Build flag

`androidApp/build.gradle.kts` generates `BuildConfig.USE_EMULATOR` in `com.georgeci.moneysurfer`:

```kotlin
defaultConfig {
    buildConfigField(
        "boolean",
        "USE_EMULATOR",
        (project.findProperty("useEmulator") == "true").toString(),
    )
}
buildFeatures { buildConfig = true }
```

Build:

```bash
./gradlew maestroAssembleDebug       # Android APK with USE_EMULATOR=true
./gradlew maestroBuildIosSimulator   # iOS Debug simulator app with MS_USE_EMULATOR=YES
```

### Wiring through `MoneySurferApplication`

`MoneySurferApplication.onCreate` reads `BuildConfig.USE_EMULATOR` BEFORE `initKoin` and sets the system property `MS_USE_EMULATOR=true`:

```kotlin
if (BuildConfig.USE_EMULATOR) {
    System.setProperty("MS_USE_EMULATOR", "true")
}
initKoin { ... }
```

`FirebaseConfigImpl.android` reads both branches:
1. System property `MS_USE_EMULATOR` (set by `MoneySurferApplication`).
2. Env var `MS_USE_EMULATOR` (for tests / direct gradle runs without Application).

### Gradle tasks

In `gradle/qa.gradle.kts`:

| Task                              | Purpose                                                                                                    |
|-----------------------------------|------------------------------------------------------------------------------------------------------------|
| `maestroAssembleDebug`            | Builds the Android APK with `BuildConfig.USE_EMULATOR=true`.                                               |
| `maestroInstallDebug`             | Builds the Android emulator APK + `adb install -r`.                                                        |
| `qaMaestroAndroid` / `qaMaestro`   | Full Android pipeline: install APK → `firebase emulators:exec` → seed → `maestro test` → tear down.         |
| `maestroBuildIosSimulator`        | Builds iOS Debug simulator app with `MS_USE_EMULATOR=YES`; default simulator name is `iPhone 17`.          |
| `maestroInstallIosSimulator`      | Builds and installs the iOS simulator app on the booted simulator.                                         |
| `qaMaestroIos`                    | Full iOS pipeline: install app → `firebase emulators:exec` → seed → `maestro test --platform ios` → tear down. |

```bash
./gradlew qaMaestroAndroid
./gradlew qaMaestroIos -PiosSimulatorName="iPhone 17" -PiosSimulatorUdid=<udid>
```

### Seed fixtures

`scripts/firebase/seed.sh` — REST helpers to bootstrap state before flows:
- `signup_user(email, password)` via `accounts:signUp` REST endpoint, idempotent.
- Baseline users: `e2e+owner@test.local`, `e2e+viewer@test.local` (password `password`).

Wired in hooks:

```bash
firebase emulators:exec --project demo-moneysurfer --only auth,firestore '
    scripts/firebase/seed.sh && \
    maestro test scripts/maestro/
'
```

> `appConfig/mobile` is **not** seeded over REST — Firestore rules block client-side writes to that document. The current static `hasValidClientVersion() >= 1` rule does not depend on the doc (DTO defaults of 1 satisfy it), so seeding is unnecessary today. If the rule turns dynamic (reading `appConfig/mobile.minSupportedAppVersionCode` via `get()`), seed `appConfig` through Admin SDK or Firestore emulator startup data.
<!-- AI:END -->

<!-- AI:SECTION id=emulator-jvm-bootstrap task=testing,emulator,jvm,gitlive -->
## JVM Firebase bootstrap

gitlive's `firebase-*-jvm` artifacts are Android sources repackaged — they import `android.content.Context` and cast to it:

```kotlin
public actual fun Firebase.initialize(context: Any?, options: FirebaseOptions): FirebaseApp =
    FirebaseApp(com.google.firebase.FirebaseApp.initializeApp(context as Context, options.toAndroid()))
```

That works on the JVM because `dev.gitlive:firebase-java-sdk` (pulled in transitively on the `jvm` target) ships JVM stand-ins for `android.content.Context`, `android.app.Application`, and the `com.google.firebase.*` classes. What it does **not** ship is a default `FirebaseApp`: there is no `google-services.json` equivalent on desktop, so `Firebase.firestore` throws `Default FirebaseApp is not initialized in this process` until one is built by hand.

`initializeDesktopFirebase()` in [FirebaseBootstrap.jvm.kt](../../data-remote/src/jvmMain/kotlin/com/georgeci/moneysurfer/data/remote/FirebaseBootstrap.jvm.kt) does that, and the desktop host calls it before `initKoin`. Three things have to be right:

1. **`FirebasePlatform.initializeFirebasePlatform(...)` first.** The java-sdk routes all persistence (installation id, refresh tokens) and logging through this hook and has no default. MoneySurfer backs it with a properties file in the app-data directory.
2. **Pass `android.app.Application`, not `android.content.Context`.** Firestore's `ComponentProvider` casts down to `Application` to register lifecycle callbacks; a plain `Context` panics its async queue with a `ClassCastException` on the first read.
3. **`getDatabasePath(name)` must return a *file* path whose parent exists.** Creating the returned path as a directory makes SQLite fail with `SQLITE_CANTOPEN`, which also panics the async queue.

Options come from the same switch as everything else: `MS_USE_EMULATOR=true` yields demo-project options (39-char dummy API key, same shape as the iOS host — see issue #219), with the project id following `FIREBASE_PROJECT_ID` when set so it tracks whatever `scripts/firebase/start.sh` booted; otherwise `MS_FIREBASE_APP_ID` / `MS_FIREBASE_API_KEY` / `MS_FIREBASE_PROJECT_ID` are read from the environment, since real credentials stay out of the repo.

**Current state of JVM test suites:**

- `:integration-test` is still limited to the Room layer — it has not been rewired onto this bootstrap.
- `EmulatorClient` in `:data-test-fixtures` still uses the emulator's REST endpoints for reset / seed, which need no SDK at all:
  - `POST|GET /v1/projects/{pid}/databases/(default)/documents/{path}` for writes / reads.
  - `POST /identitytoolkit.googleapis.com/v1/accounts:signInWithPassword` for Auth (port 9099).
- End-to-end user-flow coverage still comes from Maestro on Android/iOS. Driving the real SDK from `jvmTest` is now possible via `initializeDesktopFirebase()`, but no suite does it yet.
<!-- AI:END -->

<!-- AI:SECTION id=emulator-troubleshooting task=testing,emulator,troubleshooting -->
## Troubleshooting

### `Port 8080 is not open on 0.0.0.0`

A previous emulator instance was not killed.

```bash
lsof -i :8080 -i :9099 -i :4000
kill <PID>
```

### `MS_USE_EMULATOR=true` does not work in Android Studio Run config

AS does not inherit shell env vars. Options:

- Run `./gradlew :androidApp:installDebug` from a terminal with the env set.
- Or AS → Run config → Environment Variables → add `MS_USE_EMULATOR=true`.
- Or use `emulatorDebug` productflavor without env-var dance (Maestro pipeline path).

### `Could not reach Firestore emulator at 10.0.2.2:8080`

The Android emulator cannot reach the host:

- Confirm both the app emulator and Firebase emulator are running. `start.sh --background` reports a PID even when the CLI dies a second later — check `build/firebase-emulator.log`, and see the JDK entry below.
- AVD uses `10.0.2.2`; Genymotion uses `10.0.3.2`; real device uses the LAN IP of the host.
- Recent `firebase-tools` bind the emulators to `127.0.0.1`, which `10.0.2.2` does not reach. The app then fails every call with `Failed to connect to /10.0.2.2:9099` while `curl localhost:9099` from the host looks fine. Give each emulator a host in the config it is started with:

  ```json
  "emulators": {
    "firestore": { "port": 8080, "host": "0.0.0.0" },
    "auth": { "port": 9099, "host": "0.0.0.0" }
  }
  ```

  `firebase.json` in the repo deliberately does **not** set this — it would publish the emulator on every interface of the machine. Keep the override in a scratch config and point the CLI at it: `firebase emulators:start --config /tmp/firebase.host.json` (paths inside that file must be absolute).

### `firebase-tools no longer supports Java version before 21`

The CLI exits immediately with this and nothing is listening. `scripts/firebase/start.sh --background` still writes a PID file, so the failure looks like a connectivity problem from the app's side. Put a 21+ JDK on `PATH` for the emulator process:

```bash
export JAVA_HOME="$(/usr/libexec/java_home -v 21)"; export PATH="$JAVA_HOME/bin:$PATH"
```

Same requirement as `firestore-tests` — see [qa-runbook](qa-runbook.md).

### `PERMISSION_DENIED: Missing or insufficient permissions` locally

Rules failure. The UI at `http://localhost:4000/firestore/rules` shows which rule denied. Common checks:

- Does `appConfig/mobile` exist? If a rule depends on it (would require dynamic `hasValidClientVersion()` — see [firestore-rules-bugs.md](../architecture/firestore-rules-bugs.md) #1), all writes fail without it.
- Is `users/{uid}/members/{uid}` present? Subcollection writes need the member-row first.

### Emulator re-downloads on every start

CLI caches JARs in `~/.cache/firebase/emulators/`. If it re-downloads on every start, check directory permissions.

### CI: `Connection refused`

`firebase emulators:exec` started the emulator, but Gradle tests connect before it is ready. Solutions:

- Ping the emulator with retry in the harness before tests run.
- `--test-config` with a warmup timeout.

### `firebase emulators:exec` hangs after the test

Java emulator processes sometimes survive `exec`. CI workaround:

```bash
trap 'pkill -f "firebase emulators" || true' EXIT
firebase emulators:exec ...
```
<!-- AI:END -->

## Cheat sheet

Start:

```bash
scripts/firebase/start.sh                    # foreground
scripts/firebase/start.sh --background       # background
```

Stop:

```bash
scripts/firebase/stop.sh                     # gracefully
pkill -f "firebase emulators"                # nuke
```

Reset state without restart:

```bash
scripts/firebase/reset.sh
```

Run tests with the emulator (one command):

```bash
firebase emulators:exec --project demo-moneysurfer \
    "./gradlew :data:emulatorTest"
```

UI:

```bash
open http://localhost:4000
```

Logs:

```bash
tail -f build/firebase-emulator.log               # if started with --background
tail -f firebase-debug.log firestore-debug.log    # CLI logs
```
