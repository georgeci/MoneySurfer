package com.georgeci.moneysurfer.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.georgeci.moneysurfer.domain.dashboard.DashboardCardStyle
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutItem
import com.georgeci.moneysurfer.domain.dashboard.DashboardPeriod
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSize
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.domain.insight.SpendTrend
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.domain.model.BurnRatePace
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.feature.dashboard.AccountUi
import com.georgeci.moneysurfer.feature.dashboard.BudgetSummaryUi
import com.georgeci.moneysurfer.feature.dashboard.BurnRateDayUi
import com.georgeci.moneysurfer.feature.dashboard.BurnRateUi
import com.georgeci.moneysurfer.feature.dashboard.CategoryCapUi
import com.georgeci.moneysurfer.feature.dashboard.CategorySpendUi
import com.georgeci.moneysurfer.feature.dashboard.DashboardContent
import com.georgeci.moneysurfer.feature.dashboard.DashboardEvent
import com.georgeci.moneysurfer.feature.dashboard.DashboardState
import com.georgeci.moneysurfer.feature.dashboard.DashboardTestTags
import com.georgeci.moneysurfer.feature.dashboard.SafeToSpendUi
import com.georgeci.moneysurfer.feature.dashboard.SpentMonthDeltaUi
import com.georgeci.moneysurfer.feature.dashboard.SpentMonthUi
import com.georgeci.moneysurfer.feature.dashboard.UpcomingRecurringUi
import com.georgeci.moneysurfer.uikit.widgets.SurferSpentByCategoryVariant
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * Desktop UI cover for the dashboard widgets whose behaviour lives on the screen rather than in
 * the state — see docs/testing/testing-strategy.md.
 *
 * The quick-actions row decides for itself whether to draw at all, safe-to-spend decides between
 * its number and its "set a budget" state, and burn rate decides whether its projection carries a
 * verdict. None of those is visible to a ViewModel test: they are about what reaches the screen,
 * not about what the state holds.
 */
