# Screenshot tests (Roborazzi)

<!-- DOCS:TOC -->
## Contents
- [Screenshot tests (Roborazzi)](#screenshot-tests-roborazzi)
- [TL;DR for agents](#tldr-for-agents)
- [Two kinds of capture](#two-kinds-of-capture)
- [Where the reference images live](#where-the-reference-images-live)
- [Commands](#commands)
- [How it is wired](#how-it-is-wired)
- [Reviewing a failure](#reviewing-a-failure)
- [What is deliberately not captured](#what-is-deliberately-not-captured)
  - [SurferSplash — blocked, not forgotten](#surfersplash--blocked-not-forgotten)
- [Adding a gallery](#adding-a-gallery)
- [Adding a screen](#adding-a-screen)
- [Enabling a new module](#enabling-a-new-module)
<!-- DOCS:END -->

## TL;DR for agents

- Modules render their UI under Robolectric and diff it against PNGs committed
  in `<module>/screenshots/`.
- Three capture shapes: **component galleries** (`:uikit`), **full screens**
  (`:feature:login` onboarding + sign-in) and **full screens at three window
  widths** (the app shell in `:uikit`, the dashboard in `:feature:dashboard`).
- A visual change fails `./gradlew qaAndroidHost` — the same job that already
  gates every PR. There is no separate opt-in check.
- If the change is intended, re-record and commit the new PNGs **in the same
  commit as the UI change**.

READ WHEN:
- changing anything under `uikit/src/commonMain` or a captured feature screen
- a PR fails on `testAndroidHostTest`
- adding a new Surfer component, or a new screen worth reviewing visually

<!-- AI:SECTION id=screenshot-tests task=testing,uikit,screenshot,roborazzi -->
## Two kinds of capture

| | `captureLightAndDark` | `captureFullScreen` |
|---|---|---|
| Viewport | 411dp wide, height wraps content | 411×891dp, the whole device |
| Wrapper | `SurferComponentPreview` (themed card surface) | `AppTheme` only — the screen paints its own background |
| For | design-system components | whole screens |

Both live in the shared harness and capture light + dark from one test.

The full-screen captures exist as much for **review** as for regression: the PNG
is a frame you can look at and recognise as the screen, so `git diff` on a UI PR
shows what actually changed on the phone rather than a component swatch. That is
why screens are captured in the states that differ between builds (online vs
offline onboarding, demo-only sign-in) rather than in every permutation.

## Where the reference images live

```
uikit/screenshots/<name>_light.png                  # component galleries
uikit/screenshots/<name>_<width>_light.png          # the app shell, per width
feature/login/screenshots/<name>_light.png          # onboarding + sign-in
feature/dashboard/screenshots/<name>_<width>_light.png   # the dashboard, per width
```

Committed to git, one pair per capture. Every capture is taken in both themes
from a single test, so a screen can never drift in one theme while the other
stays green.

Diff output from a failing run is **not** committed — it lands in
`<module>/build/outputs/roborazzi/` as `<name>_actual.png` and
`<name>_compare.png`.

## Commands

The task names are unqualified on purpose: Gradle runs them in **every** module
that has them, so one command refreshes the whole set.

```bash
# Re-record every reference in every module — the "give me current screenshots" command.
./gradlew recordScreenshots -Proborazzi.record=true
```

```bash
# Verify against the committed references (what CI runs).
./gradlew verifyScreenshots
```

```bash
# One module only.
./gradlew :feature:login:recordScreenshots -Proborazzi.record=true
```

`recordScreenshots` refuses to run without `-Proborazzi.record=true`, so a
half-typed command can never silently overwrite the baseline with whatever the
current code renders.

## How it is wired

[`gradle/screenshot-tests.gradle.kts`](../../gradle/screenshot-tests.gradle.kts),
applied from [`uikit/build.gradle.kts`](../../uikit/build.gradle.kts) and
[`feature/login/build.gradle.kts`](../../feature/login/build.gradle.kts).

The Roborazzi **Gradle plugin** is deliberately not used. It drives AGP's
application/library variants, while every module here uses
`com.android.kotlin.multiplatform.library`, whose test task is
`testAndroidHostTest` rather than `test<Variant>UnitTest`. Roborazzi's runtime
reads its configuration from system properties, so the plugin's only real job —
flipping record/verify and pointing at an output directory — is a handful of
lines of wiring instead of a plugin that does not fit the build.

Robolectric needs the merged Android resources on the host-test classpath;
`isIncludeAndroidResources` is enabled for every KMP library in
[`KmpLibConventionPlugin`](../../build-logic/kmp/src/main/kotlin/com/georgeci/moneysurfer/buildlogic/KmpLibConventionPlugin.kt).

The capture harness itself —
[`gradle/screenshot-harness/kotlin/…/SurferScreenshot.kt`](../../gradle/screenshot-harness/kotlin/com/georgeci/moneysurfer/screenshot/SurferScreenshot.kt)
— is **shared source**, not a module: captures are only comparable across modules
if every module renders through literally the same code, and no module can depend
on another module's test source set. Each participating module adds the directory
to its own `androidHostTest` source set.

Roborazzi's capture entry points are JUnit 4, but the kotest-based feature modules
run `testAndroidHostTest` on the JUnit Platform, which would discover none of them —
a suite that passes by running nothing. The script plugin therefore puts
`junit-vintage-engine` on the host-test runtime classpath, and asserts after every
run that Roborazzi wrote at least one result. That guard is what turns "discovered
nothing" from a green build into a red one.

Two constants in `SurferScreenshot.kt` pin the render environment — the
Robolectric SDK level and the device qualifiers (density, font scale, width).
Changing either re-renders everything, so treat it as a deliberate, reviewed
change followed by a full re-record.

The comparison has two knobs, both in `SurferScreenshot.kt`:

- **Per-pixel tolerance** (`maxDistance = 0.02`, a normalised RGBA distance).
  Alpha compositing rounds differently on the macOS and Linux Skia builds — the
  same component captured on both hosts yields images where up to ~13% of pixels
  differ by 1–2 of 255 per channel (worst case measured on
  `surfer_category_components_dark`, peak distance 0.0136). Without this, the
  suite is green locally and red on CI. 0.02 sits above that noise floor and an
  order of magnitude below a real regression.
- **Change threshold** (0.1% of the frame): the share of pixels allowed to
  exceed the tolerance. A one-pixel border still trips it.

Consequence worth knowing: references recorded on macOS verify fine on the
Linux CI runner, so you do not need a Linux host to re-record.

## Reviewing a failure

1. Open `<module>/build/outputs/roborazzi/<name>_compare.png` — reference, actual
   and diff side by side. CI uploads this directory as the `roborazzi-diffs`
   artifact on the `test` job.
2. Unintended? Fix the component.
3. Intended? Re-record, then eyeball every PNG in the diff before committing —
   a re-record rewrites *all* references, including ones you did not mean to touch.

## What is deliberately not captured

Components driving an infinite animation render a time-dependent frame, so the
comparison would flake:

- `SurferFullScreenLoader`
- `SurferSkeleton` / `SurferSkeletonRow`
- `SurferAccountDetailsHeroCard` (its "synced" dot pulses)

Cover those with behavioural tests instead. This is also why sign-in has no
`isLoading = true` capture — that state is the full-screen loader.

### `SurferSplash` — blocked, not forgotten

The launch screen is the obvious next full-screen capture and it is **not** here,
because `captureRoboImage` never returns on it. The capture hangs rather than
fails, which burns a CI job's whole timeout instead of printing a diff. Two
candidate causes, neither confirmed:

- its `CircularProgressIndicator` animates forever, so the frame callbacks it
  queues keep `ActivityScenario`'s looper idling; and/or
- `painterResource(Res.drawable.uikit_app_icon)` — the composable resource load
  is async, and the onboarding and sign-in screens that *do* capture cleanly use
  only `stringResource`.

Splitting light and dark into separate `@Test` methods does not help, so a fresh
Robolectric environment is not what is missing. The path that should work is
driving the capture through `runComposeUiTest` with `mainClock.autoAdvance =
false` and `onRoot().captureRoboImage(...)`, which needs
`org.jetbrains.compose.ui:ui-test` added to the `screenshot-test` bundle. Until
someone does that, do not add a splash capture back — a hanging test is worse
than a missing one.

## Adding a gallery

Add a `@Test` to one of the `*ScreenshotTest` classes in
`uikit/src/androidHostTest/kotlin/com/georgeci/moneysurfer/uikit/screenshot/`, or
create a new class carrying the same three annotations:

```kotlin
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class SurferThingScreenshotTest {

    @Test
    fun surferThings() = captureLightAndDark("surfer_things") {
        // Composable content — already wrapped in AppTheme + the app surface.
    }
}
```

Then record the new references and commit them alongside the test.

## Adding a screen

Same three annotations, but `captureFullScreen` and a **stateless** body — mount
the screen's content composable with an injected state rather than standing up a
Koin-backed view model:

```kotlin
@Test
fun onboardingValueOffline() = captureFullScreen("onboarding_value_offline") {
    OnboardingContent(state = OnboardingState(isOffline = true), onEvent = {})
}
```

### …at three window widths

A screen whose layout depends on the window is captured with
`captureFullScreenAtWidths` instead, which repeats the capture once per
`ScreenshotWidth` — Compact (411 dp), Expanded (1024 dp) and Large (1360 dp, the
design's desktop canvas). Roborazzi resizes the Robolectric display itself, so
`currentSurferWindowSize()` inside the screen reports the width being captured:

```kotlin
@Test
fun dashboard() = captureFullScreenAtWidths("dashboard") {
    DashboardContent(state = dashboardState(), onEvent = {})
}
```

Six PNGs per capture, so use it only where a width actually changes the layout —
the app shell and the dashboard today. Everything else stays on
`captureFullScreen`.

If the screen has no stateless body yet, extract one — `SignInContent` and
`OnboardingContent` are the pattern. Pick the states that differ *structurally*
(a build variant, an error shape, an empty list), not every field permutation:
each one is two PNGs a reviewer has to look at.

## Enabling a new module

Three edits, all of them mechanical:

1. `<module>/build.gradle.kts` — apply the script plugin and compile the harness:
   ```kotlin
   kotlin.sourceSets.getByName("androidHostTest") {
       kotlin.srcDir(rootProject.file("gradle/screenshot-harness/kotlin"))
   }
   apply(from = rootProject.file("gradle/screenshot-tests.gradle.kts"))
   ```
2. Root [`build.gradle.kts`](../../build.gradle.kts) — add the module to
   `screenshotHarnessProjects` so detekt lints the harness there. The directory
   sits outside every module's `src/`, so it is invisible to the per-module
   detekt config otherwise.
3. `./gradlew :module:recordScreenshots -Proborazzi.record=true`, then commit
   `<module>/screenshots/`.

The srcDir and detekt lines cannot move into the script plugin: a script applied
with `apply(from = …)` is compiled against the Gradle API alone, without the
Kotlin or detekt plugin types.
<!-- AI:END -->
