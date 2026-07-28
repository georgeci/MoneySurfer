package com.georgeci.moneysurfer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.modifier.SurferContentMaxWidth
import com.georgeci.moneysurfer.uikit.modifier.surferContentContainer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

private const val WINDOW = "content-container:window"
private const val ROW = "content-container:row"

/** A phone-width window: well under [SurferContentMaxWidth], so the cap must not engage. */
private val PhoneWidth = 411.dp

/** The window width issue #387 calls out as the "stretched phone" case. */
private val DesktopWidth = 1360.dp

/** Layout rounds to whole pixels; half a dp is well inside that and well outside a real shift. */
private const val TOLERANCE_DP = 0.5

/**
 * The acceptance cover for issue #387's content-width cap, exercised through the real modifier
 * rather than through a screen: every feature screen applies the same chain at its content root,
 * so what has to hold is that the chain is inert below the cap and centres above it.
 *
 * Lives in `composeApp` because that is where the project's Compose desktop UI tests are wired
 * (`libs.compose.uiTest`); `:uikit` carries no UI-test dependency of its own.
 */
@OptIn(ExperimentalTestApi::class)
class SurferContentContainerTest : StringSpec({

    "at a phone width the cap is inert — content still spans the whole window" {
        runComposeUiTest {
            setContent { CappedContent(windowWidth = PhoneWidth) }

            val window = onNodeWithTag(WINDOW).getUnclippedBoundsInRoot()
            val row = onNodeWithTag(ROW).getUnclippedBoundsInRoot()

            (window.right - window.left).shouldBeDp(PhoneWidth)
            (row.right - row.left).shouldBeDp(PhoneWidth)
            (row.left - window.left).shouldBeDp(0.dp)
        }
    }

    "content is capped and centred once the window is wider than the cap" {
        runComposeUiTest {
            setContent { CappedContent(windowWidth = DesktopWidth) }

            val window = onNodeWithTag(WINDOW).getUnclippedBoundsInRoot()
            val row = onNodeWithTag(ROW).getUnclippedBoundsInRoot()

            (window.right - window.left).shouldBeDp(DesktopWidth)
            (row.right - row.left).shouldBeDp(SurferContentMaxWidth)

            val margin = (DesktopWidth - SurferContentMaxWidth) / 2
            (row.left - window.left).shouldBeDp(margin)
            (window.right - row.right).shouldBeDp(margin)
        }
    }
})

private fun Dp.shouldBeDp(expected: Dp) {
    value.toDouble() shouldBe expected.value.toDouble().plusOrMinus(TOLERANCE_DP)
}

/**
 * A stand-in for a feature screen's Scaffold content root — same modifier order the screens use —
 * inside a window of [windowWidth].
 *
 * Both measured nodes are children rather than the tagged root itself: a semantics node reports the
 * bounds of the *outermost* modifier in its chain, which by design still spans the window. [WINDOW]
 * is a full-width sibling that marks the window's own edges, and positions are compared against it
 * because `requiredWidth` lets the box overflow the test window, putting root coordinates off by
 * the overflow.
 */
@Composable
private fun CappedContent(windowWidth: Dp) {
    Box(
        modifier = Modifier
            .requiredWidth(windowWidth)
            .requiredHeight(600.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .testTag(WINDOW),
        )
        Column(modifier = Modifier.fillMaxSize().surferContentContainer()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag(ROW),
            )
        }
    }
}