@OptIn(ExperimentalTestApi::class)
class DashboardScreenStateTest : StringSpec({

    "the quick-actions row draws both shortcuts once a transfer is possible" {
        runPhoneUiTest {
            setContent {
                DashboardContent(state = contentWith(accounts = 2, transferEnabled = true), onEvent = {})
            }

            onNodeWithTag(DashboardTestTags.QuickActions).assertIsDisplayed()
            onNodeWithText(ADD_TRANSACTION).assertIsDisplayed()
            onNodeWithText(TRANSFER).assertIsDisplayed()
        }
    }

    "a build without transfers draws no quick-actions row" {
        runPhoneUiTest {
            setContent {
                DashboardContent(state = contentWith(accounts = 2, transferEnabled = false), onEvent = {})
            }

            // Half the row is a Transfer button that would land on an expense form here.
            onNodeWithTag(DashboardTestTags.QuickActions).assertDoesNotExist()
        }
    }

    "a single account draws no quick-actions row — there is nowhere to transfer to" {
        runPhoneUiTest {
            setContent {
                DashboardContent(state = contentWith(accounts = 1, transferEnabled = true), onEvent = {})
            }

            onNodeWithTag(DashboardTestTags.QuickActions).assertDoesNotExist()
        }
    }

    "the two shortcuts ask for the plain form and the transfer form respectively" {
        runPhoneUiTest {
            val events = mutableListOf<DashboardEvent>()
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true),
                    onEvent = { events += it },
                )
            }

            onNodeWithText(ADD_TRANSACTION).performClick()
            onNodeWithText(TRANSFER).performClick()
            waitForIdle()

            events shouldContainExactly listOf(
                DashboardEvent.OnAddTransactionClick,
                DashboardEvent.OnTransferClick,
            )
        }
    }

    "the balance card states the month delta as a sentence" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true).copy(
                        formattedTrendDelta = TREND_DELTA,
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("$TREND_DELTA this month").assertIsDisplayed()
        }
    }

    "a converted total shows both its trend and how old the rates behind it are" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true).copy(
                        formattedTrendDelta = TREND_DELTA,
                        ratesAsOf = RATES_DATE,
                    ),
                    onEvent = {},
                )
            }

            // The two used to share one footnote slot, where the staleness note won and the trend
            // was never drawn for a multi-currency workspace.
            onNodeWithText("$TREND_DELTA this month").assertIsDisplayed()
            onNodeWithText(RATES_NOTE).assertIsDisplayed()
        }
    }

    "an empty balance drops the trend rather than pairing a delta with a dash" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 0, transferEnabled = true).copy(
                        formattedTotalBalance = null,
                        formattedTrendDelta = TREND_DELTA,
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("$TREND_DELTA this month").assertDoesNotExist()
            onNodeWithText(BALANCE_EMPTY).assertIsDisplayed()
        }
    }

    "with no budget the safe-to-spend card still draws, offering the way out of its empty state" {
        runPhoneUiTest {
            val events = mutableListOf<DashboardEvent>()
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true),
                    onEvent = { events += it },
                )
            }

            onNodeWithTag(DashboardTestTags.SafeToSpend).assertIsDisplayed()
            onNodeWithTag(DashboardTestTags.SafeToSpendSetBudget).performClick()
            waitForIdle()

            events shouldContainExactly listOf(DashboardEvent.OnSetBudgetClick)
        }
    }

    "with a budget the card shows the remainder and drops the set-a-budget link" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true).copy(
                        safeToSpend = safeToSpendUi(),
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText(SAFE_TO_SPEND_REMAINDER).assertIsDisplayed()
            onNodeWithText(SAFE_TO_SPEND_DAYS_LEFT).assertIsDisplayed()
            onNodeWithText("of $SAFE_TO_SPEND_LIMIT · Everyday").assertIsDisplayed()
            // The link only belongs to the empty state — there is a budget to read now.
            onNodeWithTag(DashboardTestTags.SafeToSpendSetBudget).assertDoesNotExist()
        }
    }

    "an overspent budget says so in the caption rather than reading as headroom" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true).copy(
                        safeToSpend = safeToSpendUi(
                            remaining = "−€120.00",
                            status = BudgetStatus.OVER,
                            progress = 1.07f,
                        ),
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("−€120.00").assertIsDisplayed()
            // "of €1,800.00" would read as money still available.
            onNodeWithText("over $SAFE_TO_SPEND_LIMIT · Everyday").assertIsDisplayed()
        }
    }

    "a budget past its alert threshold is still headroom, not an overspend" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true).copy(
                        safeToSpend = safeToSpendUi(status = BudgetStatus.WARN, progress = 0.88f),
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("of $SAFE_TO_SPEND_LIMIT · Everyday").assertIsDisplayed()
        }
    }

    "the period switch sits above the widgets with the current span selected" {
        runPhoneUiTest {
            setContent {
                DashboardContent(state = contentWith(accounts = 2, transferEnabled = true), onEvent = {})
            }

            onNodeWithTag(DashboardTestTags.PeriodSwitch).assertIsDisplayed()
            onNodeWithTag(DashboardTestTags.periodOption(DashboardPeriod.Month)).assertIsSelected()
            onNodeWithTag(DashboardTestTags.periodOption(DashboardPeriod.Week)).assertIsNotSelected()
        }
    }

    "picking the other span asks the view model for it rather than deciding on screen" {
        runPhoneUiTest {
            val events = mutableListOf<DashboardEvent>()
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true),
                    onEvent = { events += it },
                )
            }

            onNodeWithTag(DashboardTestTags.periodOption(DashboardPeriod.Week)).performClick()
            waitForIdle()

            events shouldContainExactly listOf(DashboardEvent.OnPeriodChange(DashboardPeriod.Week))
        }
    }

    "a layout with no period-scoped widget draws no period switch" {
        runPhoneUiTest {
            val layout = DashboardWidgetType.entries
                .filter { it.isPeriodScoped }
                .fold(DashboardLayoutConfig.DEFAULT) { acc, type -> acc.withWidgetEnabled(type, enabled = false) }
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true).copy(layout = layout),
                    onEvent = {},
                )
            }

            // A Week/Month control with nothing under it to re-read would read as broken.
            onNodeWithTag(DashboardTestTags.PeriodSwitch).assertDoesNotExist()
        }
    }

    "the burn-rate card draws its pace and projection, and says so when a budget judges them" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true).copy(
                        burnRate = burnRateUi(pace = BurnRatePace.OffPace),
                        layout = BURN_RATE_ONLY,
                    ),
                    onEvent = {},
                )
            }

            onNodeWithTag(DashboardTestTags.BurnRate).assertIsDisplayed()
            onNodeWithText("$BURN_RATE_AVERAGE a day").assertIsDisplayed()
            onNodeWithText("$BURN_RATE_PROJECTION projected by month end").assertIsDisplayed()
            onNodeWithText("Off pace").assertIsDisplayed()
        }
    }

    "the spent-this-month card draws the amount, the cap it is under and the month before it" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true).copy(
                        spentMonth = SpentMonthUi(
                            spentFormatted = SPENT_MONTH_AMOUNT,
                            capFormatted = SPENT_MONTH_CAP,
                            progress = 0.65f,
                            delta = SpentMonthDeltaUi(trend = SpendTrend.Down, percent = 18),
                        ),
                        layout = SPENT_MONTH_ONLY,
                    ),
                    onEvent = {},
                )
            }

            onNodeWithTag(DashboardTestTags.SpentMonth).assertIsDisplayed()
            onNodeWithText(SPENT_MONTH_AMOUNT).assertIsDisplayed()
            onNodeWithText("of $SPENT_MONTH_CAP budget").assertIsDisplayed()
            onNodeWithText("\u221218% vs last").assertIsDisplayed()
        }
    }

    "a month that outspent the last one is labelled as a rise" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true).copy(
                        spentMonth = spentMonthUi(SpentMonthDeltaUi(trend = SpendTrend.Up, percent = 24)),
                        layout = SPENT_MONTH_ONLY,
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("+24% vs last").assertIsDisplayed()
        }
    }

    "a month in line with the last one says so instead of printing a zero" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true).copy(
                        spentMonth = spentMonthUi(SpentMonthDeltaUi(trend = SpendTrend.Flat, percent = 0)),
                        layout = SPENT_MONTH_ONLY,
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("In line with last").assertIsDisplayed()
            onNodeWithText("+0% vs last").assertDoesNotExist()
        }
    }

    "the compact spent-this-month card is shorter than the expanded one" {
        var expandedHeight = 0f
        var compactHeight = 0f
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true)
                        .copy(spentMonth = spentMonthUi(), layout = SPENT_MONTH_ONLY),
                    onEvent = {},
                )
            }
            val bounds = onNodeWithTag(DashboardTestTags.SpentMonth).getUnclippedBoundsInRoot()
            expandedHeight = (bounds.bottom - bounds.top).value
        }
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true)
                        .copy(spentMonth = spentMonthUi(), layout = SPENT_MONTH_ONLY_COMPACT),
                    onEvent = {},
                )
            }
            val bounds = onNodeWithTag(DashboardTestTags.SpentMonth).getUnclippedBoundsInRoot()
            compactHeight = (bounds.bottom - bounds.top).value
        }

        // The size the user picked has to change the card's footprint, not only its typography.
        (compactHeight < expandedHeight) shouldBe true
    }

    "with no budget the spent-this-month card says so rather than inventing a limit" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true).copy(
                        spentMonth = SpentMonthUi(
                            spentFormatted = SPENT_MONTH_AMOUNT,
                            capFormatted = null,
                            progress = 0f,
                        ),
                        layout = SPENT_MONTH_ONLY,
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("No budget set").assertIsDisplayed()
        }
    }

    "before the month's figure resolves the card claims nothing about a budget" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true)
                        .copy(spentMonth = null, layout = SPENT_MONTH_ONLY),
                    onEvent = {},
                )
            }

            // A workspace the device has not pulled yet may well have a budget — saying "No budget
            // set" here would state something about the user's money the app has not read.
            onNodeWithTag(DashboardTestTags.SpentMonth).assertIsDisplayed()
            onNodeWithText("No budget set").assertDoesNotExist()
        }
    }

    "with no budget the burn-rate card still draws the projection, minus the verdict" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true).copy(
                        burnRate = burnRateUi(),
                        layout = BURN_RATE_ONLY,
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("$BURN_RATE_PROJECTION projected by month end").assertIsDisplayed()
            // Neither verdict belongs on a projection with no cap to miss.
            onNodeWithText("On track").assertDoesNotExist()
            onNodeWithText("Off pace").assertDoesNotExist()
        }
    }
    "the budgets widget draws a row per budget, with its status and what is left" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = budgetsOnly(
                        listOf(
                            budgetUi(),
                            budgetUi(
                                id = "b-2",
                                name = "Transport",
                                status = BudgetStatus.OK,
                                spent = "€120.00",
                                limit = "€300.00",
                                remainder = "€180.00",
                            ),
                        ),
                    ),
                    onEvent = {},
                )
            }

            onNodeWithTag(DashboardTestTags.Budgets).assertIsDisplayed()
            onNodeWithText(BUDGET_NAME).assertIsDisplayed()
            onNodeWithText("Transport").assertIsDisplayed()
            onNodeWithText("$BUDGET_SPENT of $BUDGET_LIMIT").assertIsDisplayed()
            onNodeWithText("$BUDGET_REMAINDER left").assertIsDisplayed()
            onNodeWithText("Near limit").assertIsDisplayed()
        }
    }

    "a compact budgets card keeps only the most pressing budget" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = budgetsOnly(
                        listOf(budgetUi(), budgetUi(id = "b-2", name = "Transport")),
                        size = DashboardWidgetSize.Compact,
                    ),
                    onEvent = {},
                )
            }

            // The card is one row tall at this size; the rest of the list is behind "See all".
            onNodeWithText(BUDGET_NAME).assertIsDisplayed()
            onNodeWithText("Transport").assertDoesNotExist()
        }
    }

    "an overspent budget row says how far over it is, not how much is left" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = budgetsOnly(listOf(budgetUi(status = BudgetStatus.OVER, progress = 1.14f))),
                    onEvent = {},
                )
            }

            onNodeWithText("$BUDGET_REMAINDER over").assertIsDisplayed()
            onNodeWithText("Over").assertIsDisplayed()
        }
    }

    "with no budgets the card still draws, pointing at the budgets screen" {
        runPhoneUiTest {
            val events = mutableListOf<DashboardEvent>()
            setContent {
                DashboardContent(state = budgetsOnly(emptyList()), onEvent = { events += it })
            }

            onNodeWithText("No budgets yet").assertIsDisplayed()
            onNodeWithTag(DashboardTestTags.BudgetsSeeAll).performClick()
            waitForIdle()

            events shouldContainExactly listOf(DashboardEvent.OnSeeAllBudgetsClick)
        }
    }

    "tapping a budget row asks for that budget" {
        runPhoneUiTest {
            val events = mutableListOf<DashboardEvent>()
            setContent {
                DashboardContent(state = budgetsOnly(listOf(budgetUi())), onEvent = { events += it })
            }

            onNodeWithText(BUDGET_NAME).performClick()
            waitForIdle()

            events shouldContainExactly listOf(DashboardEvent.OnBudgetClick(BudgetId("b-1")))
        }
    }
    "every spent-by-category variant draws its rows rather than any of them measuring to nothing" {
        SurferSpentByCategoryVariant.entries.forEach { variant ->
            runPhoneUiTest {
                setContent {
                    DashboardContent(
                        state = spentByCategoryState(variant),
                        onEvent = {},
                    )
                }

                // Five layouts over one list: a branch that crashes or collapses shows up here
                // and nowhere else, because none of it is visible to a ViewModel test.
                onNodeWithTag(DashboardTestTags.SpentByCategory).assertIsDisplayed()
                // All-nodes rather than one: Ring prints the top amount in the hole and again on
                // the legend row that carries that category's status word.
                onAllNodesWithText(GROCERIES_SPEND).onFirst().assertIsDisplayed()
            }
        }
    }

    "a capped category says what its meter measures, and an uncapped one says the share instead" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = spentByCategoryState(SurferSpentByCategoryVariant.Bar),
                    onEvent = {},
                )
            }

            // The captions are what keep a cap meter and a share meter from looking alike.
            onNodeWithText("$GROCERIES_SPEND of $GROCERIES_CAP").assertIsDisplayed()
            onNodeWithText("Near limit").assertIsDisplayed()
            onNodeWithText("60% of spending").assertIsDisplayed()
        }
    }

    "an overspent category states the cap it passed rather than reading as headroom" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = spentByCategoryState(SurferSpentByCategoryVariant.Bar).copy(
                        spentByCategory = listOf(
                            categorySpendUi(
                                name = "Groceries",
                                cap = CategoryCapUi(
                                    limitFormatted = GROCERIES_CAP,
                                    status = BudgetStatus.OVER,
                                    progress = 1.2f,
                                ),
                            ),
                        ),
                    ),
                    onEvent = {},
                )
            }

            // "€142.10 of €150" would read as money still available.
            onNodeWithText("over $GROCERIES_CAP").assertIsDisplayed()
            onNodeWithText("Over").assertIsDisplayed()
        }
    }

    "a month with no spend keeps the card and says so, rather than leaving a gap" {
        runPhoneUiTest {
            setContent {
                // The card alone, like the variant cases: the default layout is long enough that
                // this one would otherwise sit below the fold and never compose.
                DashboardContent(
                    state = spentByCategoryState(SurferSpentByCategoryVariant.Bar)
                        .copy(spentByCategory = emptyList()),
                    onEvent = {},
                )
            }

            onNodeWithTag(DashboardTestTags.SpentByCategory).assertIsDisplayed()
            onNodeWithText("Nothing spent yet").assertIsDisplayed()
        }
    }

    "a slice with no category is named on the screen, not left blank" {
        runPhoneUiTest {
            setContent {
                // The card alone: the default layout now carries ten widgets, so this one sits
                // below the fold and never composes when the whole column is drawn.
                DashboardContent(
                    state = spentByCategoryState(SurferSpentByCategoryVariant.Bar).copy(
                        spentByCategory = listOf(
                            categorySpendUi(categoryId = null, name = null, hue = null, share = 1f),
                        ),
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("Uncategorized").assertIsDisplayed()
        }
    }

    "the donut names its segments and states the period total in the middle" {
        runPhoneUiTest {
            setContent { DashboardContent(state = categoriesDonutState(), onEvent = {}) }

            onNodeWithTag(DashboardTestTags.CategoriesDonut).assertIsDisplayed()
            onNodeWithText("Groceries").assertIsDisplayed()
            onNodeWithText("Rent").assertIsDisplayed()
            // The centre is the period's own total, not the top segment repeated from the legend.
            onNodeWithText("Spent").assertIsDisplayed()
            onNodeWithText(DONUT_TOTAL).assertIsDisplayed()
        }
    }

    "the hero donut legend keeps five rows and the compact one three" {
        val segments = List(SIX_SEGMENTS) { index ->
            categorySpendUi(categoryId = "c-$index", name = "Category $index", share = 1f / SIX_SEGMENTS)
        }

        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = categoriesDonutState(DashboardWidgetSize.Expanded).copy(spentByCategory = segments),
                    onEvent = {},
                )
            }

            onNodeWithText("Category 4").assertIsDisplayed()
            onNodeWithText("Category 5").assertDoesNotExist()
        }

        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = categoriesDonutState(DashboardWidgetSize.Compact).copy(spentByCategory = segments),
                    onEvent = {},
                )
            }

            onNodeWithText("Category 2").assertIsDisplayed()
            onNodeWithText("Category 3").assertDoesNotExist()
        }
    }

    "spend with no category is a segment of the donut with a name, not a gap" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = categoriesDonutState().copy(
                        spentByCategory = listOf(
                            categorySpendUi(categoryId = null, name = null, hue = null, share = 1f),
                        ),
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("Uncategorized").assertIsDisplayed()
        }
    }

    "a period with no spend keeps the donut and says so, rather than leaving a gap" {
        runPhoneUiTest {
            setContent {
                DashboardContent(
                    state = categoriesDonutState().copy(spentByCategory = emptyList(), spentByCategoryTotal = null),
                    onEvent = {},
                )
            }

            onNodeWithTag(DashboardTestTags.CategoriesDonut).assertIsDisplayed()
            onNodeWithText("No spend").assertIsDisplayed()
        }
    }

    "the upcoming card names the two nearest days rather than printing their dates" {
        runPhoneUiTest {
            setContent { DashboardContent(state = upcomingState(), onEvent = {}) }

            onNodeWithTag(DashboardTestTags.Recurring).assertIsDisplayed()
            onNodeWithText("Today").assertIsDisplayed()
            onNodeWithText("Tomorrow").assertIsDisplayed()
            // Anything further out is a date, which needs no locale rules to read.
            onNodeWithText("2026-05-02").assertIsDisplayed()
            onNodeWithText(RENT_DUE).assertIsDisplayed()
        }
    }

    "a workspace with nothing scheduled keeps the card and says so" {
        runPhoneUiTest {
            setContent {
                DashboardContent(state = upcomingState().copy(upcoming = emptyList()), onEvent = {})
            }

            onNodeWithTag(DashboardTestTags.Recurring).assertIsDisplayed()
            onNodeWithText("Nothing scheduled").assertIsDisplayed()
        }
    }
})

