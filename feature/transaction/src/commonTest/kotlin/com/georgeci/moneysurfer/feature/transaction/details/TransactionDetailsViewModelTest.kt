package com.georgeci.moneysurfer.feature.transaction.details

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionByIdUseCase
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.navigation.SnackbarController
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_undo
import moneysurfer.feature.transaction.generated.resources.transaction_details_deleted_snackbar

/**
 * Delete + Undo flow for `TransactionDetailsViewModel`. Exercises the real
 * `DeleteTransactionUseCase` / `ApplyTransactionChangeUseCase` against in-memory fakes so
 * the balance side effects of delete and the undo are both covered.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionDetailsViewModelTest : StringSpec({

    val ws: WorkspaceId = workspaceId("ws-1")

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "delete removes the transaction and shows an undo snackbar" {
        runTest {
            val account = anAccount(id = accountId("a-1"), workspaceId = ws, balance = 500.dollars)
            val transaction = aTransaction(
                id = transactionId("t-1"),
                workspaceId = ws,
                accountId = account.id,
                money = 80.dollars,
                categoryId = null,
            )
            val fixture = Fixture(ws)
            fixture.seed(account, transaction)
            val vm = fixture.createViewModel(transaction.id)
            try {
                vm.awaitContent()

                fixture.snackbar.requests.test {
                    vm.onEvent(TransactionDetailsEvent.OnDeleteConfirmed)
                    val request = awaitItem()
                    request.message shouldBe Res.string.transaction_details_deleted_snackbar
                    request.actionLabel shouldBe Res.string.transaction_details_delete_undo
                    request.onAction.shouldNotBeNull()
                }

                fixture.transactionRepository.getById(transaction.id) shouldBe null
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "tapping Undo restores the deleted transaction" {
        runTest {
            val account = anAccount(id = accountId("a-1"), workspaceId = ws, balance = 500.dollars)
            val transaction = aTransaction(
                id = transactionId("t-1"),
                workspaceId = ws,
                accountId = account.id,
                money = 80.dollars,
                categoryId = null,
            )
            val fixture = Fixture(ws)
            fixture.seed(account, transaction)
            val vm = fixture.createViewModel(transaction.id)
            try {
                vm.awaitContent()

                var onUndo: (suspend () -> Unit)? = null
                fixture.snackbar.requests.test {
                    vm.onEvent(TransactionDetailsEvent.OnDeleteConfirmed)
                    onUndo = awaitItem().onAction
                }
                fixture.transactionRepository.getById(transaction.id) shouldBe null

                onUndo.shouldNotBeNull().invoke()

                fixture.transactionRepository.getById(transaction.id) shouldBe transaction
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }
})

private suspend fun TransactionDetailsViewModel.awaitContent(): TransactionDetailsState.Content =
    first { it is TransactionDetailsState.Content } as TransactionDetailsState.Content

private class Fixture(workspaceId: WorkspaceId) {
    val accountRepository = FakeAccountRepository()
    val transactionRepository = FakeTransactionRepository()
    val categoryRepository = FakeCategoryRepository()
    val snackbar = SnackbarController()
    private val session = InMemorySessionPointers(currentWorkspaceId = workspaceId)
    private val applyChange = ApplyTransactionChangeUseCase(transactionRepository, accountRepository)

    suspend fun seed(account: Account, transaction: Transaction) {
        accountRepository.insert(account)
        transactionRepository.insert(transaction)
    }

    fun createViewModel(transactionId: TransactionId) = TransactionDetailsViewModel(
        transactionId = transactionId,
        getTransactionById = GetTransactionByIdUseCase(transactionRepository),
        getAccountById = GetAccountByIdUseCase(accountRepository),
        getCategories = GetCategoriesUseCase(categoryRepository, session),
        deleteTransaction = DeleteTransactionUseCase(transactionRepository, applyChange),
        applyTransactionChange = applyChange,
        snackbar = snackbar,
    )
}

private class FakeAccountRepository : AccountRepository {
    private val byId = mutableMapOf<AccountId, Account>()
    private val byWorkspace = MutableStateFlow<List<Account>>(emptyList())

    override fun getAll(): Flow<List<Account>> = byWorkspace
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = byWorkspace
    override suspend fun getById(id: AccountId): Account? = byId[id]
    override suspend fun insert(account: Account) {
        byId[account.id] = account
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun update(account: Account) {
        byId[account.id] = account
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun delete(id: AccountId) {
        byId.remove(id)
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun applyDelta(accountId: AccountId, delta: Money) {
        val current = byId[accountId] ?: return
        byId[accountId] = current.copy(balance = current.balance + delta)
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun setBalance(accountId: AccountId, balance: Money) {
        val current = byId[accountId] ?: return
        byId[accountId] = current.copy(balance = balance)
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) {
        val current = byId[accountId] ?: return
        byId[accountId] = current.copy(archived = archived)
        byWorkspace.value = byId.values.toList()
    }
}

private class FakeTransactionRepository : TransactionRepository {
    private val byId = mutableMapOf<TransactionId, Transaction>()
    private val all = MutableStateFlow<List<Transaction>>(emptyList())

    override fun getAll(): Flow<List<Transaction>> = all
    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> = all
    override fun getCategorizedWindow(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
        limit: Int,
    ) = error("not used")
    override fun getTotals(accountId: AccountId?, window: TransactionPeriodWindow) = error("not used")
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> = all
    override suspend fun getById(id: TransactionId): Transaction? = byId[id]
    override suspend fun insert(transaction: Transaction) {
        byId[transaction.id] = transaction
        all.value = byId.values.toList()
    }
    override suspend fun update(transaction: Transaction) {
        byId[transaction.id] = transaction
        all.value = byId.values.toList()
    }
    override suspend fun delete(id: TransactionId) {
        byId.remove(id)
        all.value = byId.values.toList()
    }
}

private class FakeCategoryRepository : CategoryRepository {
    private val byWorkspace = MutableStateFlow<List<Category>>(emptyList())

    override fun getAll(): Flow<List<Category>> = byWorkspace
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = byWorkspace
    override suspend fun getById(id: CategoryId): Category? = null
    override suspend fun insert(category: Category) = Unit
    override suspend fun update(category: Category) = Unit
    override suspend fun delete(id: CategoryId) = Unit
}
