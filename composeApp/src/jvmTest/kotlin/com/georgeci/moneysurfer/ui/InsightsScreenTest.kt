package com.georgeci.moneysurfer.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.domain.dashboard.DashboardPeriod
import com.georgeci.moneysurfer.feature.insights.InsightsCategoryUi
import com.georgeci.moneysurfer.feature.insights.InsightsContent
import com.georgeci.moneysurfer.feature.insights.InsightsEvent
import com.georgeci.moneysurfer.feature.insights.InsightsMerchantUi
import com.georgeci.moneysurfer.feature.insights.InsightsMonthUi
import com.georgeci.moneysurfer.feature.insights.InsightsPeriodUi
import com.georgeci.moneysurfer.feature.insights.InsightsState
import com.georgeci.moneysurfer.feature.insights.InsightsTestTags
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarTestTags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * Desktop UI cover for the analytics screen — see docs/testing/testing-strategy.md.
 *
 * The screen owns everything the view model does not: which of the three empty answers is drawn,
 * what the period pill says for each cadence, the fallback name for the uncategorized bucket, and
 * the a11y copy on a chart made of unlabelled rectangles. A view model spec only sees the
 * `InsightsState` going in, so none of that is visible to one.
 */
@OptIn(ExperimentalTestApi::class)
class InsightsScreenTest : StringSpec({

    "a period with spend draws the donut, the trend and the merchants" {
        runComposeUiTest {
            setContent { InsightsContent(state = CONTENT, onEvent = {}) }

            onNodeWithTag(InsightsTestTags.Donut).assertIsDisplayed()
            onNodeWithTag(InsightsTestTags.Trend).assertIsDisplayed()
            // Twice on purpose: the donut's legend names it, and so does the row under the chart.
            // The legend tops out at five entries, which is why the full list exists beside it.
            onAllNodesWithText("Rent").assertCountEquals(2)
            onNodeWithText("46% of spending").assertIsDisplayed()
        }
    }

    "the merchant list names who took the most, and how often" {
        runComposeUiTest {
            setContent { InsightsContent(state = CONTENT, onEvent = {}) }

            // Below the fold on a default window: a LazyColumn does not compose what it cannot show.
            onNodeWithTag(InsightsTestTags.List).performScrollToNode(hasText("Albert Heijn"))

            onNodeWithText("Albert Heijn").assertIsDisplayed()
            onNodeWithText("14 transactions").assertIsDisplayed()
        }
    }

    "a period whose spending names no merchant says so rather than showing an empty section" {
        runComposeUiTest {
            val state = CONTENT.copy(merchants = emptyList())
            setContent { InsightsContent(state = state, onEvent = {}) }

            onNodeWithTag(InsightsTestTags.List)
                .performScrollToNode(hasText("No period spending names a merchant."))

            onNodeWithText("No period spending names a merchant.").assertIsDisplayed()
        }
    }

    "the uncategorized bucket is named by the screen, not left blank" {
        runComposeUiTest {
            setContent { InsightsContent(state = CONTENT, onEvent = {}) }

            // Legend and row again — the bucket has no stored name, so the screen supplies both.
            onAllNodesWithText("Uncategorized").assertCountEquals(2)
        }
    }

    "a month column names both its figures, since the bars carry no numerals" {
        runComposeUiTest {
            setContent { InsightsContent(state = CONTENT, onEvent = {}) }

            onNodeWithContentDescription("Jul 2026: income €3,400.00, expense €1,842.50")
                .assertIsDisplayed()
        }
    }

    "an empty period says so without blaming the currency filter" {
        runComposeUiTest {
            setContent { InsightsContent(state = EMPTY, onEvent = {}) }

            onNodeWithTag(InsightsTestTags.Empty).assertIsDisplayed()
            onNodeWithTag(InsightsTestTags.Donut).assertDoesNotExist()
            // The trend spans six months, so it still has something to say about an empty one.
            onNodeWithTag(InsightsTestTags.Trend).assertIsDisplayed()
        }
    }

    "an empty period answers once, not three times over" {
        runComposeUiTest {
            setContent { InsightsContent(state = EMPTY, onEvent = {}) }

            // Merchants read the same window the breakdown does and their rows are a subset of it,
            // so an empty breakdown guarantees an empty merchant list. Saying so under the empty
            // state would answer the same question twice.
            onNodeWithText("Top merchants").assertDoesNotExist()
            onNodeWithText("No period spending names a merchant.").assertDoesNotExist()
        }
    }

    "the base-currency filter is named as the reason, and nothing else is drawn" {
        runComposeUiTest {
            setContent { InsightsContent(state = CURRENCY_FILTERED, onEvent = {}) }

            onNodeWithTag(InsightsTestTags.CurrencyFiltered).assertIsDisplayed()
            onNodeWithText("Only EUR is counted here").assertIsDisplayed()
            // A chart under a message explaining why there is nothing to chart reads as the
            // message being about something else.
            onNodeWithTag(InsightsTestTags.Trend).assertDoesNotExist()
            onNodeWithTag(InsightsTestTags.Donut).assertDoesNotExist()
            onNodeWithTag(InsightsTestTags.Empty).assertDoesNotExist()
        }
    }

    "excluded currencies are still named beside a period that does have spend" {
        runComposeUiTest {
            val state = CONTENT.copy(hiddenCurrencies = listOf("USD", "GBP"))
            setContent { InsightsContent(state = state, onEvent = {}) }

            onNodeWithText("Not counted: USD, GBP").assertIsDisplayed()
        }
    }

    "the pager states the month and its year" {
        runComposeUiTest {
            setContent { InsightsContent(state = CONTENT, onEvent = {}) }

            onNodeWithText("July").assertIsDisplayed()
            onNodeWithText("2026").assertIsDisplayed()
        }
    }

    "the pager states the ISO week and its week-year in Week cadence" {
        runComposeUiTest {
            val state = CONTENT.copy(
                mode = DashboardPeriod.Week,
                period = InsightsPeriodUi.Week(weekNumber = 30, weekYear = 2026),
            )
            setContent { InsightsContent(state = state, onEvent = {}) }

            onNodeWithText("Week 30").assertIsDisplayed()
            onNodeWithText("2026").assertIsDisplayed()
        }
    }

    "the arrows page the period" {
        runComposeUiTest {
            val events = mutableListOf<InsightsEvent>()
            setContent {
                InsightsContent(state = CONTENT.copy(canGoToNextPeriod = true), onEvent = events::add)
            }

            onNodeWithContentDescription("Previous period").performClick()
            onNodeWithContentDescription("Next period").performClick()

            events shouldContainExactly listOf(
                InsightsEvent.OnPreviousPeriodClick,
                InsightsEvent.OnNextPeriodClick,
            )
        }
    }

    "the forward arrow is inert on the period containing today" {
        runComposeUiTest {
            val events = mutableListOf<InsightsEvent>()
            setContent { InsightsContent(state = CONTENT, onEvent = events::add) }

            onNodeWithContentDescription("Next period").assertIsNotEnabled()

            events shouldBe emptyList()
        }
    }

    "the toolbar's back control reports one event" {
        runComposeUiTest {
            val events = mutableListOf<InsightsEvent>()
            setContent { InsightsContent(state = CONTENT, onEvent = events::add) }

            onNodeWithTag(SurferToolbarTestTags.Back).assertHasClickAction().performClick()

            events shouldContainExactly listOf(InsightsEvent.OnBackClick)
        }
    }

    "Loading renders the chrome and nothing under it" {
        runComposeUiTest {
            setContent { InsightsContent(state = InsightsState.Loading, onEvent = {}) }

            onNodeWithTag(InsightsTestTags.Root).assertIsDisplayed()
            onNodeWithTag(InsightsTestTags.List).assertDoesNotExist()
        }
    }
})

