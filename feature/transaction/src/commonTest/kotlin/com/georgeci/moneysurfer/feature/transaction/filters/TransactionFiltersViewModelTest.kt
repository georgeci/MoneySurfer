package com.georgeci.moneysurfer.feature.transaction.filters

import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.FakeUiPreferences
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.splitId
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.SplitId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionsByAccountUseCase
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDatePreset
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDateRange
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionFilterStore
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionFilters
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionSort
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionTypeFilter
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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

private val TODAY = LocalDate(2025, 3, 27)
private val WORKSPACE = WorkspaceId("ws-1")
private val ACCOUNT = accountId("acc-1")
private val CATEGORY = categoryId("cat-1")

/**
 * The draft/apply contract of the filter screen (issue #262), and the live result count behind
 * `Apply · N results`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionFiltersViewModelTest : StringSpec({

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "editing the draft leaves the applied filters alone until Apply" {
        runTest {
            val env = Env()
            val viewModel = env.viewModel()

            viewModel.onEvent(
                TransactionFiltersEvent.OnTypeSelected(TransactionTypeFilter.Income),
            )

            env.filterStore.filters.value.type shouldBe TransactionTypeFilter.All
            viewModel.currentState.draft.type shouldBe TransactionTypeFilter.Income

            viewModel.onEvent(TransactionFiltersEvent.OnApplyClick)

            env.filterStore.filters.value.type shouldBe TransactionTypeFilter.Income
        }
    }

    "cancelling never writes the draft anywhere" {
        runTest {
            val env = Env()
            val viewModel = env.viewModel()

            viewModel.onEvent(TransactionFiltersEvent.OnRecurringOnlyChanged(enabled = true))
            viewModel.onEvent(TransactionFiltersEvent.OnCancelClick)

            env.filterStore.filters.value shouldBe TransactionFilters.Empty
        }
    }

    "reset clears the draft's filters but keeps the search the list is showing" {
        runTest {
            val env = Env()
            env.filterStore.setQuery("coffee")
            val viewModel = env.viewModel()
            viewModel.onEvent(TransactionFiltersEvent.OnPlannedOnlyChanged(enabled = true))

            viewModel.onEvent(TransactionFiltersEvent.OnResetClick)

            viewModel.currentState.draft.plannedOnly shouldBe false
            viewModel.currentState.draft.query shouldBe "coffee"
        }
    }

    "tapping the selected preset again returns the window to the pager" {
        runTest {
            val viewModel = Env().viewModel()

            viewModel.onEvent(TransactionFiltersEvent.OnDatePresetClick(TransactionDatePreset.Today))
            viewModel.currentState.draft.dateRange shouldBe
                TransactionDateRange.Preset(TransactionDatePreset.Today)

            viewModel.onEvent(TransactionFiltersEvent.OnDatePresetClick(TransactionDatePreset.Today))
            viewModel.currentState.draft.dateRange shouldBe TransactionDateRange.FollowPeriod
        }
    }

    "picking a From date is itself the switch to a custom range" {
        runTest {
            val viewModel = Env().viewModel()

            viewModel.onEvent(TransactionFiltersEvent.OnCustomFromPicked(LocalDate(2025, 1, 1)))

            viewModel.currentState.draft.dateRange shouldBe
                TransactionDateRange.Custom(from = LocalDate(2025, 1, 1), to = null)
        }
    }

    "the result count is a real count over the draft, updated as it changes" {
        runTest {
            val env = Env(
                transactions = listOf(
                    expense(id = "rent", amount = 900),
                    income(id = "salary", amount = 3000),
                ),
            )
            val viewModel = env.viewModel()

            viewModel.currentState.resultCount shouldBe 2

            viewModel.onEvent(
                TransactionFiltersEvent.OnTypeSelected(TransactionTypeFilter.Income),
            )

            viewModel.currentState.resultCount shouldBe 1
            viewModel.currentState.resultCountCapped shouldBe false
        }
    }

    "the result count promises rows the list will render, so a receipt counts once" {
        runTest {
            val receipt = splitId("sp-1")
            val env = Env(
                transactions = listOf(
                    expense(id = "rent", amount = 900),
                    expense(id = "leg-groceries", amount = 30).copy(splitId = receipt),
                    expense(id = "leg-chemicals", amount = 4).copy(splitId = receipt),
                ),
            )

            // Three rows in storage, two lines on the screen the button leads back to.
            env.viewModel().currentState.resultCount shouldBe 2
        }
    }

    "an account-scoped screen hides the account picker" {
        runTest {
            val env = Env()

            env.viewModel(accountId = ACCOUNT).currentState.showAccounts shouldBe false
            env.viewModel(accountId = null).currentState.showAccounts shouldBe true
        }
    }

    "the screen opens on what is currently applied, not on a blank slate" {
        runTest {
            val env = Env()
            env.filterStore.commit(TransactionFilters(recurringOnly = true))

            env.viewModel().currentState.draft.recurringOnly shouldBe true
        }
    }

    "the follow-period count uses the anchor the list was paged to, not today" {
        runTest {
            val env = Env(
                transactions = listOf(
                    expense(id = "jan-1", amount = 10, date = LocalDate(2025, 1, 10)),
                    expense(id = "jan-2", amount = 10, date = LocalDate(2025, 1, 20)),
                    expense(id = "today", amount = 10, date = TODAY),
                ),
            )

            // Anchored on January (the month the list is showing) counts January's two rows...
            env.viewModel(anchorEpochDay = LocalDate(2025, 1, 15).toEpochDays())
                .currentState.resultCount shouldBe 2
            // ...while no anchor falls back to today's month, which holds one.
            env.viewModel().currentState.resultCount shouldBe 1
        }
    }

    "an account or a category is toggled on, then off again" {
        runTest {
            val viewModel = Env().viewModel()

            viewModel.onEvent(TransactionFiltersEvent.OnAccountToggled(ACCOUNT))
            viewModel.onEvent(TransactionFiltersEvent.OnCategoryToggled(CATEGORY))
            viewModel.currentState.draft.accountIds shouldBe setOf(ACCOUNT)
            viewModel.currentState.draft.categoryIds shouldBe setOf(CATEGORY)

            viewModel.onEvent(TransactionFiltersEvent.OnAccountToggled(ACCOUNT))
            viewModel.onEvent(TransactionFiltersEvent.OnCategoryToggled(CATEGORY))
            viewModel.currentState.draft.accountIds shouldBe emptySet()
            viewModel.currentState.draft.categoryIds shouldBe emptySet()
        }
    }

    "All categories drops the selection rather than adding one more chip" {
        runTest {
            val viewModel = Env().viewModel()
            viewModel.onEvent(TransactionFiltersEvent.OnCategoryToggled(CATEGORY))

            viewModel.onEvent(TransactionFiltersEvent.OnAllCategoriesClick)

            viewModel.currentState.draft.categoryIds shouldBe emptySet()
        }
    }

    "the amount bounds, the two flags and the sort all land in the draft" {
        runTest {
            val viewModel = Env().viewModel()

            viewModel.onEvent(TransactionFiltersEvent.OnMinAmountChanged("12."))
            viewModel.onEvent(TransactionFiltersEvent.OnMaxAmountChanged("50"))
            viewModel.onEvent(TransactionFiltersEvent.OnRecurringOnlyChanged(enabled = true))
            viewModel.onEvent(TransactionFiltersEvent.OnPlannedOnlyChanged(enabled = true))
            viewModel.onEvent(TransactionFiltersEvent.OnSortSelected(TransactionSort.Oldest))

            val draft = viewModel.currentState.draft
            // Raw text, not a parsed number: a half-typed bound has to survive the round trip back
            // to the field the user is still typing in.
            draft.minAmount shouldBe "12."
            draft.maxAmount shouldBe "50"
            draft.recurringOnly shouldBe true
            draft.plannedOnly shouldBe true
            draft.sort shouldBe TransactionSort.Oldest
        }
    }

    "Reset is offered only once something is set" {
        runTest {
            val viewModel = Env().viewModel()
            viewModel.currentState.canReset shouldBe false

            viewModel.onEvent(TransactionFiltersEvent.OnSortSelected(TransactionSort.Oldest))
            viewModel.currentState.canReset shouldBe true
        }
    }

    "tapping Custom a second time hands the window back to the pager" {
        runTest {
            val viewModel = Env().viewModel()

            viewModel.onEvent(TransactionFiltersEvent.OnCustomDateClick)
            viewModel.currentState.draft.dateRange shouldBe TransactionDateRange.Custom(null, null)

            viewModel.onEvent(TransactionFiltersEvent.OnCustomToPicked(LocalDate(2025, 3, 31)))
            viewModel.currentState.draft.dateRange shouldBe
                TransactionDateRange.Custom(from = null, to = LocalDate(2025, 3, 31))

            viewModel.onEvent(TransactionFiltersEvent.OnCustomDateClick)
            viewModel.currentState.draft.dateRange shouldBe TransactionDateRange.FollowPeriod
        }
    }

    "an archived account is not offered as a filter" {
        runTest {
            val viewModel = Env().viewModel()

            // Everything the workspace still uses, and nothing it has put away: filtering by an
            // archived account could only ever narrow the list to rows the user retired.
            viewModel.currentState.accounts.map { it.id } shouldBe listOf(ACCOUNT)
        }
    }
})

private fun expense(id: String, amount: Int, date: LocalDate = TODAY): Transaction = aTransaction(
    id = transactionId(id),
    workspaceId = WORKSPACE,
    accountId = ACCOUNT,
    money = amount.dollars,
    operationDate = date,
    operationAt = date.atStartOfDayIn(TimeZone.UTC),
    type = TransactionType.EXPENSE,
)

private fun income(id: String, amount: Int): Transaction =
    expense(id = id, amount = amount).copy(type = TransactionType.INCOME)

private class Env(transactions: List<Transaction> = emptyList()) {
    val filterStore = TransactionFilterStore()
    private val repository = WindowedTransactions(transactions)
    private val session = InMemorySessionPointers(currentWorkspaceId = WORKSPACE)

    fun viewModel(
        accountId: AccountId? = null,
        anchorEpochDay: Long? = null,
    ): TransactionFiltersViewModel =
        TransactionFiltersViewModel(
            accountId = accountId,
            anchorEpochDay = anchorEpochDay,
            filterStore = filterStore,
            getTransactionsByAccount = GetTransactionsByAccountUseCase(repository),
            getAccounts = GetAccountsUseCase(WorkspaceAccounts, session),
            getCategories = GetCategoriesUseCase(OneCategory, session),
            uiPreferences = FakeUiPreferences(),
            clock = ClockUseCase(FixedClock(TODAY.atStartOfDayIn(TimeZone.UTC))),
        )
}

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/** Applies the window the way the DAO does, so a forgotten bound would fail these tests. */
private class WindowedTransactions(transactions: List<Transaction>) : TransactionRepository {
    private val rows = MutableStateFlow(transactions)