/**
 * Every case here mounts the dashboard in a phone-sized window.
 *
 * The default test surface is 1024 dp wide, which is expanded width — the dashboard lays out as a
 * grid there, and a widget in a narrow cell draws its Compact treatment rather than its Expanded
 * one. Nothing in this spec is about the grid, so pinning the window keeps these assertions about
 * what a widget decides to draw instead of about how wide the test surface happens to be.
 * `DashboardGridScreenTest` covers the layout itself.
 */
@OptIn(ExperimentalTestApi::class)
private fun runPhoneUiTest(block: suspend SkikoComposeUiTest.() -> Unit) =
    runSkikoComposeUiTest(size = Size(PHONE_WIDTH_PX, PHONE_HEIGHT_PX), block = block)

/** A 411 × 891 phone at 1x — the artboard the widgets' Expanded treatments are drawn for. */
private const val PHONE_WIDTH_PX = 411f
private const val PHONE_HEIGHT_PX = 891f

private const val ADD_TRANSACTION = "Add transaction"
private const val TRANSFER = "Transfer"
private const val TREND_DELTA = "+€412.00"
private const val RATES_DATE = "2026-07-29"
private const val RATES_NOTE = "Converted at rates from $RATES_DATE · exchangerate-api.com"
private const val BALANCE_EMPTY = "Add your first account to see balance."
private const val SAFE_TO_SPEND_REMAINDER = "€642.30"
private const val SAFE_TO_SPEND_LIMIT = "€1,800.00"
private const val SAFE_TO_SPEND_DAYS_LEFT = "12 days left"

