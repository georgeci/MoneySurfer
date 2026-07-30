package com.georgeci.moneysurfer.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDatePreset
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDateRange
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionSort
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionTypeFilter
import com.georgeci.moneysurfer.feature.transaction.list.TransactionDateUi
import com.georgeci.moneysurfer.feature.transaction.list.TransactionFilterChipsUi
import com.georgeci.moneysurfer.feature.transaction.list.TransactionGroupUi
import com.georgeci.moneysurfer.feature.transaction.list.TransactionPeriodUi
import com.georgeci.moneysurfer.feature.transaction.list.TransactionRowUi
import com.georgeci.moneysurfer.feature.transaction.list.TransactionSummaryUi
import com.georgeci.moneysurfer.feature.transaction.list.TransactionsByAccountContent
import com.georgeci.moneysurfer.feature.transaction.list.TransactionsByAccountEvent
import com.georgeci.moneysurfer.feature.transaction.list.TransactionsByAccountState
import com.georgeci.moneysurfer.feature.transaction.list.TransactionsListTestTags
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import kotlinx.datetime.LocalDate

/**
 * Desktop UI cover for the transactions list — see docs/testing/testing-strategy.md.
 *
 * Everything asserted here is a decision the screen makes on its own: which empty state to draw and
 * whether the FAB may sit beside it, what a chip says once one thing is picked rather than three,
 * how a day header labels today, and which of the pager's arrows a period leaves usable. None of it
 * is visible to a ViewModel test, which sees only the state that reaches the screen.
 */
