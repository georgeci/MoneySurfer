# Testing Strategy

<!-- DOCS:TOC -->
## Contents
- [Testing Strategy](#testing-strategy)
- [TL;DR for agents](#tldr-for-agents)
- [Test layers](#test-layers)
- [Common Commands](#common-commands)
- [QA Entry Points](#qa-entry-points)
  - [Offline golden-path E2E](#offline-golden-path-e2e)
  - [iOS E2E scope (issue #297)](#ios-e2e-scope-issue-297)
- [Desktop UI tests (Compose, jvmTest)](#desktop-ui-tests-compose-jvmtest)
- [Test tags (Compose ↔ Maestro)](#test-tags-compose--maestro)
- [Rules](#rules)
<!-- DOCS:END -->

The entry point for everything about testing MoneySurfer: which layers exist,
which one a given change belongs in, and how to run them. Operational detail —
tool install, per-scope QA tasks, report and artifact paths — lives in the
[QA runbook](qa-runbook.md).

## TL;DR for agents

- Do not run broad builds by default.
- Pick the narrowest validation that covers the edited module.
- Device integration tests need Firebase Emulator Suite and an Android device/emulator.
- Read this before adding tests or choosing QA commands.

READ WHEN:
- adding tests
- choosing validation
- changing sync or persistence
- touching Android UI flows
- adding Compose desktop UI tests

<!-- AI:SECTION id=testing-strategy task=testing,qa,validation -->
## Test layers

| Layer | Where | Needs | What it covers |
|---|---|---|---|
| Unit | `commonTest`, `jvmTest`, `androidHostTest` | nothing | Pure logic: domain rules, mappers, ViewModel state. kotest `StringSpec`. |
| Desktop UI | `:composeApp:jvmTest` | nothing (headless) | Screen logic through the semantics tree — see [below](#desktop-ui-tests-compose-jvmtest). |
| Screenshot | `:uikit` `androidHostTest` | nothing (Robolectric) | Visual regression of design-system components — see [screenshot-tests](screenshot-tests.md). |
| Integration | `:integration-test` `jvmTest` | nothing | Room round-trips across domain → data. |
| Device integration | `:integration-test` `connectedAndroidDeviceTest` | Android device + Firebase emulator | Real Firebase SDK against Firestore/Auth emulators. |
| Firestore rules | `firestore-tests/` | Node + JDK 21 | Security rules, per-role access. Mocha, not Gradle. |
| E2E | `scripts/maestro/` | device/simulator + emulator | Full user journeys on Android and iOS. |

Pick the cheapest layer that can catch the regression. A rule of thumb: if it
can be a unit test, it should be; reach for a device or Maestro layer only when
the behaviour genuinely depends on the platform.

## Common Commands

```bash
./gradlew :moduleName:compileCommonMainKotlinMetadata
./gradlew :moduleName:compileKotlinJvm
./gradlew :moduleName:testDebugUnitTest
./gradlew :moduleName:jvmTest
./gradlew test
```

## QA Entry Points

```bash
./gradlew qaCommon
./gradlew qaAndroidHost
./gradlew qaAndroidDevice
./gradlew qaMaestro
./gradlew qaJvmAndAndroid
```

`qaJvmAndAndroid` aggregates JVM, Android host, and Android device scopes. It
does not run Maestro or Firestore-rules tests. `qaAll` remains only as a
deprecated compatibility alias for the same scope.

`qaCommon` and `qaAndroidHost` discover test-owning modules from their
`commonTest`, `jvmTest`, and `androidHostTest` source directories. Adding a test
source set therefore opts the module into the aggregate without updating a
separate module list.

### Offline golden-path E2E

`scripts/maestro/offline/offline-golden.yaml` (tagged `offline`) is the
end-to-end gate for the offline MVP: first launch → currency picker → seed
verified → create transaction → balance updates → Settings (no Backup/Sync/
Logout rows). It drives the `:androidApp-offline` / `:iosAppOffline` binary,
which makes zero network calls — so it needs **no Firebase emulator and no
seeded users**. The online `qaMaestro*` suites skip it via `--exclude-tags
offline`.

```bash
# Android — needs a booted emulator/device:
./gradlew qaMaestroOfflineAndroid

# iOS — needs a booted Simulator. Runs the launch smoke, not the golden path:
./gradlew qaMaestroOfflineIos
```

### iOS E2E scope (issue #297)

Both iOS entry points — `qaMaestroIos` (online) and `qaMaestroOfflineIos`
(offline) — currently run one flow, `scripts/maestro/ios/app-open.yaml`: launch
with `clearState` and assert onboarding renders. The suites were
non-deterministically red on iOS, so they were cut back to the signal worth
acting on; nothing was deleted, and Android still runs both the online suite and
the offline golden path in full. Use `maestroRunAllIos` to drive the whole suite
on a simulator locally. See [qa-runbook.md](qa-runbook.md#ios-scope-launch-smoke-only-issue-297).

## Desktop UI tests (Compose, `jvmTest`)

Screen-state UI tests run in-process on the JVM through
`androidx.compose.ui.test.v2.runComposeUiTest`, inside ordinary kotest
`StringSpec` blocks — no JUnit rule, no `kotlin.test` carve-out. They render
**headless** (offscreen Skiko), so they need no display and no `xvfb`, and they
ride along in `qaCommon` (→ `testCommon` → `:composeApp:jvmTest`) with zero
extra CI wiring. Kover and Allure pick them up automatically.

```bash
./gradlew :composeApp:jvmTest
```

Rules for writing them:

- Mount the screen's **stateless content composable** with an injected state —
  not the `*Screen()` entry point, which resolves its ViewModel via
  `koinViewModel()`. Publish that composable if it is still `private`
  (`SignInContent` is the reference).
- Address nodes through the screen's `*TestTags` object (see below) — the same
  constants Maestro uses. Never match on localized text.
- CMP 1.11 defaults to `StandardTestDispatcher`: coroutines launched in
  composition do **not** run eagerly. Use `waitForIdle()` / `waitUntil {}`
  after anything that emits asynchronously.
- `jvmTest` forces `java.awt.headless=true`, so a test that accidentally needs
  a display fails locally instead of only on CI.

Reference implementation:
`composeApp/src/jvmTest/.../ui/SignInScreenStateTest.kt`. Rollout status and
the follow-up steps (gray-box journeys, Roborazzi screenshots, packaging
smoke) live in
[docs/plans/jvm-desktop-testing-rollout.md](../plans/jvm-desktop-testing-rollout.md).

## Test tags (Compose ↔ Maestro)

Anchor Maestro selectors and desktop UI tests to stable identifiers, not
localized text. Compose `Modifier.testTag(...)` is the source of truth —
Maestro reaches it through the Android resource-id bridge, and
`runComposeUiTest` reads it straight off the semantics tree.

**Author screens like this:**

1. Declare tag constants in a `*TestTags` object next to the screen
   (public, top-level — referenced from both production and tests).
   Use a `screen:element` namespace (e.g. `signIn:submit`) to keep them
   greppable.
2. Apply `Modifier.testTag(...)` on every node a test or Maestro flow
   needs to find: root, fields, primary actions, error/loader nodes.
3. On the screen root, attach `Modifier.surferTestTagAsId()`
   (from `uikit/.../modifier/SurferTestTagAsId.kt`). This enables
   `semantics { testTagsAsResourceId = true }` on Android and is a
   no-op on iOS/JVM. Without it Maestro cannot match by `id:`.

Reference implementation: `feature/login/.../SignInScreen.kt::SignInTestTags`
and `scripts/maestro/00_login.yaml` / `01_auth_signin.yaml`.

**Maestro selector style:**

```yaml
- tapOn:
    id: "signIn:submit"        # preferred — stable across locales/themes
- assertVisible:
    text: "Choose a workspace" # acceptable for screens not yet tagged
```

Prefer `id:` for everything inside a tagged screen. Fall back to `text:`
only when the target screen has no tags yet (track that as follow-up
work — don't sprinkle text matchers permanently).

## Rules

- Use module tests for narrow changes.
- Use integration tests for persistence/sync behavior spanning modules.
- Use Firestore rules tests for security rules changes — they live in
  [`firestore-tests/`](../../firestore-tests/) (Mocha +
  `@firebase/rules-unit-testing`; `cd firestore-tests && npm test`, boots its
  own Firestore emulator, needs JDK 21+ on PATH for firebase-tools). Any
  change to `firestore.rules` **or** to the wire shape clients write (push
  DTOs, tombstone patch) needs coverage there — device ITs exercise rules
  only incidentally.
- Changing a `:uikit` component re-renders its committed reference screenshots.
  `./gradlew qaAndroidHost` fails on the visual diff; re-record and commit the
  PNGs in the same commit as the UI change — see
  [Screenshot tests (Roborazzi)](screenshot-tests.md).
<!-- AI:END -->
