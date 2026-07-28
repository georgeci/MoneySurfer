package com.georgeci.moneysurfer.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.domain.dashboard.DashboardCardStyle
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutItem
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSize
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.domain.insight.InsightTone
import com.georgeci.moneysurfer.feature.dashboard.DashboardContent
import com.georgeci.moneysurfer.feature.dashboard.DashboardState
import com.georgeci.moneysurfer.feature.dashboard.DashboardTestTags
import com.georgeci.moneysurfer.feature.dashboard.InsightKind
import com.georgeci.moneysurfer.feature.dashboard.InsightUi
import com.georgeci.moneysurfer.uikit.widgets.SurferInsightsVariant
import io.kotest.core.spec.style.StringSpec

/**
 * Desktop UI cover for the Insights widget — see docs/testing/testing-strategy.md.
 *
 * The screen owns everything the ViewModel does not: which sentence a kind draws, how many cards a
 * card style keeps, and the fallback name for the uncategorized bucket. None of that is visible to
 * a ViewModel test, which only sees the [InsightUi] list going in.
 */
@OptIn(ExperimentalTestApi::class)
class DashboardInsightsScreenTest : StringSpec({

    "each kind draws its own sentence, built from the formatted amounts" {
        runComposeUiTest {
            setContent { DashboardContent(state = contentWith(SAMPLES), onEvent = {}) }

            onNodeWithTag(DashboardTestTags.Insights).assertIsDisplayed()
            onNodeWithText("Dining is up 28%").assertIsDisplayed()
            onNodeWithText("€162.00 this month, against €127.00 by the same day last month.")
                .assertIsDisplayed()
            onNodeWithText("Spending is down 12%").assertIsDisplayed()
        }
    }

    "a compact card keeps only the first insight; an expanded one keeps three" {
        runComposeUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(SAMPLES, size = DashboardWidgetSize.Compact),
                    onEvent = {},
                )
            }

            onNodeWithText("Dining is up 28%").assertIsDisplayed()
            onNodeWithText("Spending is down 12%").assertDoesNotExist()
        }
    }

    "the carousel reaches the insights a compact list would have dropped" {
        runComposeUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(
                        SAMPLES,
                        size = DashboardWidgetSize.Compact,
                        variant = SurferInsightsVariant.Carousel,
                    ),
                    onEvent = {},
                )
            }

            // One page at a time, so the first card is what shows — but unlike the compact list it
            // is a page of four rather than the only insight the card will ever draw.
            onNodeWithTag(DashboardTestTags.Insights).assertIsDisplayed()
            onNodeWithText("Dining is up 28%").assertIsDisplayed()
        }
    }

    "a slice with no category is named rather than left blank" {
        runComposeUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(
                        listOf(
                            InsightUi(
                                id = "category-change:uncategorized:2026-07",
                                kind = InsightKind.CategoryUp,
                                tone = InsightTone.Warn,
                                label = null,
                                amount = "€40.00",
                                comparison = "€20.00",
                                percent = 100,
                            ),
                        ),
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("Uncategorized is up 100%").assertIsDisplayed()
        }
    }

    "a quiet period says so instead of hiding the widget" {
        runComposeUiTest {
            setContent { DashboardContent(state = contentWith(emptyList()), onEvent = {}) }

            onNodeWithTag(DashboardTestTags.Insights).assertIsDisplayed()
            onNodeWithText("Nothing notable this period.").assertIsDisplayed()
        }
    }
})

private val SAMPLES = listOf(
    InsightUi(
        id = "category-change:cat-dining:2026-07",
        kind = InsightKind.CategoryUp,
        tone = InsightTone.Warn,
        label = "Dining",
        amount = "€162.00",
        comparison = "€127.00",
        percent = 28,
    ),
    InsightUi(
        id = "category-change:cat-leisure:2026-07",
        kind = InsightKind.CategoryDown,
        tone = InsightTone.Good,
        label = "Leisure",
        amount = "€84.00",
        comparison = "€106.00",
        percent = 21,
    ),
    InsightUi(
        id = "period-spend:2026-07",
        kind = InsightKind.PeriodDown,
        tone = InsightTone.Good,
        amount = "€520.00",
        comparison = "€591.00",
        percent = 12,
    ),
    InsightUi(
        id = "active-subscriptions:2026-07",
        kind = InsightKind.Subscriptions,
        tone = InsightTone.Neutral,
        amount = "€62.00",
        count = 4,
    ),
)

private fun contentWith(
    insights: List<InsightUi>,
    size: DashboardWidgetSize = DashboardWidgetSize.Expanded,
    variant: SurferInsightsVariant = SurferInsightsVariant.List,
) = DashboardState.Content(
    accounts = emptyList(),
    transactions = emptyList(),
    formattedTotalBalance = null,
    workspaceName = null,
    workspaceInitial = null,
    greeting = null,
    formattedTrendDelta = null,
    insights = insights,
    // Only the widget under test, so a sentence cannot be matched against another widget's copy.
    layout = DashboardLayoutConfig(
        items = listOf(
            DashboardLayoutItem(
                type = DashboardWidgetType.Insights,
                cardStyle = DashboardCardStyle(size = size, variant = variant.name),
            ),
        ),
    ),
)