@OptIn(ExperimentalTestApi::class)
class TransactionsListScreenStateTest : StringSpec({

    "a day's rows sit under a relative header, with the day's net beside it" {
        runComposeUiTest {
            setContent {
                TransactionsByAccountContent(state = listWith(groups = listOf(todayGroup())), onEvent = {})
            }

            onNodeWithTag(TransactionsListTestTags.Root).assertIsDisplayed()
            onNodeWithText("Today").assertIsDisplayed()
            onNodeWithText(DAY_NET).assertIsDisplayed()
            onNodeWithText("Lidl").assertIsDisplayed()
            onNodeWithText(EXPENSE_AMOUNT).assertIsDisplayed()
        }
    }

    "a row with no merchant and no note is named rather than left blank" {
        runComposeUiTest {
            setContent {
                TransactionsByAccountContent(
                    state = listWith(
                        groups = listOf(
                            todayGroup(rows = listOf(row(id = "t-1", title = "", subtitle = ""))),
                        ),
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("Transaction").assertIsDisplayed()
        }
    }

    "a collapsed receipt counts its categories instead of naming one of them" {
        runComposeUiTest {
            setContent {
                TransactionsByAccountContent(
                    state = listWith(
                        groups = listOf(
                            todayGroup(
                                rows = listOf(
                                    row(id = "t-1", title = "Auchan", subtitle = "Groceries")
                                        .copy(splitCategoryCount = 3, isSplitLeg = true),
                                ),
                            ),
                        ),
                    ),
                    onEvent = {},
                )
            }

            // "Groceries" would name one leg of a receipt the row is showing in full.
            onNodeWithText("3 categories").assertIsDisplayed()
            onNodeWithText("Groceries").assertDoesNotExist()
        }
    }

    "the account is named on a row only where the list mixes several" {
        runComposeUiTest {
            setContent {
                TransactionsByAccountContent(
                    state = listWith(groups = listOf(todayGroup()), showAccountOnRows = true),
                    onEvent = {},
                )
            }

            onNodeWithText("Groceries · Everyday").assertIsDisplayed()
        }
    }

    "the summary strip states income, expenses and the net of the period" {
        runComposeUiTest {
            setContent {
                TransactionsByAccountContent(state = listWith(groups = listOf(todayGroup())), onEvent = {})
            }

            // The strip shouts its labels, as the design draws them.
            onNodeWithText("INCOME").assertIsDisplayed()
            onNodeWithText("EXPENSES").assertIsDisplayed()
            onNodeWithText("NET").assertIsDisplayed()
            onNodeWithText(SUMMARY_NET).assertIsDisplayed()
        }
    }

    "a chip names the one thing that is picked and counts the rest" {
        runComposeUiTest {
            setContent {
                TransactionsByAccountContent(
                    state = listWith(
                        groups = listOf(todayGroup()),
                        chips = chips().copy(
                            type = TransactionTypeFilter.Expenses,
                            dateRange = TransactionDateRange.Preset(TransactionDatePreset.ThisWeek),
                            accountCount = 1,
                            accountName = "Everyday",
                            categoryCount = 3,
                            sort = TransactionSort.Oldest,
                        ),
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("Type · Expenses").assertIsDisplayed()
            onNodeWithText("Date · This week").assertIsDisplayed()
            onNodeWithText("Account · Everyday").assertIsDisplayed()
            // Three names would not fit and any one of them would misreport the other two.
            onNodeWithText("Category · 3 selected").assertIsDisplayed()
            onNodeWithText("Sort · Oldest").assertIsDisplayed()
        }
    }

    "an unset chip is its bare label, and the date chip is not drawn at all" {
        runComposeUiTest {
            setContent {
                TransactionsByAccountContent(state = listWith(groups = listOf(todayGroup())), onEvent = {})
            }

            onNodeWithText("Type").assertIsDisplayed()
            onNodeWithText("Account").assertIsDisplayed()
            onNodeWithText("Category").assertIsDisplayed()
            // The sort chip always spells its value out; the default is what makes it read as unset.
            onNodeWithText("Sort · Newest").assertIsDisplayed()
            // While the pager owns the window there is no range to report.
            onNodeWithTag(TransactionsListTestTags.FilterDate).assertDoesNotExist()
        }
    }

    "every chip is a shortcut into the filter screen" {
        runComposeUiTest {
            val events = mutableListOf<TransactionsByAccountEvent>()
            setContent {
                TransactionsByAccountContent(
                    state = listWith(groups = listOf(todayGroup())),
                    onEvent = { events += it },
                )
            }

            onNodeWithTag(TransactionsListTestTags.FilterType).performClick()
            onNodeWithTag(TransactionsListTestTags.FilterAccount).performClick()
            onNodeWithTag(TransactionsListTestTags.FilterSort).performClick()
            waitForIdle()

            events shouldContainExactly List(3) { TransactionsByAccountEvent.OnOpenFiltersClick }
        }
    }

    "the pager names the month and refuses to page past the current one" {
        runComposeUiTest {
            setContent {
                TransactionsByAccountContent(
                    state = listWith(
                        groups = listOf(todayGroup()),
                        period = TransactionPeriodUi.Month(monthNumber = 3, year = 2025),
                        canGoToNextPeriod = false,
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("March").assertIsDisplayed()
            onNodeWithText("2025").assertIsDisplayed()
            // Forward from the current period could only ever show an empty list.
            onNodeWithContentDescription("Next period").assertIsNotEnabled()
            onNodeWithContentDescription("Previous period").assertIsEnabled()
        }
    }

    "a week is labelled by its range and its ISO number" {
        runComposeUiTest {
            setContent {
                TransactionsByAccountContent(
                    state = listWith(
                        groups = listOf(todayGroup()),
                        periodMode = TransactionPeriodMode.Week,
                        period = TransactionPeriodUi.Week(
                            from = LocalDate(2025, 3, 24),
                            to = LocalDate(2025, 3, 30),
                            weekNumber = 13,
                            weekYear = 2025,
                        ),
                    ),
                    onEvent = {},
                )
            }

            onNodeWithText("Mar 24 – 30").assertIsDisplayed()
            onNodeWithText("W13 · 2025").assertIsDisplayed()
        }
    }

    "an explicit date range takes the window over, so the pager is not drawn" {
        runComposeUiTest {
            setContent {
                TransactionsByAccountContent(
                    state = listWith(groups = listOf(todayGroup()), showPeriodPager = false),
                    onEvent = {},
                )
            }

            // Two controls over one window is exactly what the filters were meant to avoid.
            onNodeWithContentDescription("Previous period").assertDoesNotExist()
            onNodeWithText("March").assertDoesNotExist()
        }
    }

    "an account with nothing logged yet is offered the first transaction" {
        runComposeUiTest {
            val events = mutableListOf<TransactionsByAccountEvent>()
            setContent {
                TransactionsByAccountContent(state = listWith(), onEvent = { events += it })
            }

            onNodeWithText("No transactions yet").assertIsDisplayed()
            onNodeWithText("Add transaction").performClick()
            waitForIdle()

            events shouldContainExactly listOf(TransactionsByAccountEvent.OnAddTransactionClick)
            // The empty state's CTA *is* the FAB's action; two of them a thumb apart is one too many.
            onNodeWithText("New").assertDoesNotExist()
        }
    }

    "a search that matched nothing offers the search back, not a new transaction" {
        runComposeUiTest {
            val events = mutableListOf<TransactionsByAccountEvent>()
            setContent {
                TransactionsByAccountContent(
                    state = listWith(query = "gwerty", isFiltered = true),
                    onEvent = { events += it },
                )
            }

            onNodeWithText("Nothing to show").assertIsDisplayed()
            onNodeWithText("Nothing matches this search.").assertIsDisplayed()
            onNodeWithText("Clear search").performClick()
            waitForIdle()

            events shouldContainExactly listOf(TransactionsByAccountEvent.OnClearFiltersClick)
            // Rows exist here, they are merely hidden — so the FAB stays.
            onNodeWithText("New", useUnmergedTree = true).assertIsDisplayed()
        }
    }

    "filters that matched nothing name the filters rather than the search" {
        runComposeUiTest {
            setContent {
                TransactionsByAccountContent(
                    state = listWith(activeFilterCount = 2, isFiltered = true),
                    onEvent = {},
                )
            }

            // "Clear search" would name a control this user never touched.
            onNodeWithText("No transactions match the filters you applied.").assertIsDisplayed()
            onNodeWithText("Clear filters").assertIsDisplayed()
        }
    }

    "the toolbar falls back to a generic title when no account owns the list" {
        runComposeUiTest {
            setContent {
                TransactionsByAccountContent(
                    state = listWith(groups = listOf(todayGroup()), accountName = ""),
                    onEvent = {},
                )
            }

            onNodeWithText("Transactions").assertIsDisplayed()
        }
    }
})

private const val EXPENSE_AMOUNT = "−€42.10"
private const val DAY_NET = "−€52.10"
private const val SUMMARY_NET = "+€1,204.00"

private fun row(
    id: String,
    title: String = "Lidl",
    subtitle: String = "Groceries",
) = TransactionRowUi(
    id = TransactionId(id),
    title = title,
    subtitle = subtitle,
    formattedAmount = EXPENSE_AMOUNT,
    isExpense = true,
    categoryHueSeed = subtitle,
    accountName = "Everyday",
)

private fun todayGroup(rows: List<TransactionRowUi> = listOf(row(id = "t-1"))) = TransactionGroupUi(
    date = LocalDate(2025, 3, 27),
    dateLabel = TransactionDateUi.Today,
    netFormatted = DAY_NET,
    netPositive = false,
    transactions = rows,
)

private fun chips() = TransactionFilterChipsUi(
    dateRange = TransactionDateRange.FollowPeriod,
    type = TransactionTypeFilter.All,
    accountCount = 0,
    accountName = null,
    categoryCount = 0,
    categoryName = null,
    sort = TransactionSort.Newest,
)

@Suppress("LongParameterList")
private fun listWith(
    groups: List<TransactionGroupUi> = emptyList(),
    accountName: String = "Everyday",
    showAccountOnRows: Boolean = false,
    query: String = "",
    chips: TransactionFilterChipsUi = chips(),
    activeFilterCount: Int = 0,
    isFiltered: Boolean = false,
    periodMode: TransactionPeriodMode = TransactionPeriodMode.Month,
    period: TransactionPeriodUi = TransactionPeriodUi.Month(monthNumber = 3, year = 2025),
    showPeriodPager: Boolean = true,
    canGoToNextPeriod: Boolean = true,
) = TransactionsByAccountState.Content(
    accountId = AccountId("acc-1"),
    accountName = accountName,
    groups = groups,
    showAccountOnRows = showAccountOnRows,
    summary = TransactionSummaryUi(
        incomeFormatted = "+€3,000.00",
        expenseFormatted = "−€1,796.00",
        netFormatted = SUMMARY_NET,
        netPositive = true,
    ),
    query = query,
    filters = chips,
    activeFilterCount = activeFilterCount,
    isFiltered = isFiltered,
    periodMode = periodMode,
    period = period,
    showPeriodPager = showPeriodPager,
    canGoToPreviousPeriod = true,
    canGoToNextPeriod = canGoToNextPeriod,
    canLoadMore = false,
)