private val CONTENT = InsightsState.Content(
    mode = DashboardPeriod.Month,
    period = InsightsPeriodUi.Month(monthNumber = 7, year = 2026),
    canGoToNextPeriod = false,
    baseCurrency = "EUR",
    totalFormatted = "€1,842.50",
    categories = listOf(
        InsightsCategoryUi(
            id = "c-rent",
            name = "Rent",
            hue = null,
            spentFormatted = "€850.00",
            share = 0.46f,
        ),
        InsightsCategoryUi(
            id = "uncategorized",
            name = null,
            hue = null,
            spentFormatted = "€200.00",
            share = 0.11f,
        ),
    ),
    months = listOf(
        InsightsMonthUi(2, 2026, 320_000, 285_000, "€3,200.00", "€2,850.00"),
        InsightsMonthUi(3, 2026, 320_000, 410_000, "€3,200.00", "€4,100.00"),
        InsightsMonthUi(4, 2026, 340_000, 260_000, "€3,400.00", "€2,600.00"),
        InsightsMonthUi(5, 2026, 340_000, 275_000, "€3,400.00", "€2,750.00"),
        // An empty month: the trend has to keep its column rather than shrink the chart.
        InsightsMonthUi(6, 2026, 0, 0, "€0.00", "€0.00"),
        InsightsMonthUi(7, 2026, 340_000, 184_250, "€3,400.00", "€1,842.50"),
    ),
    merchants = listOf(
        InsightsMerchantUi(merchant = "Albert Heijn", spentFormatted = "€312.10", transactionCount = 14),
    ),
    hiddenCurrencies = emptyList(),
    hiddenByBaseCurrency = false,
)

/** Nothing spent, and nothing the currency filter could be blamed for. */
private val EMPTY = CONTENT.copy(
    totalFormatted = "€0.00",
    categories = emptyList(),
    merchants = emptyList(),
)

/** The mixed-currency workspace: every expense filtered out, and the screen has to say so. */
private val CURRENCY_FILTERED = EMPTY.copy(
    hiddenCurrencies = listOf("USD", "GBP"),
    hiddenByBaseCurrency = true,
)
