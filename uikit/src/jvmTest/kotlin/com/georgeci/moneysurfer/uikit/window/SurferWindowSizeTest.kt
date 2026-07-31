package com.georgeci.moneysurfer.uikit.window

import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Guards the breakpoint table itself: `ofWidth` is where "wide" is defined for the whole app, and
 * an off-by-one at a bound silently moves every adaptive decision downstream of it.
 */
class SurferWindowSizeTest : StringSpec({

    "widths below the Medium bound are Compact" {
        SurferWindowSize.ofWidth(0.dp) shouldBe SurferWindowSize.Compact
        SurferWindowSize.ofWidth(411.dp) shouldBe SurferWindowSize.Compact
        SurferWindowSize.ofWidth(599.dp) shouldBe SurferWindowSize.Compact
    }

    "the Medium band runs from its lower bound up to the Expanded bound" {
        SurferWindowSize.ofWidth(600.dp) shouldBe SurferWindowSize.Medium
        SurferWindowSize.ofWidth(839.dp) shouldBe SurferWindowSize.Medium
    }

    "the Expanded band runs from its lower bound up to the Large bound" {
        SurferWindowSize.ofWidth(840.dp) shouldBe SurferWindowSize.Expanded
        SurferWindowSize.ofWidth(1199.dp) shouldBe SurferWindowSize.Expanded
    }

    "widths at or above the Large bound are Large" {
        SurferWindowSize.ofWidth(1200.dp) shouldBe SurferWindowSize.Large
        SurferWindowSize.ofWidth(1360.dp) shouldBe SurferWindowSize.Large
    }

    "entries are ordered narrow to wide, so comparisons read as at-least" {
        SurferWindowSize.entries.toList() shouldBe listOf(
            SurferWindowSize.Compact,
            SurferWindowSize.Medium,
            SurferWindowSize.Expanded,
            SurferWindowSize.Large,
        )
        (SurferWindowSize.ofWidth(1360.dp) >= SurferWindowSize.Medium) shouldBe true
        (SurferWindowSize.ofWidth(411.dp) >= SurferWindowSize.Medium) shouldBe false
    }

    "geometry reports the width band, same as ofWidth" {
        SurferWindowGeometry(width = 411.dp, height = 891.dp).sizeClass shouldBe SurferWindowSize.Compact
        SurferWindowGeometry(width = 1024.dp, height = 700.dp).sizeClass shouldBe SurferWindowSize.Expanded
        SurferWindowGeometry(width = 1360.dp, height = 880.dp).sizeClass shouldBe SurferWindowSize.Large
    }

    // The pair a width band cannot tell apart, and the reason the flag exists: the same 1024 dp
    // tablet reports Expanded whether it is lying down or stood up.
    "landscape is width against height, not a band" {
        SurferWindowGeometry(width = 1024.dp, height = 700.dp).isLandscape shouldBe true
        SurferWindowGeometry(width = 1024.dp, height = 1366.dp).isLandscape shouldBe false
    }

    "a square window is not landscape" {
        SurferWindowGeometry(width = 800.dp, height = 800.dp).isLandscape shouldBe false
    }
})