private fun safeToSpendUi(
    remaining: String = SAFE_TO_SPEND_REMAINDER,
    status: BudgetStatus = BudgetStatus.OK,
    progress: Float = 0.64f,
) = SafeToSpendUi(
    budgetName = "Everyday",
    remainingFormatted = remaining,
    spentFormatted = "€1,157.70",
    limitFormatted = SAFE_TO_SPEND_LIMIT,
    perDayFormatted = "€53.52",
    daysLeft = 12,
    progress = progress,
    paceFraction = 0.6f,
    status = status,
)

private const val BURN_RATE_AVERAGE = "€42.10"
private const val BURN_RATE_PROJECTION = "€1,263.00"

private const val SPENT_MONTH_AMOUNT = "\u20ac1,173.69"
private const val SPENT_MONTH_CAP = "\u20ac1,800.00"

/** The spent-this-month card alone, for the same reason [BURN_RATE_ONLY] pins the burn rate. */
private val SPENT_MONTH_ONLY = DashboardLayoutConfig(
    items = listOf(DashboardLayoutItem(type = DashboardWidgetType.SpentMonth)),
)

/** The same card at the other density, for the footprint comparison. */
private val SPENT_MONTH_ONLY_COMPACT = DashboardLayoutConfig(
    items = listOf(
        DashboardLayoutItem(type = DashboardWidgetType.SpentMonth, cardStyle = DashboardCardStyle.COMPACT),
    ),
)

