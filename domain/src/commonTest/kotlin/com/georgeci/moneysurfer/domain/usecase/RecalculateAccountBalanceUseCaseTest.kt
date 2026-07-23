package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.transactionId
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

class RecalculateAccountBalanceUseCaseTest : StringSpec({

    val a = accountId("a-1")

    "sums income / expense / opening_balance for the account" {
        val rows = listOf(
            aTransaction(
                id = transactionId("t-1"),
                accountId = a,
                money = Money.fromMinor(1000),
                type = TransactionType.OPENING_BALANCE,
            ),
            aTransaction(
                id = transactionId("t-2"),
                accountId = a,
                money = Money.fromMinor(300),
                type = TransactionType.INCOME,
            ),
            aTransaction(
                id = transactionId("t-3"),
                accountId = a,
                money = Money.fromMinor(50),
                type = TransactionType.EXPENSE,
            ),
            aTransaction(
                id = transactionId("t-4"),
                accountId = a,
                money = Money.fromMinor(150),
                type = TransactionType.EXPENSE,
            ),
        )
        val env = RecalcEnv(rows)

        env.useCase(a) shouldBe Money.fromMinor(1100)
        env.balances[a] shouldBe 1100L
    }

    "ignores planned rows" {
        val rows = listOf(
            aTransaction(accountId = a, money = Money.fromMinor(100), type = TransactionType.INCOME),
            aTransaction(
                accountId = a,
                money = Money.fromMinor(50),
                type = TransactionType.EXPENSE,
                status = TransactionStatus.PLANNED,
            ),
        )
        val env = RecalcEnv(rows)

        env.useCase(a) shouldBe Money.fromMinor(100)
    }

    "empty history yields zero" {
        val env = RecalcEnv(emptyList())
        env.useCase(a) shouldBe Money.zero()
    }
})

private class RecalcEnv(rows: List<Transaction>) {
    val balances = mutableMapOf<AccountId, Long>()

    private val txRepo = object : TransactionRepository {
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
        override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> = flowOf(rows)
        override suspend fun getById(id: TransactionId): Transaction? = rows.find { it.id == id }
        override suspend fun insert(transaction: Transaction) {}
        override suspend fun update(transaction: Transaction) {}
        override suspend fun delete(id: TransactionId) {}
    }

    private val accRepo = object : AccountRepository {
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

    private val recalc = RecalculateAccountBalanceUseCase(txRepo, accRepo)

    suspend fun useCase(id: AccountId): Money = recalc(id)
}
