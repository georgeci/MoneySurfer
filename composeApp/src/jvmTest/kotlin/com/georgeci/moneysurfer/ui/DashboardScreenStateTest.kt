package com.georgeci.moneysurfer.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.domain.dashboard.DashboardCardStyle
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutItem
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetSize
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.domain.model.BurnRatePace
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.feature.dashboard.AccountUi
import com.georgeci.moneysurfer.feature.dashboard.BudgetSummaryUi
import com.georgeci.moneysurfer.feature.dashboard.BurnRateDayUi
import com.georgeci.moneysurfer.feature.dashboard.BurnRateUi
import com.georgeci.moneysurfer.feature.dashboard.DashboardContent
import com.georgeci.moneysurfer.feature.dashboard.DashboardEvent
import com.georgeci.moneysurfer.feature.dashboard.DashboardState
import com.georgeci.moneysurfer.feature.dashboard.DashboardTestTags
import com.georgeci.moneysurfer.feature.dashboard.SafeToSpendUi
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly

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
        runComposeUiTest {
            setContent {
                DashboardContent(state = contentWith(accounts = 2, transferEnabled = true), onEvent = {})
            }

            onNodeWithTag(DashboardTestTags.QuickActions).assertIsDisplayed()
            onNodeWithText(ADD_TRANSACTION).assertIsDisplayed()
            onNodeWithText(TRANSFER).assertIsDisplayed()
        }
    }

    "a build without transfers draws no quick-actions row" {
        runComposeUiTest {
            setContent {
                DashboardContent(state = contentWith(accounts = 2, transferEnabled = false), onEvent = {})
            }

            // Half the row is a Transfer button that would land on an expense form here.
            onNodeWithTag(DashboardTestTags.QuickActions).assertDoesNotExist()
        }
    }

    "a single account draws no quick-actions row — there is nowhere to transfer to" {
        runComposeUiTest {
            setContent {
                DashboardContent(state = contentWith(accounts = 1, transferEnabled = true), onEvent = {})
            }

            onNodeWithTag(DashboardTestTags.QuickActions).assertDoesNotExist()
        }
    }

    "the two shortcuts ask for the plain form and the transfer form respectively" {
        runComposeUiTest {
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

    "with no budget the safe-to-spend card still draws, offering the way out of its empty state" {
        runComposeUiTest {
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
        runComposeUiTest {
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
        runComposeUiTest {
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
        runComposeUiTest {
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

    "the burn-rate card draws its pace and projection, and says so when a budget judges them" {
        runComposeUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true).copy(
                        burnRate = burnRateUi(pace = BurnRatePace.OffPace),
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

    "with no budget the burn-rate card still draws the projection, minus the verdict" {
        runComposeUiTest {
            setContent {
                DashboardContent(
                    state = contentWith(accounts = 2, transferEnabled = true).copy(burnRate = burnRateUi()),
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
        runComposeUiTest {
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
        runComposeUiTest {
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
        runComposeUiTest {
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
        runComposeUiTest {
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
        runComposeUiTest {
            val events = mutableListOf<DashboardEvent>()
            setContent {
                DashboardContent(state = budgetsOnly(listOf(budgetUi())), onEvent = { events += it })
            }

            onNodeWithText(BUDGET_NAME).performClick()
            waitForIdle()

            events shouldContainExactly listOf(DashboardEvent.OnBudgetClick(BudgetId("b-1")))
        }
    }
})

private const val ADD_TRANSACTION = "Add transaction"
private const val TRANSFER = "Transfer"
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