private fun spentMonthUi(delta: SpentMonthDeltaUi? = null) = SpentMonthUi(
    spentFormatted = SPENT_MONTH_AMOUNT,
    capFormatted = SPENT_MONTH_CAP,
    progress = 0.65f,
    delta = delta,
)

/**
 * The burn-rate card alone, so its projection and pace rows are composed rather than scrolled off
 * the bottom of the dashboard column — the same reason the spent-by-category specs pin a layout.
 */
private val BURN_RATE_ONLY = DashboardLayoutConfig(
    items = listOf(DashboardLayoutItem(type = DashboardWidgetType.BurnRate)),
)

private fun burnRateUi(pace: BurnRatePace? = null) = BurnRateUi(
    averageFormatted = BURN_RATE_AVERAGE,
    projectedFormatted = BURN_RATE_PROJECTION,
    weekTotalFormatted = "€294.70",
    busiestDayFormatted = "€96.00",
    days = List(7) { index ->
        BurnRateDayUi(dayOfMonth = 22 + index, fraction = index / 6f, isToday = index == 6)
    },
    pace = pace,
)

private const val BUDGET_NAME = "Groceries"
private const val BUDGET_SPENT = "€312.40"
private const val BUDGET_LIMIT = "€400.00"
private const val BUDGET_REMAINDER = "€87.60"

