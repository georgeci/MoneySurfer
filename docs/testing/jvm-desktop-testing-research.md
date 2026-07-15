# JVM (Desktop) Testing Research

*Research date: 2026-07-15. Stack at time of writing: Kotlin 2.4.0, Compose Multiplatform 1.11.1, Kotest 6.2.1, Koin 4.2.2, Room 2.8.4.*

## 1. Where we are today

The desktop app is `:composeApp` (`jvmMain/main.kt` → `initKoin(onlineWiring)` → `application { Window { App() } }`, packaged via `compose.desktop.application` as Dmg/Msi/Deb). `:composeAppOffline` has a `jvm()` target for tests only (no desktop binary).

What already exists on the JVM side:

- **Unit tests** — kotest `StringSpec` in `commonTest`/`jvmTest` across `domain`, `sync/*`, `feature/*`, `utils` (JUnit Platform runner, Turbine, fixture-monkey).
- **DI verification** — Koin graph tests in `composeApp/src/jvmTest` and `composeAppOffline/src/jvmTest` (compile-safety is off, verified at test time).
- **Hermetic integration tests** — `:integration-test` (14 ITs) with `InMemoryRoomDatabase` + `IntegrationHarness`; repository/sync flows run fully on JVM.
- **CI** — `qaCommon` runs `jvmTest` for 10 modules (incl. `:composeApp`) on `ubuntu-latest` with Kover + Allure.

What does **not** exist:

- No Compose UI tests anywhere (zero usages of `runComposeUiTest` / `createComposeRule` / `compose.uiTest`).
- No screenshot/visual-regression tests.
- No desktop E2E — the 16 Maestro flows in `scripts/maestro/` target Android/iOS app ids only; Maestro has no desktop support.
- CI never builds or launches the desktop binary (`packageDmg`/`runDistributable` are not exercised).

The good news: the hard prerequisites are already in place — `jvmTest` wiring, test tags (`uikit/.../SurferTestTagAsId` with a JVM actual), an in-memory Room seam, and Koin `extraModules` override points in `main.kt`.

## 2. The testing options for Compose Desktop (state of the art)

### 2.1 Compose UI tests in-process (recommended core)

Compose Multiplatform ships a first-party UI-test API; CMP 1.11 promoted the cross-platform variant to **`runComposeUiTest` v2** (`androidx.compose.ui.test.v2.runComposeUiTest`), which:

- needs **no JUnit `@get:Rule`** — it is a plain blocking top-level function, so tests can live in `commonTest` or `jvmTest`;
- uses the same finder/assertion/action API as Jetpack Compose (`onNodeWithTag`, `performClick`, `assertTextEquals`, …) against the semantics tree — no real window is opened, rendering is offscreen via Skiko;
- since CMP 1.11 defaults to **`StandardTestDispatcher`**: coroutines launched in composition no longer run eagerly — use `waitForIdle()` / `waitUntil {}` or advance the scheduler explicitly (this is the main migration gotcha reported in the field);
- is still `@ExperimentalTestApi`.

Setup (per the official docs, versions match our catalog):

```kotlin
kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(compose.uiTest) // org.jetbrains.compose.ui:ui-test:1.11.1
        }
        // :composeApp jvmMain already has compose.desktop.currentOs
    }
}
```

Tests then run under the existing `./gradlew :composeApp:jvmTest` — i.e. they slot straight into `qaCommon`. Feedback loop is the fastest of all CMP targets (tens of seconds on a warm build).

There is also an older JUnit4-rule API (`compose.desktop.uiTestJUnit4`, `createComposeRule()`); it works but ties tests to JUnit4 and adds nothing over `runComposeUiTest` for us. Skip it.

**Kotest interaction.** The known incompatibility is between kotest and JUnit4 *rules* — kotest specs cannot host `@get:Rule`, which is why the community says "you can't use kotest for Compose desktop UI tests". That applies to the rule-based API only. `runComposeUiTest` is rule-free, so calling it inside a kotest `StringSpec` block should work on JVM:

```kotlin
class DashboardUiSpec : StringSpec({
    "shows empty state when there are no accounts" {
        runComposeUiTest {
            setContent { DashboardScreen(state = DashboardState.Empty) }
            onNodeWithTag("dashboard_empty").assertExists()
        }
    }
})
```

