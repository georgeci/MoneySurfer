package com.georgeci.moneysurfer.feature.transaction.list

import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.FakeUiPreferences
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.transactionId
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
import com.georgeci.moneysurfer.domain.primitives.SplitId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
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
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionFilterStore
import com.georgeci.moneysurfer.navigation.DeleteTransactionWithUndo
import com.georgeci.moneysurfer.navigation.SnackbarController
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Clock
import kotlin.time.Instant

/** Fixed "today" for every list case: a Thursday, mid-month, mid-week. */
internal val TODAY = LocalDate(2025, 3, 27)

/** Mirrors the ViewModel's own page size — the tests page against the same boundary. */
internal const val PAGE_SIZE = 200

internal val WORKSPACE = WorkspaceId("ws-1")
internal val ACCOUNT = accountId("acc-1")
internal val OTHER_ACCOUNT = accountId("acc-2")

internal fun expense(
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

internal fun income(id: String, date: LocalDate = TODAY, amount: Int): Transaction =
    expense(id = id, date = date, amount = amount).copy(type = TransactionType.INCOME)

/** An expense of an exact number of minor units, for the amount-tolerance cases. */
internal fun expenseCents(id: String, cents: Long): Transaction =
    expense(id = id, amount = 0).copy(money = Money.fromMinor(cents))

internal fun TransactionsByAccountState.Content.rowCount(): Int = groups.sumOf { it.transactions.size }

internal fun TransactionsByAccountState.Content.rowIds(): List<String> =
    groups.flatMap { group -> group.transactions.map { it.id.value } }

internal fun TransactionsByAccountState.Content.rows(): List<TransactionRowUi> =
    groups.flatMap { it.transactions }

internal fun TransactionsByAccountViewModel.content(): TransactionsByAccountState.Content =
    currentState.shouldBeInstanceOf<TransactionsByAccountState.Content>()

/** What a fresh read of the store would return — i.e. what survives process death. */
internal suspend fun FakeUiPreferences.storedPeriodMode(): TransactionPeriodMode =
    transactionsPeriodMode.flow.first()

internal class Env(transactions: List<Transaction> = emptyList()) {
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

internal object SeededCategoryRepository : CategoryRepository {
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

internal class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

/**
 * Applies the window and the limit the way the DAO does, so a ViewModel that forgot to pass either
 * one would fail these tests rather than quietly reading everything.
 */
internal class WindowingTransactionRepository(
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
        return rows.map { all ->
            inWindow(window)
                .filter { row -> accountId == null || row.accountId == accountId }
                .take(limit)
                .map { row ->
                    CategorizedTransaction(
                        transaction = row,
                        categoryName = "Category",
                        // Counted over the whole table, exactly as the DAO's correlated subquery
                        // does — a group cut by the limit must still report its real size, or the
                        // list could not tell a complete group from a truncated one.
                        splitLegCount = all.count { it.splitId != null && it.splitId == row.splitId },
                    )
                }
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
    override suspend fun getBySplitId(splitId: SplitId): List<Transaction> =
        rows.value.filter { it.splitId == splitId }

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

internal object SingleAccountRepository : AccountRepository {
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