private fun budgetUi(
    id: String = "b-1",
    name: String = BUDGET_NAME,
    status: BudgetStatus = BudgetStatus.WARN,
    progress: Float = 0.78f,
    spent: String = BUDGET_SPENT,
    limit: String = BUDGET_LIMIT,
    remainder: String = BUDGET_REMAINDER,
) = BudgetSummaryUi(
    id = BudgetId(id),
    name = name,
    spentFormatted = spent,
    limitFormatted = limit,
    remainderFormatted = remainder,
    progress = progress,
    alertFraction = 0.8f,
    status = status,
)

/**
 * A dashboard showing nothing but the budgets card, so the assertions are about that card rather
 * than about how far down the column it lands in a desktop window.
 */
private fun budgetsOnly(
    budgets: List<BudgetSummaryUi>,
    size: DashboardWidgetSize = DashboardWidgetSize.Expanded,
) = contentWith(accounts = 2, transferEnabled = true).copy(
    budgets = budgets,
    layout = DashboardLayoutConfig(
        items = listOf(
            DashboardLayoutItem(DashboardWidgetType.Budgets, cardStyle = DashboardCardStyle(size)),
        ),
    ),
)

private const val GROCERIES_SPEND = "€142.10"
private const val GROCERIES_CAP = "€150.00"
private const val RENT_SPEND = "€760.00"