    override fun getCategorizedWindow(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
        limit: Int,
    ): Flow<List<CategorizedTransaction>> = rows.map { all ->
        all.filter { it.operationDate in window }
            .filter { accountId == null || it.accountId == accountId }
            .take(limit)
            .map { row ->
                CategorizedTransaction(
                    transaction = row,
                    categoryName = "Groceries",
                    // What the DAO's correlated subquery reports: the group's size in the whole
                    // table, so a page holding part of a group can be told apart from a whole one.
                    splitLegCount = all.count { it.splitId != null && it.splitId == row.splitId },
                )
            }
    }

    override fun getTotals(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
    ): Flow<List<TransactionTotal>> = flowOf(emptyList())

    override fun getAll(): Flow<List<Transaction>> = rows
    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> = rows
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> = rows
    override suspend fun getById(id: TransactionId): Transaction? = rows.value.find { it.id == id }
    override suspend fun getByTransferId(transferId: TransferId): List<Transaction> =
        rows.value.filter { it.transferId == transferId }
    override suspend fun getBySplitId(splitId: SplitId): List<Transaction> =
        rows.value.filter { it.splitId == splitId }
    override suspend fun insert(transaction: Transaction) = Unit
    override suspend fun update(transaction: Transaction) = Unit
    override suspend fun delete(id: TransactionId) = Unit
    override suspend fun restore(id: TransactionId): Transaction? = null
}

/** One live account and one retired one, so the screen has something to leave out. */
private object WorkspaceAccounts : AccountRepository {
    private val account = anAccount(id = ACCOUNT, workspaceId = WORKSPACE, name = "Everyday")

    private val archived = anAccount(
        id = accountId("acc-old"),
        workspaceId = WORKSPACE,
        name = "Old card",
        archived = true,
    )

    private val accounts = listOf(account, archived)

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

private object OneCategory : CategoryRepository {
    private val category = aCategory(id = CATEGORY, workspaceId = WORKSPACE, name = "Groceries")

    override fun getAll(): Flow<List<Category>> = flowOf(listOf(category))
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = flowOf(listOf(category))
    override suspend fun getById(id: CategoryId): Category? = category.takeIf { it.id == id }
    override suspend fun insert(category: Category) = Unit
    override suspend fun update(category: Category) = Unit
    override suspend fun delete(id: CategoryId) = Unit
}
