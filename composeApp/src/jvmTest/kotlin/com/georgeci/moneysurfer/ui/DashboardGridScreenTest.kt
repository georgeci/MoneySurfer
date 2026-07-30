package com.georgeci.moneysurfer.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSpan
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.feature.dashboard.AccountUi
import com.georgeci.moneysurfer.feature.dashboard.DashboardContent
import com.georgeci.moneysurfer.feature.dashboard.DashboardState
import com.georgeci.moneysurfer.feature.dashboard.DashboardTestTags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * The dashboard's second layout: a grid from expanded width up, driven by each widget's
 * [DashboardWidgetSpan] — see docs/testing/testing-strategy.md.
 *
 * Assertions are on layout geometry rather than on what is drawn, because that is the whole of what
 * this change adds: which widgets share a row, and how wide each one is. `DashboardScreenStateTest`
 * covers the widgets themselves, at phone width.
 *
 * Bounds are read unclipped: at 1360 dp the default layout is taller than the window, and a widget
 * scrolled past the bottom still has a position — it is the position that is under test.
 */
@OptIn(ExperimentalTestApi::class)
class DashboardGridScreenTest : StringSpec({

    "below expanded width every widget still gets the whole row, whatever its span says" {
        runDashboardAt(PHONE_WIDTH_PX) {
            setContent { DashboardContent(state = CONTENT, onEvent = {}) }

            // Balance is TwoThirds and QuickActions a Third in the default layout: side by side in
            // the grid, stacked here.
            val balance = onNodeWithTag(DashboardTestTags.Balance).getUnclippedBoundsInRoot()
            val quickActions = onNodeWithTag(DashboardTestTags.QuickActions).getUnclippedBoundsInRoot()

            (quickActions.top >= balance.bottom) shouldBe true
            (quickActions.left == balance.left) shouldBe true
        }
    }

    "at expanded width the default layout's first band is a hero beside the shortcuts" {
        runDashboardAt(DESKTOP_WIDTH_PX) {
            setContent { DashboardContent(state = CONTENT, onEvent = {}) }

            val balance = onNodeWithTag(DashboardTestTags.Balance).getUnclippedBoundsInRoot()
            val quickActions = onNodeWithTag(DashboardTestTags.QuickActions).getUnclippedBoundsInRoot()

            // Same row, shortcuts to the right of the hero.
            balance.sharesRowWith(quickActions) shouldBe true
            (quickActions.left >= balance.right) shouldBe true
            // Two thirds against a third — the hero is the wider of the two.
            (balance.width > quickActions.width) shouldBe true
        }
    }

    "a widget's span decides how much of the row it gets" {
        runDashboardAt(DESKTOP_WIDTH_PX) {
            val layout = DashboardLayoutConfig.DEFAULT
                .withSpan(DashboardWidgetType.Balance, DashboardWidgetSpan.Third)
            setContent { DashboardContent(state = CONTENT.copy(layout = layout), onEvent = {}) }

            val balance = onNodeWithTag(DashboardTestTags.Balance).getUnclippedBoundsInRoot()
            val quickActions = onNodeWithTag(DashboardTestTags.QuickActions).getUnclippedBoundsInRoot()

            // Both a third now, so neither is wider than the other by more than rounding.
            balance.sharesRowWith(quickActions) shouldBe true
            ((balance.width - quickActions.width).abs() < ROUNDING_SLACK) shouldBe true
        }
    }

    "a full-width widget keeps the row to itself" {
        runDashboardAt(DESKTOP_WIDTH_PX) {
            setContent { DashboardContent(state = CONTENT, onEvent = {}) }

            val spentMonth = onNodeWithTag(DashboardTestTags.SpentMonth).getUnclippedBoundsInRoot()
            val quickActions = onNodeWithTag(DashboardTestTags.QuickActions).getUnclippedBoundsInRoot()

            // Spent-this-month is the first Full-span widget in the default layout, so it starts
            // the band under the hero row rather than joining it. Read against a neighbour near the
            // top of the column rather than the last Full widget: the grid is lazy, and with the
            // donut and the spent-month band added the bottom of the default layout is no longer
            // composed at this window height.
            (spentMonth.top >= quickActions.bottom) shouldBe true
            (spentMonth.width > quickActions.width) shouldBe true
        }
    }

    "the period switch spans the whole grid rather than taking a cell" {
        runDashboardAt(DESKTOP_WIDTH_PX) {
            setContent { DashboardContent(state = CONTENT, onEvent = {}) }

            onNodeWithTag(DashboardTestTags.PeriodSwitch).assertIsDisplayed()
            val switch = onNodeWithTag(DashboardTestTags.PeriodSwitch).getUnclippedBoundsInRoot()
            val balance = onNodeWithTag(DashboardTestTags.Balance).getUnclippedBoundsInRoot()

            (balance.top >= switch.bottom) shouldBe true
        }
    }
})

/** A 411 dp phone — below the expanded breakpoint, so the dashboard stays a single column. */
private const val PHONE_WIDTH_PX = 411f

/** The 1360 dp desktop window the design is drawn at. */
private const val DESKTOP_WIDTH_PX = 1360f

/**
 * Tall enough for the whole default layout to be composed at once. The grid is lazy, so a widget
 * below the fold has no node to read bounds off — and these tests are about where widgets land
 * relative to each other, not about what fits a real window.
 */
private const val WINDOW_HEIGHT_PX = 1600f

/** Column widths are integer pixels, so two "equal" cells can differ by a rounding step. */
private val ROUNDING_SLACK = 2.dp

private val DpRect.width: Dp get() = right - left

/**
 * Whether two widgets were placed on the same grid row, asserted as a vertical overlap rather than
 * as equal tops: the tags sit inside each widget's own padding, which differs from widget to widget,
 * so two cards on one row do not start at the same y.
 */
private fun DpRect.sharesRowWith(other: DpRect): Boolean = top < other.bottom && other.top < bottom

private fun Dp.abs(): Dp = if (value < 0f) (-value).dp else this

@OptIn(ExperimentalTestApi::class)
private fun runDashboardAt(widthPx: Float, block: suspend SkikoComposeUiTest.() -> Unit) =
    runSkikoComposeUiTest(size = Size(widthPx, WINDOW_HEIGHT_PX), block = block)

/**
 * Two accounts and transfers on, so the quick-actions row draws — it is half of the first band, and
 * it stands itself down below two accounts.
 */
private val CONTENT = DashboardState.Content(
    accounts = List(2) { index ->
        AccountUi(
            id = AccountId("acc-$index"),
            name = "Account $index",
            formattedBalance = "€10.00",
            currency = "EUR",
        )
    },
    transactions = emptyList(),
    formattedTotalBalance = "€20.00",
    workspaceName = null,
    workspaceInitial = null,
    greeting = null,
    formattedTrendDelta = null,
    transferEnabled = true,
)