private fun categorySpendUi(
    categoryId: String? = "c-1",
    name: String? = "Groceries",
    hue: Int? = 35,
    spent: String = GROCERIES_SPEND,
    share: Float = 0.4f,
    cap: CategoryCapUi? = null,
) = CategorySpendUi(
    categoryId = categoryId,
    name = name,
    hue = hue,
    spentFormatted = spent,
    share = share,
    cap = cap,
)

/**
 * One capped category and one uncapped one — the pair is what makes the two captions, and the two
 * meter colours, distinguishable in a single render.
 */
private fun spentByCategoryState(variant: SurferSpentByCategoryVariant) =
    contentWith(accounts = 2, transferEnabled = true).copy(
        spentByCategory = listOf(
            categorySpendUi(
                categoryId = "c-groceries",
                name = "Groceries",
                cap = CategoryCapUi(
                    limitFormatted = GROCERIES_CAP,
                    status = BudgetStatus.WARN,
                    progress = 0.95f,
                ),
            ),
            categorySpendUi(
                categoryId = "c-rent",
                name = "Rent",
                hue = 258,
                spent = RENT_SPEND,
                share = 0.6f,
            ),
        ),
        // The card alone, so its lower rows are composed rather than scrolled off the bottom of
        // the dashboard column — the captions are the point of these assertions.
        layout = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(
                    type = DashboardWidgetType.SpentByCategory,
                    cardStyle = DashboardCardStyle(variant = variant.name),
                ),
            ),
        ),
    )