This combination is not officially documented — it needs a 1-test spike. If it misbehaves (dispatcher/thread ownership issues), the fallback is `kotlin.test`-annotated classes in `jvmTest` for UI tests only, mirroring the existing AGENTS.md carve-out for `androidDeviceTest` ("only the runner differs, assertions stay kotest matchers"). Either way AGENTS.md → Testing Conventions should get a sentence about the desktop UI-test style once decided.

**Limitations of in-process UI tests** (apply to 2.2 and 2.3 too):

- System/OS dialogs (native file pickers, notifications) are invisible to the semantics tree — inject fakes.
- Window-level chrome (menu bar, tray, multi-window `application {}` scaffolding) is outside `setContent` — `main.kt` itself stays untested.
- `@Preview` composables are not tests; test real composables with injected state.

### 2.2 Gray-box "E2E" of the real App() (recommended for critical journeys)

Because `runComposeUiTest` accepts any composable, we can mount the **real `App()` with real Koin wiring and an in-memory Room DB** — effectively an end-to-end test minus the OS window and Firebase:

- reuse `:integration-test`'s `InMemoryRoomDatabase` / `FinanceStack` fixtures as a Koin override module (`initKoin(extraModules = testWiring)`);
- the offline wiring (`:composeAppOffline` configs) is the ideal hermetic target — no network at all;
- drive flows through the same `SurferTestTagAsId` tags Maestro already uses, so mobile Maestro flows and desktop UI tests can share tag vocabulary (see `md/ui_test.md`).

This is the highest-value/lowest-cost path to desktop E2E coverage: the ecosystem has **no mature black-box driver for Compose Desktop** (see 2.4), and this approach covers everything except packaging.

### 2.3 Screenshot / visual regression — Roborazzi

**Roborazzi is currently the only screenshot-testing library that supports Compose Desktop** (Paparazzi and standard Roborazzi/Robolectric are Android-JVM only). Support is via `roborazzi-compose-desktop` (latest stable 1.68.0, desktop support marked experimental):

```kotlin
plugins { id("io.github.takahirom.roborazzi") }

kotlin.sourceSets.jvmTest.dependencies {
    implementation("io.github.takahirom.roborazzi:roborazzi-compose-desktop:1.68.0")
}
```

```kotlin
@OptIn(ExperimentalTestApi::class)
@Test
fun dashboard() = runDesktopComposeUiTest {
    setContent { App() }
    onRoot().captureRoboImage(
        roborazziOptions = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0f),
        ),
    )
}
```

Gradle tasks: `recordRoborazzi<Target>` / `compareRoborazzi<Target>` / `verifyRoborazzi<Target>` — for our `jvm()` target (named `jvm`, not `desktop`) that is **`recordRoborazziJvm`** etc. Note `runDesktopComposeUiTest` is the JUnit-flavored entry point — the kotest question from 2.1 applies here identically.

Operational notes:

- Baselines are PNGs in-repo → use **Git LFS** (or artifact-based compare) to keep the repo lean.
- Skia software rendering is deterministic per OS but **not across OSes** (font/AA differences) — record and verify baselines on one CI OS (ubuntu) only.
- Best targets: `:uikit` components and screen-level states of `feature/*` — a natural complement to the semantic assertions in 2.1.

### 2.4 Black-box E2E of the packaged app — thin ecosystem, keep minimal

Surveyed options for driving the real packaged binary:

- **Maestro** — no desktop support (mobile + web only). Our existing flows can't be reused.
- **Appium** (mac2 / Windows drivers) — sees only what the OS accessibility layer exposes; Compose Desktop renders into a single Skia surface and its a11y bridge (Java Access Bridge on Windows, AX on macOS) is partial, so element-level automation is fragile. Not recommended.
- Commercial desktop tools (ACCELQ, image-based drivers like SikuliX) — pixel-matching, high maintenance. Not recommended.

Pragmatic recommendation: **packaging smoke test, not UI automation**. A CI job per OS that runs `./gradlew :composeApp:packageDistributionForCurrentOS` and a `runDistributable`-based launch-and-exit smoke (app starts, logs "ready", exits 0, optionally behind a `--smoke-check` flag in `main.kt`). Everything behavioral stays at levels 2.1–2.3.

