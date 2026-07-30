package com.georgeci.moneysurfer.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.uikit.components.category.SurferCategoryTrendBar
import com.georgeci.moneysurfer.uikit.components.category.SurferCategoryTrendCard
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import io.kotest.core.spec.style.StringSpec

/**
 * The bar treatment `SurferCategoryTrendCard` was built with, now shared with the insights screen's
 * income-vs-expense chart through `SurferBarColumns`.
 *
 * Asserted through the trend card rather than through the primitive directly: the card is what
 * already shipped, so these are the promises its own KDoc makes. Nothing rendered this component
 * before the extraction, which is what made the refactor worth covering.
 *
 * Bar *heights* are deliberately not asserted. A column publishes one merged
 * `contentDescription` — the bars inside carry no semantics of their own — and every column fills
 * the chart's height, so any geometry assertion available here would pass without measuring what it
 * claims to. What the zero-set spec does catch is the divide-by-zero guard: an unguarded scale
 * yields a NaN fraction, and `Modifier.height(NaN.dp)` throws rather than drawing.
 */
@OptIn(ExperimentalTestApi::class)
class SurferBarColumnsTest : StringSpec({

    "every column is announced as its label and its value" {
        runComposeUiTest {
            setContent { TrendCard(BARS) }

            onNodeWithContentDescription("Jan: €240").assertIsDisplayed()
            onNodeWithContentDescription("Feb: €120").assertIsDisplayed()
            onNodeWithContentDescription("Mar: €176").assertIsDisplayed()
            onNodeWithContentDescription("Apr: €168.55").assertIsDisplayed()
        }
    }

    "only the final column prints its value, so phone-width numerals cannot collide" {
        runComposeUiTest {
            setContent { TrendCard(BARS) }

            // Unmerged: each column clears its children's semantics in favour of one description,
            // so the printed label is only addressable below the merge.
            onNodeWithText("€168.55", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithText("€240", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    "a set that is entirely zero still draws every column instead of dividing by zero" {
        runComposeUiTest {
            setContent { TrendCard(List(4) { SurferCategoryTrendBar("M$it", 0, "€0") }) }

            onNodeWithContentDescription("M0: €0").assertIsDisplayed()
            onNodeWithContentDescription("M3: €0").assertIsDisplayed()
        }
    }
})

private val BARS = listOf(
    SurferCategoryTrendBar("Jan", 24_000, "€240"),
    SurferCategoryTrendBar("Feb", 12_000, "€120"),
    SurferCategoryTrendBar("Mar", 17_600, "€176"),
    SurferCategoryTrendBar("Apr", 16_855, "€168.55"),
)

@Composable
private fun TrendCard(bars: List<SurferCategoryTrendBar>) {
    SurferComponentPreview {
        SurferCategoryTrendCard(
            title = "Last 4 months",
            bars = bars,
            tint = Color.Red,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
