package com.georgeci.moneysurfer.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.georgeci.moneysurfer.uikit.window.SurferWindowSize
import com.georgeci.moneysurfer.uikit.window.currentSurferWindowSize
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Covers the composition half of `currentSurferWindowSize()` — the part that reads the window and
 * converts it to dp. `SurferWindowSizeTest` in `:uikit` already pins the breakpoint table itself;
 * what is only reachable from a composition is the density conversion, and that is exactly where a
 * silent bug would hide: treating the container size as dp rather than pixels misclassifies every
 * window on any device that is not 1x, and every adaptive decision downstream follows it.
 *
 * Every case below is therefore run at 2x, so raw pixels and real dp land in different buckets.
 *
 * Lives in `composeApp` because that is where the project's Compose desktop UI tests are wired
 * (`libs.compose.uiTest`); `:uikit` carries no UI-test dependency of its own.
 */
@OptIn(ExperimentalTestApi::class)
class SurferWindowSizeCompositionTest : StringSpec({

    listOf(
        Triple(822, SurferWindowSize.Compact, "411 dp phone"),
        Triple(1200, SurferWindowSize.Medium, "600 dp small tablet"),
        Triple(1680, SurferWindowSize.Expanded, "840 dp tablet"),
        Triple(2720, SurferWindowSize.Large, "1360 dp desktop"),
    ).forEach { (widthPx, expected, label) ->
        "a $label window at 2x density reports $expected" {
            runComposeUiTest {
                var observed: SurferWindowSize? = null
                setContent {
                    CompositionLocalProvider(
                        LocalWindowInfo provides FakeWindowInfo(IntSize(widthPx, WINDOW_HEIGHT_PX)),
                        LocalDensity provides Density(DENSITY),
                    ) {
                        observed = currentSurferWindowSize()
                    }
                }
                waitForIdle()

                observed shouldBe expected
            }
        }
    }
})

/** Every case runs at 2x so a pixels-read-as-dp regression changes the bucket, not just the value. */
private const val DENSITY = 2f

/** Irrelevant to the width breakpoints, but a window needs one. */
private const val WINDOW_HEIGHT_PX = 1600

/**
 * `WindowInfo` leaves everything but [isWindowFocused] defaulted, so a fake only has to supply the
 * container size the test wants to drive.
 */
private class FakeWindowInfo(override val containerSize: IntSize) : WindowInfo {
    override val isWindowFocused: Boolean = true
}