## 3. CI considerations

- **Headless rendering**: `runComposeUiTest`/Roborazzi render offscreen via Skiko software rendering and are expected to run on `ubuntu-latest` without a display; Roborazzi's own CI runs desktop tests on plain GitHub runners. If AWT throws `HeadlessException` on some code path, the standard fallback is `xvfb-run ./gradlew ...` (or the `coactions/setup-xvfb` action). Budget a spike-commit to confirm on our workflow.
- **Integration into `qaCommon`**: UI tests land in `:composeApp:jvmTest` / `feature/*` `jvmTest`, which `testCommon` already runs — no new CI job needed for level 2.1/2.2. Kover/Allure pick them up automatically.
- **Roborazzi in CI**: add `verifyRoborazziJvm` to PR checks + an upload of the diff report on failure; `recordRoborazziJvm` runs locally (or via a label-triggered workflow) to update baselines.
- **Packaging matrix**: `packageDmg`/`packageMsi`/`packageDeb` require their native OS → nightly matrix `macos-15` / `windows-latest` / `ubuntu-latest`, not on every PR.

## 4. Proposed roadmap

| # | Step | Scope | Value |
|---|------|-------|-------|
| 1 | Spike: `compose.uiTest` in `:composeApp` (or one `feature/*` module), 1–2 tests; verify kotest `StringSpec` + `runComposeUiTest` v2 works, verify headless on CI | S | Unblocks everything below; settles the kotest-vs-kotlin.test convention |
| 2 | Screen-state UI tests for `feature/*` (commonTest where possible — they'd also run on Android/iOS later) | M | Regression net for UI logic, runs in existing `qaCommon` |
| 3 | Gray-box journeys: `App()` + offline wiring + `InMemoryRoomDatabase`, 3–5 critical flows (create workspace, add transaction, transfer, undo delete) | M | Desktop "E2E" without new infra; reuses integration-test fixtures |
| 4 | Roborazzi `roborazzi-compose-desktop` for `:uikit` + key screens; Git LFS for baselines; `verifyRoborazziJvm` in CI | M | Visual regression; the only screenshot option for desktop |
| 5 | Nightly packaging smoke: `packageDistributionForCurrentOS` + launch smoke on macos/windows/ubuntu | S | Catches packaging/startup breakage the in-process tests can't see |
| 6 | Update AGENTS.md Testing Conventions + `docs/testing/testing-strategy.md` with the desktop UI-test style decided in step 1 | S | Keeps conventions authoritative |

Deliberately out of scope: Appium/image-based black-box automation (fragile, low ROI), Maestro-on-desktop (unsupported), accessibility-check automation (Compose a11y test framework is Android-only as of Compose 1.8/1.9).

## 5. Sources

- [Testing Compose Multiplatform UI (kotlinlang.org)](https://kotlinlang.org/docs/multiplatform/compose-test.html) — `compose.uiTest`, `runComposeUiTest`, per-target run commands.
- [Testing Compose Multiplatform UI with JUnit (kotlinlang.org)](https://kotlinlang.org/docs/multiplatform/compose-desktop-ui-testing.html) — legacy JUnit4-rule desktop API.
- [Clean Lap: UI Testing in Compose Multiplatform (KMP Bits)](https://www.kmpbits.com/posts/compose-ui-test-cmp) — `runComposeUiTest` v2 in CMP 1.11, `StandardTestDispatcher` migration gotchas.
- [Roborazzi (GitHub)](https://github.com/takahirom/roborazzi) — `roborazzi-compose-desktop`, record/compare/verify tasks.
- [ComposablePreviewScanner (GitHub)](https://github.com/sergio-sastre/ComposablePreviewScanner) — preview-driven screenshot-test generation (Android-focused today, worth watching for CMP).
- [Compose Multiplatform UI Tests (M. Novakovic, Medium)](https://markonovakovic.medium.com/compose-multiplatform-ui-tests-d59b398bb984) — kotest `@get:Rule` incompatibility context.
- [setup-xvfb action (GitHub)](https://github.com/coactions/setup-xvfb) — headless fallback for GUI tests on CI.
