package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class ApplyTransactionChangeUseCaseTest : StringSpec({

    val a = accountId("a-1")
    val b = accountId("a-2")
    val hundred = Money.fromMinor(100)

    "create income increments cache by +amount" {
        val env = ApplyTxEnv()
        val tx = aTransaction(accountId = a, money = hundred, type = TransactionType.INCOME)

        env.useCase(old = null, new = tx)

        env.balanceOf(a) shouldBe hundred
        env.txStore[tx.id] shouldBe tx
    }

    "create expense decrements cache by amount" {
        val env = ApplyTxEnv()
        val tx = aTransaction(accountId = a, money = hundred, type = TransactionType.EXPENSE)

        env.useCase(old = null, new = tx)

        env.balanceOf(a) shouldBe -hundred
    }

    "update amount applies only the difference" {
        val env = ApplyTxEnv()
        val old = aTransaction(accountId = a, money = hundred, type = TransactionType.EXPENSE)
        env.useCase(old = null, new = old)
        val new = old.copy(money = Money.fromMinor(150))

        env.useCase(old = old, new = new)

        env.balanceOf(a) shouldBe Money.fromMinor(-150)
    }

    "moving expense between accounts unwinds old and applies new" {
        val env = ApplyTxEnv()
        val old = aTransaction(accountId = a, money = hundred, type = TransactionType.EXPENSE)
        env.useCase(old = null, new = old)
        val new = old.copy(accountId = b)

        env.useCase(old = old, new = new)

        env.balanceOf(a) shouldBe Money.zero()
        env.balanceOf(b) shouldBe -hundred
    }

    "planned → actual applies impact" {
        val env = ApplyTxEnv()
        val planned = aTransaction(
            accountId = a,
            money = hundred,
            type = TransactionType.EXPENSE,
            status = TransactionStatus.PLANNED,
        )
        env.useCase(old = null, new = planned)
        env.balanceOf(a) shouldBe Money.zero()

        val actual = planned.copy(status = TransactionStatus.ACTUAL)
        env.useCase(old = planned, new = actual)

        env.balanceOf(a) shouldBe -hundred
    }

    "actual → planned unwinds impact" {
        val env = ApplyTxEnv()
        val tx = aTransaction(accountId = a, money = hundred, type = TransactionType.EXPENSE)
        env.useCase(old = null, new = tx)
        env.balanceOf(a) shouldBe -hundred

        env.useCase(old = tx, new = tx.copy(status = TransactionStatus.PLANNED))

        env.balanceOf(a) shouldBe Money.zero()
    }

    "delete unwinds impact and removes the row" {
        val env = ApplyTxEnv()
        val tx = aTransaction(accountId = a, money = hundred, type = TransactionType.INCOME)
        env.useCase(old = null, new = tx)

        env.useCase(old = tx, new = null)

        env.balanceOf(a) shouldBe Money.zero()
        env.txStore.containsKey(tx.id) shouldBe false
    }

    "repeat delete (null/null) is a no-op" {
        val env = ApplyTxEnv()
        env.useCase(old = null, new = null)
        env.balanceOf(a) shouldBe Money.zero()
    }

    "opening_balance behaves as a regular ACTUAL for cache" {
        val env = ApplyTxEnv()
        val tx = aTransaction(accountId = a, money = hundred, type = TransactionType.OPENING_BALANCE)

        env.useCase(old = null, new = tx)

        env.balanceOf(a) shouldBe hundred
    }
})

private class ApplyTxEnv {
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
        override suspend fun insert(transaction: Transaction) { txStore[transaction.id] = transaction }
        override suspend fun update(transaction: Transaction) { txStore[transaction.id] = transaction }
        override suspend fun delete(id: TransactionId) { txStore.remove(id) }
    }

    val accRepo = object : AccountRepository {
        override fun getAll(): Flow<List<Account>> = flowOf(emptyList())
        override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = flowOf(emptyList())
        override suspend fun getById(id: AccountId): Account? = null
        override suspend fun insert(account: Account) {}
        override suspend fun update(account: Account) {}
        override suspend fun delete(id: AccountId) {}
        override suspend fun applyDelta(accountId: AccountId, delta: Money) {
            balances[accountId] = (balances[accountId] ?: 0L) + delta.minor
        }
        override suspend fun setBalance(accountId: AccountId, balance: Money) {
            balances[accountId] = balance.minor
        }
        override suspend fun setArchived(accountId: AccountId, archived: Boolean) = Unit
    }

    private val applyChange = ApplyTransactionChangeUseCase(txRepo, accRepo)

    suspend fun useCase(old: Transaction?, new: Transaction?) {
        applyChange(old, new)
    }

    fun balanceOf(id: AccountId): Money = Money.fromMinor(balances[id] ?: 0L)
}
