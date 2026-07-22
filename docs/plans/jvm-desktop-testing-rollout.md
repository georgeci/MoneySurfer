---
title: JVM desktop testing — compose.uiTest spike + rollout roadmap
created: 2026-07-15
status: in-progress
---

# JVM desktop testing — compose.uiTest spike + rollout roadmap

<!-- DOCS:TOC -->
## Contents
- [JVM desktop testing — compose.uiTest spike + rollout roadmap](#jvm-desktop-testing--composeuitest-spike--rollout-roadmap)
- [Chosen approach](#chosen-approach)
- [Roadmap](#roadmap)
- [Spike details (step 1)](#spike-details-step-1)
- [Known limitations (accepted)](#known-limitations-accepted)
- [Out of scope](#out-of-scope)
<!-- DOCS:END -->

Full research: [docs/testing/jvm-desktop-testing-research.md](../testing/jvm-desktop-testing-research.md)
(stack at time of research: Kotlin 2.4.0, Compose Multiplatform 1.11.1, Kotest 6.2.1).

## Chosen approach

**`compose.uiTest` (`runComposeUiTest` v2) inside `jvmTest`**, in two applications:

1. **Screen-state UI tests** for `feature/*` — fast checks of screen logic through the
   semantics tree (test-tag infra already exists: `uikit/.../SurferTestTagAsId`).
2. **Gray-box journeys** — same `runComposeUiTest`, but mounting the real `App()` with
   offline wiring and `InMemoryRoomDatabase` from `:integration-test`. This is the desktop
   "E2E": real user flows without Firebase and without a real window.

Why this option:

- **Zero new infrastructure** — tests land in the existing `:composeApp:jvmTest`, which
  `qaCommon` already runs on CI; Kover and Allure pick them up automatically.
- **No real alternatives**: Maestro has no desktop support; Appium/image-based tools are
  fragile against Compose Desktop (single Skia canvas, weak a11y bridge). Black-box
  automation is a dead end.
- Versions align: CMP 1.11.1 is exactly where `runComposeUiTest` v2 became a proper
  cross-platform API (no JUnit rule required).

Roborazzi (screenshots) and packaging smoke are worthwhile follow-ups, stages 2–3, not the start.

## Roadmap

| # | Step | Scope | Value |
|---|------|-------|-------|
| 1 | ✅ Spike: `compose.uiTest` in `:composeApp`, `SignInContent` screen-state tests; kotest `StringSpec` + `runComposeUiTest` v2 confirmed, headless confirmed | S | Unblocks everything below; settles the kotest-vs-kotlin.test convention |
| 2 | Screen-state UI tests for `feature/*` (commonTest where possible — they'd also run on Android/iOS later) | M | Regression net for UI logic, runs in existing `qaCommon` |
| 3 | Gray-box journeys: `App()` + offline wiring + `InMemoryRoomDatabase`, 3–5 critical flows (create workspace, add transaction, transfer, undo delete) | M | Desktop "E2E" without new infra; reuses integration-test fixtures |
| 4 | Roborazzi `roborazzi-compose-desktop` for `:uikit` + key screens; Git LFS for baselines; `verifyRoborazziJvm` in CI | M | Visual regression; the only screenshot option for desktop |
| 5 | Nightly packaging smoke: `packageDistributionForCurrentOS` + launch smoke on macos/windows/ubuntu | S | Catches packaging/startup breakage the in-process tests can't see |
| 6 | Update AGENTS.md Testing Conventions + `docs/testing/testing-strategy.md` with the desktop UI-test style decided in step 1 | S | Keeps conventions authoritative |

## Spike details (step 1) — done

**Answers (spike landed in `composeApp/src/jvmTest/.../ui/SignInScreenStateTest.kt`, 7 tests):**

- **kotest works.** `androidx.compose.ui.test.v2.runComposeUiTest` runs fine inside a
  `StringSpec` block — no JUnit rule, no dispatcher or thread-ownership conflict with the kotest
  engine. The `kotlin.test` fallback is **not** needed: desktop UI tests follow the normal
  AGENTS.md convention (kotest `StringSpec` + kotest matchers).
- **Headless works.** No display, no `xvfb`. `jvmTest` now sets
  `systemProperty("java.awt.headless", "true")` so developer machines exercise the same path CI
  does, and one test asserts `GraphicsEnvironment.isHeadless()` so a regression fails loudly
  instead of silently depending on a display.
- **Dependency is declared, not accessed.** `compose.uiTest` is deprecated in CMP 1.11
  ("Specify dependency directly") and fails Kotlin script compilation without an opt-in, so the
  artifact is a normal catalog entry: `compose-uiTest = org.jetbrains.compose.ui:ui-test`.
- **Screens need a stateless entry point.** `SignInScreen` resolves its ViewModel via
  `koinViewModel()`; the test mounts the now-public `SignInContent(state, onEvent)` instead. Each
  screen pulled into step 2 needs the same treatment — publish the stateless content composable.
- **Lockfiles.** Adding the dependency needs a relock. Scope it
  (`./gradlew :composeApp:resolveAndLockAll --write-locks --no-configuration-cache`) — the
  repo-wide `resolveAndLockAll` also drags in unrelated floating-version drift across all 28
  lockfiles.

Original questions:

- Does `runComposeUiTest` v2 (`androidx.compose.ui.test.v2.runComposeUiTest`) work inside a
  kotest `StringSpec` block? It is rule-free (the known kotest incompatibility only applies to
  the JUnit4 `createComposeRule` API). If it misbehaves (dispatcher/thread ownership), fall
  back to `kotlin.test`-annotated classes in `jvmTest` for UI tests only — mirroring the
  existing AGENTS.md carve-out for `androidDeviceTest`.
- Does it run headless on `ubuntu-latest` without a display? Expected yes (offscreen Skiko
  software rendering); fallback is `xvfb-run` / `coactions/setup-xvfb`.

Migration gotcha to bake into the first tests: CMP 1.11 defaults to `StandardTestDispatcher` —
coroutines launched in composition don't run eagerly; use `waitForIdle()` / `waitUntil {}`.

Setup (as landed):

```kotlin
kotlin {
    sourceSets {
        jvmTest.dependencies {
            implementation(projects.feature.login)
            implementation(libs.compose.uiTest) // org.jetbrains.compose.ui:ui-test:1.11.1
        }
        // :composeApp jvmMain already has compose.desktop.currentOs
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    systemProperty("java.awt.headless", "true")
}
```

## Known limitations (accepted)

- System/OS dialogs (native file pickers, notifications) are invisible to the semantics tree — inject fakes.
- Window-level chrome (menu bar, tray, multi-window `application {}` scaffolding) is outside `setContent` — `main.kt` stays covered only by the packaging smoke (step 5).
- `@Preview` composables are not tests; test real composables with injected state.

## Out of scope

- Appium / image-based black-box automation (fragile, low ROI).
- Maestro on desktop (unsupported).
- Accessibility-check automation (Compose a11y test framework is Android-only as of Compose 1.8/1.9).
