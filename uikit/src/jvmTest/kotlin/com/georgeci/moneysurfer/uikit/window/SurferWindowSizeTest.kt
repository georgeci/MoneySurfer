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
})
