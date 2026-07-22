# Screenshot tests (Roborazzi)

<!-- DOCS:TOC -->
## Contents
- [Screenshot tests (Roborazzi)](#screenshot-tests-roborazzi)
- [TL;DR for agents](#tldr-for-agents)
- [Where the reference images live](#where-the-reference-images-live)
- [Commands](#commands)
- [How it is wired](#how-it-is-wired)
- [Reviewing a failure](#reviewing-a-failure)
- [What is deliberately not captured](#what-is-deliberately-not-captured)
- [Adding a gallery](#adding-a-gallery)
<!-- DOCS:END -->

## TL;DR for agents

- `:uikit` renders its design-system components under Robolectric and diffs them
  against PNGs committed in `uikit/screenshots/`.
- A visual change fails `./gradlew qaAndroidHost` — the same job that already
  gates every PR. There is no separate opt-in check.
- If the change is intended, re-record and commit the new PNGs **in the same
  commit as the UI change**.

READ WHEN:
- changing anything under `uikit/src/commonMain`
- a PR fails on `:uikit:testAndroidHostTest`
- adding a new Surfer component

<!-- AI:SECTION id=screenshot-tests task=testing,uikit,screenshot,roborazzi -->
## Where the reference images live

```
uikit/screenshots/<gallery>_light.png
uikit/screenshots/<gallery>_dark.png
```

Committed to git, one pair per gallery. Every gallery is captured in both themes
from a single test, so a component can never drift in one theme while the other
stays green.

Diff output from a failing run is **not** committed — it lands in
`uikit/build/outputs/roborazzi/` as `<name>_actual.png` and `<name>_compare.png`.

## Commands

```bash
# Verify against the committed references (what CI runs).
./gradlew :uikit:testAndroidHostTest

# Re-record every reference after an intended UI change.
./gradlew :uikit:recordScreenshots -Proborazzi.record=true
```

`recordScreenshots` refuses to run without `-Proborazzi.record=true`, so a
half-typed command can never silently overwrite the baseline with whatever the
current code renders.

## How it is wired

[`gradle/screenshot-tests.gradle.kts`](../../gradle/screenshot-tests.gradle.kts),
applied from [`uikit/build.gradle.kts`](../../uikit/build.gradle.kts).

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

Two constants in `SurferScreenshot.kt` pin the render environment — the
Robolectric SDK level and the device qualifiers (density, font scale, width).
Changing either re-renders everything, so treat it as a deliberate, reviewed
change followed by a full re-record.

The comparison threshold is 0.1% of the frame. That absorbs the sub-pixel
antialiasing jitter that differs between JDK builds, while still tripping on a
one-pixel border or a colour-token change.

## Reviewing a failure

1. Open `uikit/build/outputs/roborazzi/<name>_compare.png` — reference, actual
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

Cover those with behavioural tests instead.

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
<!-- AI:END -->
