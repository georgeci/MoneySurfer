package com.georgeci.moneysurfer.feature.transaction.creation

import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransferUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.UpdateTransactionUseCase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * Save flows for `TransactionCreationViewModel`. Each test stages accounts +
 * categories so the VM's `loadData()` resolves a default selection, then drives
 * the relevant `On*` events and asserts on the fake repositories.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransactionCreationViewModelTest : StringSpec({

    val ws: WorkspaceId = workspaceId("ws-1")

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "save EXPENSE persists a transaction with the account's currency and updates balance" {
        runTest {
            val acc = anAccount(
                id = accountId("a-1"),
                workspaceId = ws,
                currencyCode = USD,
                balance = 500.dollars,
            )
            val expenseCategory = aCategory(
                id = categoryId("c-exp"),
                workspaceId = ws,
                type = CategoryType.EXPENSE,
            )
            val fixture = Fixture(ws).apply {
                accountRepository.seed(acc)
                categoryRepository.seed(expenseCategory)
            }
            val vm = fixture.createViewModel(prefillAccount = acc.id)

            vm.onEvent(TransactionCreationEvent.OnAmountChanged("80"))
            vm.onEvent(TransactionCreationEvent.OnSaveClick)

            val saved = fixture.transactionRepository.inserted.single()
            saved.type shouldBe TransactionType.EXPENSE
            saved.money shouldBe 80.dollars
            saved.currencyCode shouldBe USD
            saved.accountId shouldBe acc.id
            saved.categoryId shouldBe expenseCategory.id

            fixture.accountRepository.byId[acc.id]!!.balance shouldBe 420.dollars
        }
    }

    "switching to Income picks an income category and save persists INCOME" {
        runTest {
            val acc = anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD)
            val incomeCategory = aCategory(
                id = categoryId("c-inc"),
                workspaceId = ws,
                type = CategoryType.INCOME,
                name = "Salary",
            )
            val expenseCategory = aCategory(
                id = categoryId("c-exp"),
                workspaceId = ws,
                type = CategoryType.EXPENSE,
            )
            val fixture = Fixture(ws).apply {
                accountRepository.seed(acc)
                categoryRepository.seed(expenseCategory, incomeCategory)
            }
            val vm = fixture.createViewModel()

            vm.onEvent(TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Income))
            vm.onEvent(TransactionCreationEvent.OnAmountChanged("125"))
            vm.onEvent(TransactionCreationEvent.OnSaveClick)

            val saved = fixture.transactionRepository.inserted.single()
            saved.type shouldBe TransactionType.INCOME
            saved.money shouldBe 125.dollars
            saved.categoryId shouldBe incomeCategory.id
        }
    }

    "same-currency transfer creates two paired legs with equal amounts" {
        runTest {
            val from = anAccount(
                id = accountId("a-from"),
                workspaceId = ws,
                currencyCode = USD,
                balance = 1_000.dollars,
            )
            val to = anAccount(
                id = accountId("a-to"),
                workspaceId = ws,
                currencyCode = USD,
                balance = Money.zero(),
            )
            val expenseCategory = aCategory(
                id = categoryId("c-exp"),
                workspaceId = ws,
                type = CategoryType.EXPENSE,
            )
            val fixture = Fixture(ws).apply {
                accountRepository.seed(from, to)
                categoryRepository.seed(expenseCategory)
            }
            val vm = fixture.createViewModel()

            vm.onEvent(TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Transfer))
            vm.onEvent(TransactionCreationEvent.OnAmountChanged("150"))
            vm.onEvent(TransactionCreationEvent.OnSaveClick)

            val txns = fixture.transactionRepository.inserted
            txns.size shouldBe 2
            val expense = txns.single { it.type == TransactionType.EXPENSE }
            val income = txns.single { it.type == TransactionType.INCOME }
            expense.accountId shouldBe from.id
            income.accountId shouldBe to.id
            expense.money shouldBe 150.dollars
            income.money shouldBe 150.dollars
            expense.transferId shouldNotBe null
            expense.transferId shouldBe income.transferId

            fixture.accountRepository.byId[from.id]!!.balance shouldBe 850.dollars
            fixture.accountRepository.byId[to.id]!!.balance shouldBe 150.dollars
        }
    }

    "cross-currency transfer uses separate fromMoney and toMoney with each account's currency" {
        runTest {
            val from = anAccount(
                id = accountId("a-usd"),
                workspaceId = ws,
                currencyCode = USD,
                balance = 500.dollars,
            )
            val to = anAccount(
                id = accountId("a-eur"),
                workspaceId = ws,
                currencyCode = EUR,
                balance = Money.zero(),
            )
            val expenseCategory = aCategory(
                id = categoryId("c-exp"),
                workspaceId = ws,
                type = CategoryType.EXPENSE,
            )
            val fixture = Fixture(ws).apply {
                accountRepository.seed(from, to)
                categoryRepository.seed(expenseCategory)
            }
            val vm = fixture.createViewModel()

            vm.onEvent(TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Transfer))
            // The VM may seed fromAccount=first and toAccount=second; force the order explicitly.
            fixture.pickFromAccount(vm, from.id)
            fixture.pickToAccount(vm, to.id)
            vm.onEvent(TransactionCreationEvent.OnAmountChanged("100"))
            vm.onEvent(TransactionCreationEvent.OnToAmountChanged("92"))
            vm.onEvent(TransactionCreationEvent.OnSaveClick)

            val expense = fixture.transactionRepository.inserted.single { it.type == TransactionType.EXPENSE }
            val income = fixture.transactionRepository.inserted.single { it.type == TransactionType.INCOME }
            expense.money shouldBe 100.dollars
            expense.currencyCode shouldBe USD
            income.money shouldBe 92.dollars
            income.currencyCode shouldBe EUR

            fixture.accountRepository.byId[from.id]!!.balance shouldBe 400.dollars
            fixture.accountRepository.byId[to.id]!!.balance shouldBe 92.dollars
        }
    }
})

