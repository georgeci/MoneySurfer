package com.georgeci.moneysurfer.feature.transaction.list

import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDatePreset
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDateRange
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionSort
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionTypeFilter
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate

/**
 * Search, filter chips, sorting and account scoping for `TransactionsByAccountViewModel`. The
 * period pager and paging itself live in [TransactionsByAccountPeriodTest].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsByAccountFilteringTest : StringSpec({

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "the type filter narrows the list without touching the period summary" {
        runTest {
            val env = Env(
                transactions = listOf(
                    income(id = "salary", date = LocalDate(2025, 3, 1), amount = 100),
                    expense(id = "rent", date = LocalDate(2025, 3, 2), amount = 40),
                ),
            )
            val viewModel = env.viewModel()

            env.filterStore.commit(
                env.filterStore.filters.value.copy(type = TransactionTypeFilter.Income),
            )

            val state = viewModel.content()
            state.isFiltered shouldBe true
            state.activeFilterCount shouldBe 1
            state.groups.single().transactions.single().id.value shouldBe "salary"
            state.summary.expenseFormatted shouldBe "−$40.00"
        }
    }

    "search matches merchant, note and category, and survives into the state" {
        runTest {
            val env = Env(
                transactions = listOf(
                    expense(id = "coffee", amount = 4).copy(merchant = "Starbucks"),
                    expense(id = "book", amount = 20).copy(note = "Birthday present"),
                    expense(id = "rent", amount = 900),
                ),
            )
            val viewModel = env.viewModel()

            viewModel.onEvent(TransactionsByAccountEvent.OnSearchQueryChanged("star"))

            val state = viewModel.content()
            state.query shouldBe "star"
            state.rowIds().shouldContainExactly("coffee")
            // The search box is its own visible state; it must not also inflate the filter badge.
            state.activeFilterCount shouldBe 0
        }
    }

    "searching an amount tolerates the cents the user did not type" {
        runTest {
            val env = Env(
                transactions = listOf(
                    expenseCents(id = "near", cents = 1240),
                    expenseCents(id = "far", cents = 1300),
                ),
            )
            val viewModel = env.viewModel()

            viewModel.onEvent(TransactionsByAccountEvent.OnSearchQueryChanged("12"))

            viewModel.content().rowIds().shouldContainExactly("near")
        }
    }

    "a sparse search keeps loading until deep matches surface, not showing nothing" {
        runTest {
            // 200 non-matching newest rows, then 5 matching rows older than the first page.
            val noise = (1..PAGE_SIZE).map { expense(id = "n-$it", date = LocalDate(2025, 3, 20), amount = 1) }
            val coffee = (1..5).map { i ->
                expense(id = "c-$i", date = LocalDate(2025, 1, i), amount = 4).copy(merchant = "Coffee")
            }
            val env = Env(transactions = noise + coffee)
            val viewModel = env.viewModel()
            // All time so the window holds every row; the matches sit beyond the first 200.
            viewModel.onEvent(
                TransactionsByAccountEvent.OnPeriodModeChanged(TransactionPeriodMode.AllTime),
            )

            viewModel.onEvent(TransactionsByAccountEvent.OnSearchQueryChanged("coffee"))

            // Without auto-loading, the first page has 0 coffee rows and the list is stuck empty.
            val state = viewModel.content()
            state.isEmpty shouldBe false
            state.groups.flatMap { it.transactions } shouldHaveSize 5
        }
    }

    "an explicit date range replaces the pager window and hides the pager" {
        runTest {
            val env = Env(
                transactions = listOf(
                    expense(id = "today", date = TODAY, amount = 1),
                    expense(id = "earlier", date = LocalDate(2025, 3, 1), amount = 1),
                ),
            )
            val viewModel = env.viewModel()

            env.filterStore.commit(
                env.filterStore.filters.value.copy(
                    dateRange = TransactionDateRange.Preset(TransactionDatePreset.Today),
                ),
            )

            val state = viewModel.content()
            state.showPeriodPager shouldBe false
            env.repository.lastWindow shouldBe TransactionPeriodWindow(from = TODAY, to = TODAY)
            state.rowIds().shouldContainExactly("today")
        }
    }

    "oldest first reverses the whole list, day groups included" {
        runTest {
            val env = Env(
                transactions = listOf(
                    expense(id = "newer", date = LocalDate(2025, 3, 20), amount = 1),
                    expense(id = "older", date = LocalDate(2025, 3, 2), amount = 1),
                ),
            )
            val viewModel = env.viewModel()

            env.filterStore.commit(env.filterStore.filters.value.copy(sort = TransactionSort.Oldest))

            viewModel.content().rowIds().shouldContainExactly("older", "newer")
        }
    }

    "recurring-only and planned-only read the domain fields they name" {
        runTest {
            val env = Env(
                transactions = listOf(
                    expense(id = "manual", amount = 1),
                    expense(id = "generated", amount = 1)
                        .copy(recurringRuleId = RecurringRuleId("rule-1")),
                    expense(id = "planned", amount = 1)
                        .copy(status = TransactionStatus.PLANNED),
                ),
            )
            val viewModel = env.viewModel()

            env.filterStore.commit(env.filterStore.filters.value.copy(recurringOnly = true))
            viewModel.content().rowIds().shouldContainExactly("generated")

            env.filterStore.commit(
                env.filterStore.filters.value.copy(recurringOnly = false, plannedOnly = true),
            )
            viewModel.content().rowIds().shouldContainExactly("planned")
        }
    }

    "the amount bounds compare against the magnitude, not the sign" {
        runTest {
            val env = Env(
                transactions = listOf(
                    expense(id = "small", amount = 10),
                    expense(id = "big", amount = 90),
                ),
            )
            val viewModel = env.viewModel()

            env.filterStore.commit(env.filterStore.filters.value.copy(minAmount = "50"))

            viewModel.content().rowIds().shouldContainExactly("big")
        }
    }

    "an account-scoped list ignores an account picked on the filter screen" {
        runTest {
            val env = Env(transactions = listOf(expense(id = "mine", amount = 10)))
            val viewModel = env.viewModel(accountId = ACCOUNT)

            env.filterStore.commit(
                env.filterStore.filters.value.copy(accountIds = setOf(accountId("other"))),
            )

            viewModel.content().rowIds().shouldContainExactly("mine")
        }
    }

    "rows name their account only where the list mixes several" {
        runTest {
            val env = Env(
                transactions = listOf(
                    expense(id = "mine", amount = 10),
                    expense(id = "theirs", amount = 20, account = OTHER_ACCOUNT),
                ),
            )

            val allAccounts = env.viewModel(accountId = null).content()
            allAccounts.showAccountOnRows shouldBe true
            allAccounts.rows().map { it.accountName }
                .shouldContainExactly("Everyday", "Savings")

            // Scoped to one account the toolbar already says which; the rows still carry the name
            // so the screen can decide, but the screen is told not to draw it.
            env.viewModel(accountId = ACCOUNT).content().showAccountOnRows shouldBe false
        }
    }

    "a transfer leg is filterable and marked as a transfer on the row" {
        runTest {
            val env = Env(
                transactions = listOf(
                    expense(id = "rent", amount = 40),
                    expense(id = "moved-out", amount = 100).copy(transferId = TransferId("tr-1")),
                    income(id = "moved-in", amount = 100).copy(transferId = TransferId("tr-1")),
                ),
            )
            val viewModel = env.viewModel(accountId = null)

            env.filterStore.commit(
                env.filterStore.filters.value.copy(type = TransactionTypeFilter.Transfer),
            )

            val transfers = viewModel.content()
            transfers.rowIds().shouldContainExactly("moved-out", "moved-in")
            transfers.rows().map { it.isTransfer } shouldBe listOf(true, true)
            // The seeded transfer category must not colour the row: it renders from the shared
            // transfer palette instead, which the blank seed is what selects.
            transfers.rows().map { it.categoryHueSeed } shouldBe listOf("", "")

            // The point of the segment: the outgoing leg no longer reads as a plain expense.
            env.filterStore.commit(
                env.filterStore.filters.value.copy(type = TransactionTypeFilter.Expenses),
            )
            viewModel.content().rowIds().shouldContainExactly("rent")
        }
    }

    "only the nothing-logged-yet empty state carries an add CTA, so only then is the FAB dropped" {
        runTest {
            val empty = Env().viewModel()
            // Nothing logged at all: the empty state offers "Add transaction", and the screen
            // hides the FAB rather than show the same action twice.
            empty.content().showsAddCta shouldBe true

            val env = Env(transactions = listOf(expense(id = "rent", amount = 40)))
            val viewModel = env.viewModel()
            // Rows exist and are hidden: the CTA clears the filter, so the FAB is still the only
            // way to add one.
            viewModel.onEvent(TransactionsByAccountEvent.OnSearchQueryChanged("coffee"))
            viewModel.content().isEmpty shouldBe true
            viewModel.content().showsAddCta shouldBe false

            // A list with rows in it has no empty state at all.
            viewModel.onEvent(TransactionsByAccountEvent.OnSearchQueryChanged(""))
            viewModel.content().showsAddCta shouldBe false
        }
    }

    "clearing from the empty state drops the filters and the search text together" {
        runTest {
            val env = Env(transactions = listOf(expense(id = "rent", amount = 40)))
            val viewModel = env.viewModel()
            env.filterStore.commit(
                env.filterStore.filters.value.copy(type = TransactionTypeFilter.Income),
            )
            viewModel.onEvent(TransactionsByAccountEvent.OnSearchQueryChanged("coffee"))
            viewModel.content().isEmpty shouldBe true

            viewModel.onEvent(TransactionsByAccountEvent.OnClearFiltersClick)

            val state = viewModel.content()
            state.query shouldBe ""
            state.isFiltered shouldBe false
            // Anything less would answer the CTA with the same empty screen.
            state.rowIds().shouldContainExactly("rent")
        }
    }
})
