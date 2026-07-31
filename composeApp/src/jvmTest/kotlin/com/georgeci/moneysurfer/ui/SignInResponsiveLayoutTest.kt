package com.georgeci.moneysurfer.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.feature.login.SignInContent
import com.georgeci.moneysurfer.feature.login.SignInState
import com.georgeci.moneysurfer.feature.login.SignInTestTags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The sign-in screen's second layout: brand beside the sheet from expanded width up, but only while
 * the window is also wider than it is tall — the "01d · Sign in (small tablet, landscape)" artboard.
 *
 * Assertions are on geometry rather than on what is drawn, because the split is the whole of what
 * this adds: the same nodes, placed side by side instead of stacked. `SignInScreenStateTest` covers
 * the states themselves, pinned to a phone window.
 *
 * Bounds are read unclipped — in a short landscape window the sheet scrolls, and a node below the
 * fold still has the position that is under test.
 */
@OptIn(ExperimentalTestApi::class)
class SignInResponsiveLayoutTest : StringSpec({

    "a phone in portrait stacks the sheet under the hero" {
        runSignInAt(PHONE_WIDTH_PX, PHONE_HEIGHT_PX) {
            setContent { SignInContent(state = SignInState(), onEvent = {}) }

            onNodeWithTag(SignInTestTags.Sheet).assertIsDisplayed()
            (sheet().top >= hero().bottom) shouldBe true
        }
    }

    "a tablet in landscape puts the sheet beside the hero" {
        runSignInAt(TABLET_LANDSCAPE_WIDTH_PX, TABLET_LANDSCAPE_HEIGHT_PX) {
            setContent { SignInContent(state = SignInState(), onEvent = {}) }

            onNodeWithTag(SignInTestTags.Sheet).assertIsDisplayed()
            (sheet().left >= hero().right) shouldBe true
            sheet().sharesRowWith(hero()) shouldBe true
        }
    }

    "a desktop window puts the sheet beside the hero" {
        runSignInAt(DESKTOP_WIDTH_PX, DESKTOP_HEIGHT_PX) {
            setContent { SignInContent(state = SignInState(), onEvent = {}) }

            (sheet().left >= hero().right) shouldBe true
            sheet().sharesRowWith(hero()) shouldBe true
        }
    }

    // Width alone would split this one: it is past the expanded bound but held upright, and two
    // half-empty columns down a 1366 dp window is exactly what the height check exists to prevent.
    "a tablet in portrait stays stacked even though it is expanded-width" {
        runSignInAt(TABLET_LANDSCAPE_WIDTH_PX, TABLET_PORTRAIT_HEIGHT_PX) {
            setContent { SignInContent(state = SignInState(), onEvent = {}) }

            (sheet().top >= hero().bottom) shouldBe true
        }
    }

    // Asserted as "the same at both widths" rather than against a copy of the production cap: the
    // point is that the sheet stops growing, and a duplicated number would drift from the real one.
    "the sheet stops growing once the cap engages, instead of tracking the window" {
        var onTablet = 0.dp
        var onDesktop = 0.dp
        runSignInAt(TABLET_LANDSCAPE_WIDTH_PX, TABLET_LANDSCAPE_HEIGHT_PX) {
            setContent { SignInContent(state = SignInState(), onEvent = {}) }
            onTablet = sheet().width
        }
        runSignInAt(DESKTOP_WIDTH_PX, DESKTOP_HEIGHT_PX) {
            setContent { SignInContent(state = SignInState(), onEvent = {}) }
            onDesktop = sheet().width
        }

        onDesktop shouldBe onTablet
    }

    "every action stays reachable in a short landscape window" {
        // A phone on its side: expanded width, but only ~410 dp of height for a form roughly 545 dp
        // tall, so the bottom of the sheet starts off screen. `performScrollTo` is the assertion
        // that matters — it fails unless a scrollable ancestor can actually bring the node into
        // view, where `assertExists` would pass on a composed-but-unreachable node just the same.
        runSignInAt(PHONE_HEIGHT_PX, PHONE_WIDTH_PX) {
            setContent { SignInContent(state = SignInState(), onEvent = {}) }

            onNodeWithTag(SignInTestTags.SubmitButton).performScrollTo().assertIsDisplayed()
            onNodeWithTag(SignInTestTags.AnonymousButton).performScrollTo().assertIsDisplayed()
            onNodeWithTag(SignInTestTags.Terms).performScrollTo().assertIsDisplayed()
        }
    }
})

/** A 411 dp phone in portrait — Compact, so the layout stays stacked. */
private const val PHONE_WIDTH_PX = 411f
private const val PHONE_HEIGHT_PX = 891f

/** The 1024 × 700 artboard the landscape design is drawn at. */
private const val TABLET_LANDSCAPE_WIDTH_PX = 1024f
private const val TABLET_LANDSCAPE_HEIGHT_PX = 700f

/** The same 1024 dp tablet, stood upright. */
private const val TABLET_PORTRAIT_HEIGHT_PX = 1366f

/** The 1360 dp desktop window the app opens at. */
private const val DESKTOP_WIDTH_PX = 1360f
private const val DESKTOP_HEIGHT_PX = 880f

private val DpRect.width: Dp get() = right - left

@OptIn(ExperimentalTestApi::class)
private fun SkikoComposeUiTest.sheet(): DpRect =
    onNodeWithTag(SignInTestTags.Sheet).getUnclippedBoundsInRoot()

@OptIn(ExperimentalTestApi::class)
private fun SkikoComposeUiTest.hero(): DpRect =
    onNodeWithTag(SignInTestTags.HeroTitle).getUnclippedBoundsInRoot()

/** Whether two blocks were placed side by side, asserted as a vertical overlap. */
private fun DpRect.sharesRowWith(other: DpRect): Boolean =
    top < other.bottom && other.top < bottom

@OptIn(ExperimentalTestApi::class)
private fun runSignInAt(
    widthPx: Float,
    heightPx: Float,
    block: suspend SkikoComposeUiTest.() -> Unit,
) = runSkikoComposeUiTest(size = Size(widthPx, heightPx), block = block)
