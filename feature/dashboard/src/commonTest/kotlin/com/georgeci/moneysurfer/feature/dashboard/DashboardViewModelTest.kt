package com.georgeci.moneysurfer.feature.dashboard

import com.georgeci.moneysurfer.domain.OfflineBuildFlags
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalContributionRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeSavingsGoalRepository
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetGoalsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetRecentTransactionsUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "recentTransactionsEmpty is true when no transactions are logged" {
        val ws = workspaceId("ws-1")
        val account = anAccount(id = accountId("a-1"), workspaceId = ws)
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(account)),
            transactions = FakeTransactionRepository(emptyList()),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.recentTransactionsEmpty shouldBe true
    }

    "recentTransactionsEmpty is true when only an opening-balance transaction exists" {
        val ws = workspaceId("ws-1")
        val account = anAccount(id = accountId("a-1"), workspaceId = ws)
        val opening = aTransaction(
            id = transactionId("tx-open"),
            workspaceId = ws,
            accountId = account.id,
            type = TransactionType.OPENING_BALANCE,
        )
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(account)),
            transactions = FakeTransactionRepository(listOf(opening)),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.recentTransactionsEmpty shouldBe true
    }

    "recentTransactionsEmpty is false once a real transaction is logged" {
        val ws = workspaceId("ws-1")
        val account = anAccount(id = accountId("a-1"), workspaceId = ws)
        val expense = aTransaction(
            id = transactionId("tx-1"),
            workspaceId = ws,
            accountId = account.id,
            type = TransactionType.EXPENSE,
        )
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(account)),
            transactions = FakeTransactionRepository(listOf(expense)),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.recentTransactionsEmpty shouldBe false
    }
})

private fun newViewModel(
    ws: WorkspaceId,
    accounts: FakeAccountRepository,
    transactions: FakeTransactionRepository,
): DashboardViewModel {
    val session = InMemorySessionPointers(currentWorkspaceId = ws)
    return DashboardViewModel(
        getAccounts = GetAccountsUseCase(accounts, session),
        getRecentTransactions = GetRecentTransactionsUseCase(transactions, session),
        getGoals = GetGoalsUseCase(FakeSavingsGoalRepository(), FakeGoalContributionRepository(), session),
        offlineBuildFlags = OfflineBuildFlags(isOffline = false),
    )
}

private class FakeTransactionRepository(
    initial: List<Transaction>,
) : TransactionRepository {
    private val state = MutableStateFlow(initial)

    override fun getAll(): Flow<List<Transaction>> = state
    override fun getAllCategorized(): Flow<List<CategorizedTransaction>> = flowOf(emptyList())
    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> = state
    override fun getByAccountIdCategorized(accountId: AccountId): Flow<List<CategorizedTransaction>> =
        flowOf(emptyList())
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> = state
    override suspend fun getById(id: TransactionId): Transaction? =
        state.value.firstOrNull { it.id == id }
    override suspend fun insert(transaction: Transaction) = Unit
    override suspend fun update(transaction: Transaction) = Unit
    override suspend fun delete(id: TransactionId) = Unit
}

private class FakeAccountRepository(
    initial: List<Account>,
) : AccountRepository {
    private val state = MutableStateFlow(initial)

    override fun getAll(): Flow<List<Account>> = state
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = state
    override suspend fun getById(id: AccountId): Account? = state.value.firstOrNull { it.id == id }
    override suspend fun insert(account: Account) = Unit
    override suspend fun update(account: Account) = Unit
    override suspend fun delete(id: AccountId) = Unit
    override suspend fun applyDelta(accountId: AccountId, delta: Money) = Unit
    override suspend fun setBalance(accountId: AccountId, balance: Money) = Unit
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) = Unit
}
