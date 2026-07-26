package com.georgeci.moneysurfer.feature.transaction.list

import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.FakeUiPreferences
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.transferId
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionsByAccountUseCase
import com.georgeci.moneysurfer.domain.usecase.RestoreTransactionsUseCase
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDatePreset
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDateRange
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionFilterStore
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionSort
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionTypeFilter
import com.georgeci.moneysurfer.navigation.DeleteTransactionWithUndo
import com.georgeci.moneysurfer.navigation.SnackbarController
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Clock
import kotlin.time.Instant

/** Fixed "today" for every case below: a Thursday, mid-month, mid-week. */
private val TODAY = LocalDate(2025, 3, 27)

/**
 * Period pager, windowed loading and period-scoped summary for `TransactionsByAccountViewModel`
 * (issue #261). The repository fake applies the window and the limit for real, so the assertions
 * are about what the ViewModel actually asked the database for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsByAccountViewModelTest : StringSpec({

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "opens on the month containing today" {
        runTest {
            val env = Env()

            val state = env.viewModel().content()

            state.periodMode shouldBe TransactionPeriodMode.Month
            state.period shouldBe TransactionPeriodUi.Month(monthNumber = 3, year = 2025)
            env.repository.lastWindow shouldBe TransactionPeriodWindow(
                from = LocalDate(2025, 3, 1),
                to = LocalDate(2025, 3, 31),
            )
        }
    }

    "only transactions inside the period reach the list" {
        runTest {
            val env = Env(
                transactions = listOf(
                    expense(id = "in-march", date = LocalDate(2025, 3, 10), amount = 30),
                    expense(id = "in-february", date = LocalDate(2025, 2, 10), amount = 40),
                ),
            )

            val state = env.viewModel().content()

            state.groups.flatMap { group -> group.transactions.map { it.id.value } }
                .shouldContainExactly("in-march")
        }
    }

    "the summary covers the period, not the loaded page" {
        runTest {
            val env = Env(
                transactions = listOf(
                    income(id = "salary", date = LocalDate(2025, 3, 1), amount = 100),
                    expense(id = "rent", date = LocalDate(2025, 3, 2), amount = 40),
                    expense(id = "old", date = LocalDate(2025, 1, 2), amount = 999),
                ),
            )

            val summary = env.viewModel().content().summary

            summary.incomeFormatted shouldBe "+$100.00"
            summary.expenseFormatted shouldBe "−$40.00"
            summary.netFormatted shouldBe "+$60.00"
            summary.netPositive shouldBe true
        }
    }

    "paging back a month re-queries the previous window" {
        runTest {
            val env = Env(
                transactions = listOf(expense(id = "february", date = LocalDate(2025, 2, 10), amount = 30)),
            )
            val viewModel = env.viewModel()

            viewModel.onEvent(TransactionsByAccountEvent.OnPreviousPeriodClick)

            val state = viewModel.content()
            state.period shouldBe TransactionPeriodUi.Month(monthNumber = 2, year = 2025)
            env.repository.lastWindow shouldBe TransactionPeriodWindow(
                from = LocalDate(2025, 2, 1),
                to = LocalDate(2025, 2, 28),
            )
            state.groups.single().transactions.single().id.value shouldBe "february"
        }
    }

    "the pager stops at the current period but always allows going back" {
        runTest {
            val env = Env()
            val viewModel = env.viewModel()

            viewModel.content().canGoToNextPeriod shouldBe false
            viewModel.content().canGoToPreviousPeriod shouldBe true

            viewModel.onEvent(TransactionsByAccountEvent.OnPreviousPeriodClick)

            viewModel.content().canGoToNextPeriod shouldBe true
        }
    }

    "week mode windows Monday to Sunday and labels the ISO week" {
        runTest {
            val env = Env()
            val viewModel = env.viewModel()

            viewModel.onEvent(
                TransactionsByAccountEvent.OnPeriodModeChanged(TransactionPeriodMode.Week),
            )

            val state = viewModel.content()
            state.period shouldBe TransactionPeriodUi.Week(
                from = LocalDate(2025, 3, 24),
                to = LocalDate(2025, 3, 30),
                weekNumber = 13,
                weekYear = 2025,
            )
            env.repository.lastWindow shouldBe TransactionPeriodWindow(
                from = LocalDate(2025, 3, 24),
                to = LocalDate(2025, 3, 30),
            )
        }
    }

    "the period mode is persisted, not held in the screen state" {
        runTest {
            val env = Env()

            env.viewModel().onEvent(
                TransactionsByAccountEvent.OnPeriodModeChanged(TransactionPeriodMode.AllTime),
            )

            env.preferences.storedPeriodMode() shouldBe TransactionPeriodMode.AllTime
        }
    }

    "all time drops the date bounds but stays paged" {
        runTest {
            val env = Env(transactions = (1..PAGE_SIZE + 5).map { expense(id = "t-$it", amount = 1) })
            val viewModel = env.viewModel()

            viewModel.onEvent(
                TransactionsByAccountEvent.OnPeriodModeChanged(TransactionPeriodMode.AllTime),
            )

            val state = viewModel.content()
            state.period shouldBe TransactionPeriodUi.AllTime
            state.canGoToPreviousPeriod shouldBe false
            state.canGoToNextPeriod shouldBe false
            env.repository.lastWindow shouldBe TransactionPeriodWindow.Unbounded
            state.rowCount() shouldBe PAGE_SIZE
            state.canLoadMore shouldBe true
        }
    }

    "loading more appends the next page and stops when the window is exhausted" {
        runTest {
            val env = Env(transactions = (1..PAGE_SIZE + 5).map { expense(id = "t-$it", amount = 1) })
            val viewModel = env.viewModel()
            viewModel.onEvent(
                TransactionsByAccountEvent.OnPeriodModeChanged(TransactionPeriodMode.AllTime),
            )

            viewModel.onEvent(TransactionsByAccountEvent.OnLoadMore)

            val state = viewModel.content()
            state.rowCount() shouldBe PAGE_SIZE + 5
            state.canLoadMore shouldBe false
        }
    }

    "changing the period resets paging back to the first page" {
        runTest {
            val env = Env(transactions = (1..PAGE_SIZE + 5).map { expense(id = "t-$it", amount = 1) })
            val viewModel = env.viewModel()
            viewModel.onEvent(
                TransactionsByAccountEvent.OnPeriodModeChanged(TransactionPeriodMode.AllTime),
            )
            viewModel.onEvent(TransactionsByAccountEvent.OnLoadMore)

            viewModel.onEvent(TransactionsByAccountEvent.OnPeriodModeChanged(TransactionPeriodMode.Month))
            viewModel.onEvent(
                TransactionsByAccountEvent.OnPeriodModeChanged(TransactionPeriodMode.AllTime),
            )

            viewModel.content().rowCount() shouldBe PAGE_SIZE
        }
    }

    "day headers are relative for today and yesterday and exact otherwise" {
        runTest {
            val env = Env(
                transactions = listOf(
                    expense(id = "now", date = TODAY, amount = 1),
                    expense(id = "then", date = LocalDate(2025, 3, 26), amount = 1),
                    expense(id = "older", date = LocalDate(2025, 3, 10), amount = 1),
                ),
            )

            val labels = env.viewModel().content().groups.map { it.dateLabel }

            labels[0] shouldBe TransactionDateUi.Today
            labels[1] shouldBe TransactionDateUi.Yesterday
            labels[2] shouldBe TransactionDateUi.Exact(LocalDate(2025, 3, 10))
        }
    }

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

    "swiping a row away removes it, and the snackbar's Undo puts it back" {
        runTest {
            val env = Env(transactions = listOf(expense(id = "rent", amount = 900), expense(id = "coffee", amount = 4)))
            val viewModel = env.viewModel()

            viewModel.onEvent(TransactionsByAccountEvent.OnDeleteTransaction(transactionId("coffee")))

            // No optimistic removal in the ViewModel — the row is gone because the database Flow
            // re-emitted without it.
            viewModel.content().rowIds().shouldContainExactly("rent")

            env.snackbar.requests.first().onAction!!.invoke()

            viewModel.content().rowIds().shouldContainExactly("rent", "coffee")
        }
    }

    "swiping one leg of a transfer takes both, and Undo restores the pair" {
        runTest {
            val transfer = transferId("tr-1")
            val env = Env(
                transactions = listOf(
                    expense(id = "rent", amount = 900),
                    expense(id = "leg-out", amount = 50).copy(transferId = transfer),
                    income(id = "leg-in", amount = 50).copy(transferId = transfer),
                ),
            )
            val viewModel = env.viewModel()

            // The leg the user swiped is the incoming one; the outgoing sibling has to go with it,
            // or the other account keeps money that no longer came from anywhere.
            viewModel.onEvent(TransactionsByAccountEvent.OnDeleteTransaction(transactionId("leg-in")))

            viewModel.content().rowIds().shouldContainExactly("rent")

            env.snackbar.requests.first().onAction!!.invoke()

            viewModel.content().rowIds().shouldHaveSize(3)
        }
    }

    "the all-accounts summary currency is the dominant one, not the newest row's" {
        runTest {
            val env = Env(
                transactions = listOf(
                    // Newest, but the smaller total: it must not decide the summary currency.
                    expense(id = "usd", date = LocalDate(2025, 3, 20), amount = 10, currency = USD),
                    expense(id = "eur", date = LocalDate(2025, 3, 1), amount = 30, currency = EUR),
                ),
            )

            val summary = env.viewModel(accountId = null).content().summary

            summary.expenseFormatted shouldBe "−€30.00"
        }
    }
})

private const val PAGE_SIZE = 200

private val WORKSPACE = WorkspaceId("ws-1")
private val ACCOUNT = accountId("acc-1")
private val OTHER_ACCOUNT = accountId("acc-2")

private fun expense(
    id: String,
    date: LocalDate = TODAY,
    amount: Int,
    currency: CurrencyCode = USD,
    account: AccountId = ACCOUNT,
): Transaction = aTransaction(
    id = transactionId(id),
    workspaceId = WORKSPACE,
    accountId = account,
    money = amount.dollars,
    currencyCode = currency,
    operationDate = date,
    operationAt = date.atStartOfDayIn(TimeZone.UTC),
    type = TransactionType.EXPENSE,
)

private fun income(id: String, date: LocalDate = TODAY, amount: Int): Transaction =
    expense(id = id, date = date, amount = amount).copy(type = TransactionType.INCOME)

private fun TransactionsByAccountState.Content.rowCount(): Int = groups.sumOf { it.transactions.size }

private fun TransactionsByAccountState.Content.rowIds(): List<String> =
    groups.flatMap { group -> group.transactions.map { it.id.value } }

private fun TransactionsByAccountState.Content.rows(): List<TransactionRowUi> =
    groups.flatMap { it.transactions }

/** An expense of an exact number of minor units, for the amount-tolerance cases. */
private fun expenseCents(id: String, cents: Long): Transaction =
    expense(id = id, amount = 0).copy(money = Money.fromMinor(cents))

