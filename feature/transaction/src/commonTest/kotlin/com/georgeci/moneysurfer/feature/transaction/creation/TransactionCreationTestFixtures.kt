package com.georgeci.moneysurfer.feature.transaction.creation

import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.config.HostCapabilities
import com.georgeci.moneysurfer.domain.fixtures.FakeHostCapabilities
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.SplitId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateSplitTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransferUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.RestoreTransactionsUseCase
import com.georgeci.moneysurfer.domain.usecase.UpdateTransactionUseCase
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.navigation.DeleteTransactionWithUndo
import com.georgeci.moneysurfer.navigation.SnackbarController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Wait until the VM finishes its async `loadData()` and exposes a `Content` state.
 * Sending events before that point would hit the `Loading` reducers and silently no-op.
 */
internal suspend fun TransactionCreationViewModel.awaitContent(): TransactionCreationState.Content =
    first { it is TransactionCreationState.Content } as TransactionCreationState.Content

/**
 * A loaded form, built by hand.
 *
 * For the cases that are about what the state *derives* — which grid it offers, whether Save is
 * enabled — rather than about how the ViewModel got there: those need no repository, and building
 * the `Content` directly says what the case is about in one place.
 */
@Suppress("LongParameterList")
internal fun aCreationContent(
    amount: String = "",
    type: TransactionTypeUi = TransactionTypeUi.Expense,
    accounts: List<Account> = emptyList(),
    categories: List<Category> = emptyList(),
    selectedAccount: Account? = accounts.firstOrNull(),
    selectedCategory: Category? = categories.firstOrNull(),
    fromAccount: Account? = null,
    toAccount: Account? = null,
    toAmount: String = "",
    splitLines: List<TransactionSplitLineUi> = emptyList(),
): TransactionCreationState.Content = TransactionCreationState.Content(
    amount = amount,
    note = "",
    type = type,
    accounts = accounts,
    categories = categories,
    selectedAccount = selectedAccount,
    selectedCategory = selectedCategory,
    isEditMode = false,
    editingTransactionId = null,
    timestamp = 0L,
    categoryUsageCounts = emptyMap(),
    displayCategories = categories,
    fromAccount = fromAccount,
    toAccount = toAccount,
    toAmount = toAmount,
    splitLines = splitLines,
)

/**
 * Real use cases over in-memory repositories: tests stage accounts + categories so the VM's
 * `loadData()` resolves a default selection, then assert on what the fakes ended up holding.
 */
internal class TransactionCreationFixture(workspaceId: WorkspaceId) {
    val accountRepository = FakeAccountRepository()
    val transactionRepository = FakeTransactionRepository()
    val categoryRepository = FakeCategoryRepository()
    val session = InMemorySessionPointers(currentWorkspaceId = workspaceId)
    val snackbar = SnackbarController()
    private val clock = ClockUseCase()
    private val applyChange = ApplyTransactionChangeUseCase(transactionRepository, accountRepository)

    fun createViewModel(
        editingTransactionId: TransactionId? = null,
        duplicateOf: TransactionId? = null,
        prefillAccount: AccountId? = null,
        openAsTransfer: Boolean = false,
        hostCapabilities: HostCapabilities = FakeHostCapabilities(),
    ) = TransactionCreationViewModel(
        seed = duplicateOf?.let {
            TransactionCreationSeed(it, TransactionCreationSeed.Mode.Duplicate)
        } ?: editingTransactionId?.let {
            TransactionCreationSeed(it, TransactionCreationSeed.Mode.Edit)
        },
        accountId = prefillAccount,
        openAsTransfer = openAsTransfer,
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
        createSplitTransaction = CreateSplitTransactionUseCase(
            applyTransactionChange = applyChange,
            getCurrentTime = GetCurrentTimeUseCase(clock),
        ),
        deleteWithUndo = DeleteTransactionWithUndo(
            deleteTransaction = DeleteTransactionUseCase(transactionRepository, applyChange),
            restoreTransactions = RestoreTransactionsUseCase(applyChange),
            snackbar = snackbar,
        ),
        getCurrentTime = GetCurrentTimeUseCase(clock),
        transactionRepository = transactionRepository,
        hostCapabilities = hostCapabilities,
        snackbar = snackbar,
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

internal class FakeAccountRepository : AccountRepository {
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
    override suspend fun reorder(orderedIds: List<AccountId>) = Unit
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) {
        val current = byId[accountId] ?: return
        byId[accountId] = current.copy(archived = archived)
        byWorkspace.value = byId.values.toList()
    }
}

internal class FakeTransactionRepository : TransactionRepository {
    val inserted = mutableListOf<Transaction>()
    private val byId = mutableMapOf<TransactionId, Transaction>()
    private val all = MutableStateFlow<List<Transaction>>(emptyList())

    /** Rows a delete tombstoned — out of every read, still there for a restore. */
    private val tombstones = mutableMapOf<TransactionId, Transaction>()

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
    override suspend fun getBySplitId(splitId: SplitId): List<Transaction> =
        byId.values.filter { it.splitId == splitId }
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
        byId.remove(id)?.let { tombstones[id] = it }
        all.value = byId.values.toList()
    }

    override suspend fun restore(id: TransactionId): Transaction? =
        tombstones.remove(id)?.also {
            byId[id] = it
            all.value = byId.values.toList()
        }
}

internal class FakeCategoryRepository : CategoryRepository {
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