/**
 * The donut alone, for the same reason [spentByCategoryState] draws its card alone: the whole
 * default layout is taller than the test window, and a widget below the fold never composes.
 */
private fun categoriesDonutState(size: DashboardWidgetSize = DashboardWidgetSize.Expanded) =
    contentWith(accounts = 2, transferEnabled = true).copy(
        spentByCategory = listOf(
            categorySpendUi(categoryId = "c-groceries", name = "Groceries", share = 0.4f),
            categorySpendUi(categoryId = "c-rent", name = "Rent", hue = 258, spent = RENT_SPEND, share = 0.6f),
        ),
        spentByCategoryTotal = DONUT_TOTAL,
        layout = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(
                    type = DashboardWidgetType.CategoriesDonut,
                    cardStyle = DashboardCardStyle(size = size),
                ),
            ),
        ),
    )

private const val DONUT_TOTAL = "€902.10"

/** One more than the hero legend keeps, so both row counts are a visible cut rather than the list. */
private const val SIX_SEGMENTS = 6

private fun contentWith(accounts: Int, transferEnabled: Boolean) = DashboardState.Content(
    accounts = List(accounts) { index ->
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
    transferEnabled = transferEnabled,
)

/**
 * The upcoming card alone, for the same reason [categoriesDonutState] draws the donut alone: the
 * whole default layout is taller than the test window, and a widget below the fold never composes.
 */
private fun upcomingState() = contentWith(accounts = 2, transferEnabled = true).copy(
    upcoming = listOf(
        upcomingUi(id = "coffee", days = 0, iso = "2026-04-30", amount = "€9.99"),
        upcomingUi(id = "rent", days = 1, iso = "2026-05-01", amount = RENT_DUE),
        upcomingUi(id = "gym", days = 2, iso = "2026-05-02", amount = "€32.00"),
    ),
    layout = DashboardLayoutConfig(
        items = listOf(DashboardLayoutItem(type = DashboardWidgetType.Recurring)),
    ),
)

private fun upcomingUi(id: String, days: Int, iso: String, amount: String) = UpcomingRecurringUi(
    id = RecurringRuleId(id),
    name = id,
    amountFormatted = amount,
    dueDateIso = iso,
    daysUntil = days,
    isImminent = days <= 1,
)

private const val RENT_DUE = "€980.00"
