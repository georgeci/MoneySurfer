package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.SplitId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory transaction + account stores wired to a real [ApplyTransactionChangeUseCase], shared by
 * the tests of the use cases that write through it. Balances are tracked in minor units so a test
 * can assert the side effect of a write without a real database.
 */
class TransactionStoreEnv {
    val txStore = mutableMapOf<TransactionId, Transaction>()
    val balances = mutableMapOf<AccountId, Long>()

    val txRepo = object : TransactionRepository {
        override fun getAll(): Flow<List<Transaction>> = flowOf(txStore.values.toList())
        override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> =
            flowOf(txStore.values.filter { it.accountId == accountId })
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
            flowOf(txStore.values.filter { it.workspaceId == workspaceId })
        override suspend fun getById(id: TransactionId): Transaction? = txStore[id]
        override suspend fun getByTransferId(transferId: TransferId): List<Transaction> =
            txStore.values.filter { it.transferId == transferId }
        override suspend fun getBySplitId(splitId: SplitId): List<Transaction> =
            txStore.values.filter { it.splitId == splitId }
        override suspend fun insert(transaction: Transaction) { txStore[transaction.id] = transaction }
        override suspend fun update(transaction: Transaction) { txStore[transaction.id] = transaction }
        override suspend fun delete(id: TransactionId) { txStore.remove(id) }
    }

    val accRepo = object : AccountRepository {
        override fun getAll(): Flow<List<Account>> = flowOf(emptyList())
        override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = flowOf(emptyList())
        override suspend fun getById(id: AccountId): Account? = null

        // Accounts are only ever moved by a delta here; the rows themselves are not the subject.
        override suspend fun insert(account: Account) = Unit
        override suspend fun update(account: Account) = Unit
        override suspend fun delete(id: AccountId) = Unit
        override suspend fun applyDelta(accountId: AccountId, delta: Money) {
            balances[accountId] = (balances[accountId] ?: 0L) + delta.minor
        }
        override suspend fun setBalance(accountId: AccountId, balance: Money) {
            balances[accountId] = balance.minor
        }
        override suspend fun reorder(orderedIds: List<AccountId>) = Unit
        override suspend fun setArchived(accountId: AccountId, archived: Boolean) = Unit
    }

    val applyChange = ApplyTransactionChangeUseCase(txRepo, accRepo)

    suspend fun useCase(old: Transaction?, new: Transaction?) {
        applyChange(old, new)
    }

    /** Puts rows in without going through [applyChange], so a test can stage a starting state. */
    fun seed(vararg transactions: Transaction) {
        transactions.forEach { txStore[it.id] = it }
    }

    fun balanceOf(id: AccountId): Money = Money.fromMinor(balances[id] ?: 0L)
}