private fun TransactionsByAccountViewModel.content(): TransactionsByAccountState.Content =
    currentState.shouldBeInstanceOf<TransactionsByAccountState.Content>()

private class Env(transactions: List<Transaction> = emptyList()) {
    val repository = WindowingTransactionRepository(transactions)
    val preferences = FakeUiPreferences()
    val filterStore = TransactionFilterStore()
    val snackbar = SnackbarController()
    private val session = InMemorySessionPointers(currentWorkspaceId = WORKSPACE)

    fun viewModel(accountId: AccountId? = ACCOUNT): TransactionsByAccountViewModel {
        val applyChange = ApplyTransactionChangeUseCase(repository, SingleAccountRepository)
        return TransactionsByAccountViewModel(
            accountId = accountId,
            getTransactionsByAccount = GetTransactionsByAccountUseCase(repository),
            getAccountById = GetAccountByIdUseCase(SingleAccountRepository),
            getAccounts = GetAccountsUseCase(SingleAccountRepository, session),
            getCategories = GetCategoriesUseCase(SeededCategoryRepository, session),
            filterStore = filterStore,
            uiPreferences = preferences,
            clock = ClockUseCase(FixedClock(TODAY.atStartOfDayIn(TimeZone.UTC))),
            deleteWithUndo = DeleteTransactionWithUndo(
                deleteTransaction = DeleteTransactionUseCase(repository, applyChange),
                restoreTransactions = RestoreTransactionsUseCase(applyChange),
                snackbar = snackbar,
            ),
        )
    }
}