private class Fixture(workspaceId: WorkspaceId) {
    val accountRepository = FakeAccountRepository()
    val transactionRepository = FakeTransactionRepository()
    val categoryRepository = FakeCategoryRepository()
    val session = InMemorySessionPointers(currentWorkspaceId = workspaceId)
    private val clock = ClockUseCase()
    private val applyChange = ApplyTransactionChangeUseCase(transactionRepository, accountRepository)

    fun createViewModel(
        editingTransactionId: TransactionId? = null,
        prefillAccount: AccountId? = null,
    ) = TransactionCreationViewModel(
        transactionId = editingTransactionId,
        accountId = prefillAccount,
        getAccounts = GetAccountsUseCase(accountRepository, session),
        getCategories = GetCategoriesUseCase(categoryRepository, session),
        getTransactionById = GetTransactionByIdUseCase(transactionRepository),
        createTransaction = CreateTransactionUseCase(applyChange),
        updateTransaction = UpdateTransactionUseCase(transactionRepository, applyChange),
        createTransfer = CreateTransferUseCase(
            categoryRepository = categoryRepository,
            applyTransactionChange = applyChange,
            getCurrentTime = GetCurrentTimeUseCase(clock),
        ),
        getCurrentTime = GetCurrentTimeUseCase(clock),
        transactionRepository = transactionRepository,
    )

    fun pickFromAccount(vm: TransactionCreationViewModel, id: AccountId) {
        vm.onEvent(TransactionCreationEvent.OnOpenFromAccountChooser)
        vm.onEvent(TransactionCreationEvent.OnAccountPicked(id))
    }

    fun pickToAccount(vm: TransactionCreationViewModel, id: AccountId) {
        vm.onEvent(TransactionCreationEvent.OnOpenToAccountChooser)
        vm.onEvent(TransactionCreationEvent.OnAccountPicked(id))
    }
}

private class FakeAccountRepository : AccountRepository {
    val byId: MutableMap<AccountId, Account> = mutableMapOf()
    private val byWorkspace = MutableStateFlow<List<Account>>(emptyList())

    fun seed(vararg accounts: Account) {
        accounts.forEach { byId[it.id] = it }
        byWorkspace.value = byId.values.toList()
    }

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
}

private class FakeTransactionRepository : TransactionRepository {
    val inserted = mutableListOf<Transaction>()
    private val byId = mutableMapOf<TransactionId, Transaction>()
    private val all = MutableStateFlow<List<Transaction>>(emptyList())

    override fun getAll(): Flow<List<Transaction>> = all
    override fun getAllCategorized(): Flow<List<CategorizedTransaction>> = error("not used")
    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> = all
    override fun getByAccountIdCategorized(accountId: AccountId) = error("not used")
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> = all
    override suspend fun getById(id: TransactionId): Transaction? = byId[id]
    override suspend fun insert(transaction: Transaction) {
        inserted += transaction
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
    private val byId = mutableMapOf<CategoryId, Category>()
    private val byWorkspace = MutableStateFlow<List<Category>>(emptyList())

    fun seed(vararg categories: Category) {
        categories.forEach { byId[it.id] = it }
        byWorkspace.value = byId.values.toList()
    }

    override fun getAll(): Flow<List<Category>> = byWorkspace
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = byWorkspace
    override suspend fun getById(id: CategoryId): Category? = byId[id]
    override suspend fun insert(category: Category) {
        byId[category.id] = category
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun update(category: Category) {
        byId[category.id] = category
        byWorkspace.value = byId.values.toList()
    }
    override suspend fun delete(id: CategoryId) {
        byId.remove(id)
        byWorkspace.value = byId.values.toList()
    }
}
