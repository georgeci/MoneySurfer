package com.georgeci.moneysurfer.feature.transaction.details

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
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
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransferCounterpartUseCase
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.navigation.SnackbarController
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
 * `TransactionDetailsViewModel` against in-memory fakes: the delete + undo flow (running the real
 * `DeleteTransactionUseCase` / `ApplyTransactionChangeUseCase`, so the balance side effects of both
 * are covered) and the expense / income / transfer variants the screen renders from.
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

    "an expense signs its amount and names the parent of a nested category" {
        runTest {
            val fixture = Fixture(ws)
            fixture.seedAccounts(anAccount(id = accountId("a-1"), workspaceId = ws))
            fixture.seedCategories(
                aCategory(id = categoryId("c-parent"), workspaceId = ws, name = "Dining"),
                aCategory(
                    id = categoryId("c-1"),
                    workspaceId = ws,
                    name = "Coffee",
                    parentId = categoryId("c-parent"),
                ),
            )
            fixture.seedTransactions(
                aTransaction(
                    id = transactionId("t-1"),
                    workspaceId = ws,
                    accountId = accountId("a-1"),
                    money = 4.dollars,
                    categoryId = categoryId("c-1"),
                    type = TransactionType.EXPENSE,
                ),
            )
            val vm = fixture.createViewModel(transactionId("t-1"))
            try {
                val content = vm.awaitContent()

                content.type shouldBe TransactionType.EXPENSE
                content.isTransfer shouldBe false
                content.isIncome shouldBe false
                content.formattedAmount.first() shouldBe '−'
                content.categoryName shouldBe "Coffee"
                content.parentCategoryName shouldBe "Dining"
                content.canDuplicate shouldBe true
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "income signs its amount with a plus and carries no transfer" {
        runTest {
            val fixture = Fixture(ws)
            fixture.seedAccounts(anAccount(id = accountId("a-1"), workspaceId = ws, name = "Savings"))
            fixture.seedTransactions(
                aTransaction(
                    id = transactionId("t-1"),
                    workspaceId = ws,
                    accountId = accountId("a-1"),
                    money = 1_500.dollars,
                    categoryId = null,
                    merchant = "Acme Ltd",
                    type = TransactionType.INCOME,
                ),
            )
            val vm = fixture.createViewModel(transactionId("t-1"))
            try {
                val content = vm.awaitContent()

                content.isIncome shouldBe true
                content.transfer shouldBe null
                content.formattedAmount.first() shouldBe '+'
                content.accountName shouldBe "Savings"
                content.merchant shouldBe "Acme Ltd"
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a transfer leg resolves the counterpart account and leaves the amount unsigned" {
        runTest {
            val transferId = TransferId("tr-1")
            val fixture = Fixture(ws)
            fixture.seedAccounts(
                anAccount(id = accountId("a-1"), workspaceId = ws, name = "Everyday"),
                anAccount(id = accountId("a-2"), workspaceId = ws, name = "Savings"),
            )
            fixture.seedTransactions(
                aTransaction(
                    id = transactionId("t-out"),
                    workspaceId = ws,
                    accountId = accountId("a-1"),
                    money = 200.dollars,
                    categoryId = null,
                    type = TransactionType.EXPENSE,
                    transferId = transferId,
                ),
                aTransaction(
                    id = transactionId("t-in"),
                    workspaceId = ws,
                    accountId = accountId("a-2"),
                    money = 200.dollars,
                    categoryId = null,
                    type = TransactionType.INCOME,
                    transferId = transferId,
                ),
            )

            // Both legs describe the same movement, so both must read "Everyday → Savings".
            listOf(transactionId("t-out"), transactionId("t-in")).forEach { legId ->
                val vm = fixture.createViewModel(legId)
                try {
                    val content = vm.awaitContent()

                    content.isTransfer shouldBe true
                    content.isIncome shouldBe false
                    content.canDuplicate shouldBe false
                    content.transfer shouldBe TransferLeg(
                        fromAccountName = "Everyday",
                        toAccountName = "Savings",
                    )
                    content.formattedAmount.first() shouldNotBe '−'
                    content.formattedAmount.first() shouldNotBe '+'
                } finally {
                    vm.viewModelScope.cancel()
                }
            }
        }
    }

    "a transfer leg whose pair is missing still renders as a transfer" {
        runTest {
            val fixture = Fixture(ws)
            fixture.seedAccounts(anAccount(id = accountId("a-1"), workspaceId = ws, name = "Everyday"))
            fixture.seedTransactions(
                aTransaction(
                    id = transactionId("t-out"),
                    workspaceId = ws,
                    accountId = accountId("a-1"),
                    money = 200.dollars,
                    categoryId = null,
                    type = TransactionType.EXPENSE,
                    transferId = TransferId("tr-orphan"),
                ),
            )
            val vm = fixture.createViewModel(transactionId("t-out"))
            try {
                val content = vm.awaitContent()

                content.isTransfer shouldBe true
                content.transfer shouldBe TransferLeg(fromAccountName = "Everyday", toAccountName = "")
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "tapping Duplicate asks to open the creation screen for this transaction" {
        runTest {
            val fixture = Fixture(ws)
            fixture.seedAccounts(anAccount(id = accountId("a-1"), workspaceId = ws))
            fixture.seedTransactions(
                aTransaction(
                    id = transactionId("t-1"),
                    workspaceId = ws,
                    accountId = accountId("a-1"),
                    categoryId = null,
                ),
            )
            val vm = fixture.createViewModel(transactionId("t-1"))
            try {
                vm.awaitContent()

                vm.sideEffects.effectFlow.test {
                    vm.onEvent(TransactionDetailsEvent.OnDuplicateClick)
                    awaitItem() shouldBe TransactionDetailsEffect.NavigateToDuplicate(transactionId("t-1"))
                }
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

    suspend fun seedAccounts(vararg accounts: Account) = accounts.forEach { accountRepository.insert(it) }

    suspend fun seedTransactions(vararg transactions: Transaction) =
        transactions.forEach { transactionRepository.insert(it) }

    suspend fun seedCategories(vararg categories: Category) = categories.forEach { categoryRepository.insert(it) }

    fun createViewModel(transactionId: TransactionId) = TransactionDetailsViewModel(
        transactionId = transactionId,
        getTransactionById = GetTransactionByIdUseCase(transactionRepository),
        getAccountById = GetAccountByIdUseCase(accountRepository),
        getCategories = GetCategoriesUseCase(categoryRepository, session),
        getTransferCounterpart = GetTransferCounterpartUseCase(transactionRepository),
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
    override suspend fun getByTransferId(transferId: TransferId): List<Transaction> =
        byId.values.filter { it.transferId == transferId }
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
    override suspend fun getById(id: CategoryId): Category? = byWorkspace.value.find { it.id == id }
    override suspend fun insert(category: Category) {
        byWorkspace.value = byWorkspace.value.filterNot { it.id == category.id } + category
    }
    override suspend fun update(category: Category) = insert(category)
    override suspend fun delete(id: CategoryId) {
        byWorkspace.value = byWorkspace.value.filterNot { it.id == id }
    }
}