private val GROCERIES = categoryId("cat-groceries")

private object SeededCategoryRepository : CategoryRepository {
    private val categories = listOf(
        aCategory(id = GROCERIES, workspaceId = WORKSPACE, name = "Groceries"),
    )

    override fun getAll(): Flow<List<Category>> = flowOf(categories)
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = flowOf(categories)
    override suspend fun getById(id: CategoryId): Category? = categories.find { it.id == id }
    override suspend fun insert(category: Category) = Unit
    override suspend fun update(category: Category) = Unit
    override suspend fun delete(id: CategoryId) = Unit
}

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/**
 * Applies the window and the limit the way the DAO does, so a ViewModel that forgot to pass either
 * one would fail these tests rather than quietly reading everything.
 */
private class WindowingTransactionRepository(
    transactions: List<Transaction>,
) : TransactionRepository {
    private val rows = MutableStateFlow(transactions)

    var lastWindow: TransactionPeriodWindow? = null
        private set

    private fun inWindow(window: TransactionPeriodWindow): List<Transaction> = rows.value
        .filter { it.type != TransactionType.OPENING_BALANCE && it.operationDate in window }
        .sortedByDescending { it.operationDate }

    override fun getCategorizedWindow(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
        limit: Int,
    ): Flow<List<CategorizedTransaction>> {
        lastWindow = window
        return rows.map {
            inWindow(window)
                .filter { row -> accountId == null || row.accountId == accountId }
                .take(limit)
                .map { row -> CategorizedTransaction(transaction = row, categoryName = "Category") }
        }
    }

    override fun getTotals(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
    ): Flow<List<TransactionTotal>> = rows.map {
        inWindow(window)
            .filter { row -> accountId == null || row.accountId == accountId }
            .groupBy { row -> row.type to row.currencyCode }
            .map { (key, group) ->
                TransactionTotal(
                    type = key.first,
                    currencyCode = key.second,
                    total = group.fold(Money.zero()) { acc, row -> acc + row.money.abs() },
                )
            }
    }

    override fun getAll(): Flow<List<Transaction>> = rows
    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> = rows
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> = rows
    override suspend fun getById(id: TransactionId): Transaction? = rows.value.find { it.id == id }
    override suspend fun getByTransferId(transferId: TransferId): List<Transaction> =
        rows.value.filter { it.transferId == transferId }

    // Writing, not no-op: the list is fed by this flow, so a delete has to actually leave the
    // rows for the screen's reaction to it to be worth asserting.
    override suspend fun insert(transaction: Transaction) {
        rows.value = rows.value + transaction
    }

    override suspend fun update(transaction: Transaction) {
        rows.value = rows.value.map { if (it.id == transaction.id) transaction else it }
    }

    override suspend fun delete(id: TransactionId) {
        rows.value = rows.value.filterNot { it.id == id }
    }
}

private object SingleAccountRepository : AccountRepository {
    private val accounts = listOf(
        anAccount(id = ACCOUNT, workspaceId = WORKSPACE, name = "Everyday"),
        anAccount(id = OTHER_ACCOUNT, workspaceId = WORKSPACE, name = "Savings"),
    )

    override suspend fun getById(id: AccountId): Account? = accounts.find { it.id == id }
    override fun getAll(): Flow<List<Account>> = flowOf(accounts)
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = flowOf(accounts)
    override suspend fun insert(account: Account) = Unit
    override suspend fun update(account: Account) = Unit
    override suspend fun delete(id: AccountId) = Unit
    override suspend fun applyDelta(accountId: AccountId, delta: Money) = Unit
    override suspend fun setBalance(accountId: AccountId, balance: Money) = Unit
    override suspend fun reorder(orderedIds: List<AccountId>) = Unit
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) = Unit
}

/** What a fresh read of the store would return — i.e. what survives process death. */
private suspend fun FakeUiPreferences.storedPeriodMode(): TransactionPeriodMode =
    transactionsPeriodMode.flow.first()
