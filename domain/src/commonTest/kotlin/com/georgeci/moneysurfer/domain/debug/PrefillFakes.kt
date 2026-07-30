package com.georgeci.moneysurfer.domain.debug

import arrow.core.right
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.SplitId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.sync.api.LastSyncOutcome
import com.georgeci.moneysurfer.sync.api.SyncHandleStatus
import com.georgeci.moneysurfer.sync.api.SyncMode
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncResult
import com.georgeci.moneysurfer.sync.api.SyncState
import com.georgeci.moneysurfer.sync.api.SyncStep
import com.georgeci.moneysurfer.sync.api.SyncSummary
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import com.georgeci.moneysurfer.sync.coordinator.SyncHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory stores for the repositories the prefiller writes through. Only the reads and writes it
 * actually performs are modelled; the query surface each interface carries for the rest of the app
 * returns empty.
 *
 * [RecordingAccountRepository] tracks balances separately from the rows, mirroring the real split:
 * the row is inserted at zero and every balance movement arrives later as a delta from
 * `ApplyTransactionChangeUseCase`.
 */
internal class RecordingAccountRepository : AccountRepository {
    private val store = mutableMapOf<AccountId, Account>()
    private val balances = mutableMapOf<AccountId, Long>()

    val rows: List<Account> get() = store.values.toList()

    fun balanceOf(id: AccountId): Money = Money.fromMinor(balances[id] ?: 0L)

    override fun getAll(): Flow<List<Account>> = flowOf(rows)
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> =
        flowOf(rows.filter { it.workspaceId == workspaceId })

    override suspend fun getById(id: AccountId): Account? = store[id]

    override suspend fun insert(account: Account) {
        store[account.id] = account
    }

    override suspend fun update(account: Account) {
        store[account.id] = account
    }

    override suspend fun delete(id: AccountId) {
        store.remove(id)
    }

    override suspend fun applyDelta(accountId: AccountId, delta: Money) {
        balances[accountId] = (balances[accountId] ?: 0L) + delta.minor
    }

    override suspend fun setBalance(accountId: AccountId, balance: Money) {
        balances[accountId] = balance.minor
    }

    override suspend fun setArchived(accountId: AccountId, archived: Boolean) = Unit
    override suspend fun reorder(orderedIds: List<AccountId>) = Unit
}

internal class RecordingCategoryRepository : CategoryRepository {
    private val store = mutableMapOf<CategoryId, Category>()

    val rows: List<Category> get() = store.values.toList()

    override fun getAll(): Flow<List<Category>> = flowOf(rows)
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> =
        flowOf(rows.filter { it.workspaceId == workspaceId })

    override suspend fun getById(id: CategoryId): Category? = store[id]

    override suspend fun insert(category: Category) {
        store[category.id] = category
    }

    override suspend fun update(category: Category) {
        store[category.id] = category
    }

    override suspend fun delete(id: CategoryId) {
        store.remove(id)
    }
}

internal class RecordingBudgetRepository : BudgetRepository {
    private val store = mutableMapOf<BudgetId, Budget>()

    val rows: List<Budget> get() = store.values.toList()

    override fun getAll(): Flow<List<Budget>> = flowOf(rows)
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Budget>> =
        flowOf(rows.filter { it.workspaceId == workspaceId })

    override suspend fun getById(id: BudgetId): Budget? = store[id]

    override suspend fun insert(budget: Budget) {
        store[budget.id] = budget
    }

    override suspend fun update(budget: Budget) {
        store[budget.id] = budget
    }

    override suspend fun setActive(id: BudgetId, isActive: Boolean) = Unit

    override suspend fun delete(id: BudgetId) {
        store.remove(id)
    }
}

internal class RecordingTransactionRepository : TransactionRepository {
    private val store = mutableMapOf<TransactionId, Transaction>()

    val rows: List<Transaction> get() = store.values.toList()

    override fun getAll(): Flow<List<Transaction>> = flowOf(rows)
    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> =
        flowOf(rows.filter { it.accountId == accountId })

    override fun getCategorizedWindow(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
        limit: Int,
    ): Flow<List<CategorizedTransaction>> = flowOf(emptyList())

    override fun getTotals(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
    ): Flow<List<TransactionTotal>> = flowOf(emptyList())

    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> =
        flowOf(rows.filter { it.workspaceId == workspaceId })

    override suspend fun getById(id: TransactionId): Transaction? = store[id]
    override suspend fun getByTransferId(transferId: TransferId): List<Transaction> = emptyList()
    override suspend fun getBySplitId(splitId: SplitId): List<Transaction> = emptyList()

    override suspend fun insert(transaction: Transaction) {
        store[transaction.id] = transaction
    }

    override suspend fun update(transaction: Transaction) {
        store[transaction.id] = transaction
    }

    override suspend fun delete(id: TransactionId) {
        store.remove(id)
    }

    override suspend fun restore(id: TransactionId): Transaction? = null
}

/** Records the pushes the prefiller asks for — the guest/signed-in branch is the whole point. */
internal class RecordingSyncCoordinator : SyncCoordinator {
    val requests: MutableList<SyncReason> = mutableListOf()

    override val state: StateFlow<SyncState> = MutableStateFlow(SyncState.Idle)
    override val lastOutcome: StateFlow<LastSyncOutcome> = MutableStateFlow(LastSyncOutcome.None)

    override fun requestSync(reason: SyncReason, mode: SyncMode): SyncHandle {
        requests += reason
        return CompletedSyncHandle()
    }

    override fun cancelCurrent() = Unit
    override fun cancelAllQueued() = Unit
    override fun cancelAll() = Unit
}

/** Already-finished handle: the prefiller fires and forgets, it never awaits the result. */
private class CompletedSyncHandle : SyncHandle {
    private val summary = SyncSummary()

    override val id: SyncRequestId = SyncRequestId.uuid()
    override val status: StateFlow<SyncHandleStatus> = MutableStateFlow(SyncHandleStatus.Completed(summary))
    override val steps: SharedFlow<SyncStep> = MutableSharedFlow<SyncStep>(replay = 1)
        .also { it.tryEmit(SyncStep.Completed(summary)) }
    override val result: Deferred<SyncResult<SyncSummary>> =
        CompletableDeferred<SyncResult<SyncSummary>>().apply { complete(summary.right()) }

    override fun cancel() = Unit
}
