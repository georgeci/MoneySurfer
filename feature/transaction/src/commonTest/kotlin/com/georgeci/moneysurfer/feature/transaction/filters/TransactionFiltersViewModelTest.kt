package com.georgeci.moneysurfer.feature.transaction.filters

import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.FakeUiPreferences
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.dollars
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
            getAccounts = GetAccountsUseCase(OneAccount, session),
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
            .map { CategorizedTransaction(transaction = it, categoryName = "Groceries") }
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
}

private object OneAccount : AccountRepository {
    private val account = anAccount(id = ACCOUNT, workspaceId = WORKSPACE, name = "Everyday")

    override suspend fun getById(id: AccountId): Account? = account.takeIf { it.id == id }
    override fun getAll(): Flow<List<Account>> = flowOf(listOf(account))
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = flowOf(listOf(account))
    override suspend fun insert(account: Account) = Unit
    override suspend fun update(account: Account) = Unit
    override suspend fun delete(id: AccountId) = Unit
    override suspend fun applyDelta(accountId: AccountId, delta: Money) = Unit
    override suspend fun setBalance(accountId: AccountId, balance: Money) = Unit
    override suspend fun reorder(orderedIds: List<AccountId>) = Unit
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) = Unit
}

private object OneCategory : CategoryRepository {
    private val category = aCategory(workspaceId = WORKSPACE, name = "Groceries")

    override fun getAll(): Flow<List<Category>> = flowOf(listOf(category))
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = flowOf(listOf(category))
    override suspend fun getById(id: CategoryId): Category? = category.takeIf { it.id == id }
    override suspend fun insert(category: Category) = Unit
    override suspend fun update(category: Category) = Unit
    override suspend fun delete(id: CategoryId) = Unit
}
