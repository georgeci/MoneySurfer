package com.georgeci.moneysurfer.feature.transaction.creation

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.preferences.Pref
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.UserId
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionCreationViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Transfer is unreachable when transferEnabled flag is off`() = runTest(dispatcher) {
        val viewModel = buildViewModel(
            featureConfig = TransactionCreationFeatureConfig(transferEnabled = false),
        )
        advanceUntilIdle()

        viewModel.onEvent(TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Transfer))
        advanceUntilIdle()

        val state = viewModel.first { it is TransactionCreationState.Content }
            as TransactionCreationState.Content
        assertFalse(state.transferEnabled, "state should reflect the offline transferEnabled flag")
        assertFalse(state.isTransfer, "Transfer mode must remain unreachable when the flag is off")
        assertEquals(TransactionTypeUi.Expense, state.type)
    }

    @Test
    fun `Transfer is reachable when transferEnabled flag is on`() = runTest(dispatcher) {
        val viewModel = buildViewModel(
            featureConfig = TransactionCreationFeatureConfig(transferEnabled = true),
        )
        advanceUntilIdle()

        viewModel.onEvent(TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Transfer))
        advanceUntilIdle()

        val state = viewModel.first { it is TransactionCreationState.Content }
            as TransactionCreationState.Content
        assertTrue(state.transferEnabled)
        assertEquals(TransactionTypeUi.Transfer, state.type)
    }

    private fun buildViewModel(
        featureConfig: TransactionCreationFeatureConfig,
    ): TransactionCreationViewModel {
        val transactionRepository = EmptyTransactionRepository()
        val accountRepository = EmptyAccountRepository()
        val categoryRepository = EmptyCategoryRepository()
        val session = EmptySessionPointers()
        val getCurrentTime = GetCurrentTimeUseCase(ClockUseCase())
        val applyChange = ApplyTransactionChangeUseCase(transactionRepository, accountRepository)
        return TransactionCreationViewModel(
            transactionId = null,
            accountId = null,
            getAccounts = GetAccountsUseCase(accountRepository, session),
            getCategories = GetCategoriesUseCase(categoryRepository, session),
            getTransactionById = GetTransactionByIdUseCase(transactionRepository),
            createTransaction = CreateTransactionUseCase(applyChange),
            updateTransaction = UpdateTransactionUseCase(transactionRepository, applyChange),
            createTransfer = CreateTransferUseCase(categoryRepository, applyChange, getCurrentTime),
            getCurrentTime = getCurrentTime,
            transactionRepository = transactionRepository,
            featureConfig = featureConfig,
        )
    }
}

private class EmptyTransactionRepository : TransactionRepository {
    private val all = MutableStateFlow<List<Transaction>>(emptyList())
    private val categorized = MutableStateFlow<List<CategorizedTransaction>>(emptyList())
    override fun getAll(): Flow<List<Transaction>> = all
    override fun getAllCategorized(): Flow<List<CategorizedTransaction>> = categorized
    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> = all
    override fun getByAccountIdCategorized(accountId: AccountId): Flow<List<CategorizedTransaction>> = categorized
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> = all
    override suspend fun getById(id: TransactionId): Transaction? = null
    override suspend fun insert(transaction: Transaction) = Unit
    override suspend fun update(transaction: Transaction) = Unit
    override suspend fun delete(id: TransactionId) = Unit
}

private class EmptyAccountRepository : AccountRepository {
    private val all = MutableStateFlow<List<Account>>(emptyList())
    override fun getAll(): Flow<List<Account>> = all
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = all
    override suspend fun getById(id: AccountId): Account? = null
    override suspend fun insert(account: Account) = Unit
    override suspend fun update(account: Account) = Unit
    override suspend fun delete(id: AccountId) = Unit
    override suspend fun applyDelta(accountId: AccountId, delta: Money) = Unit
    override suspend fun setBalance(accountId: AccountId, balance: Money) = Unit
}

private class EmptyCategoryRepository : CategoryRepository {
    private val all = MutableStateFlow<List<Category>>(emptyList())
    override fun getAll(): Flow<List<Category>> = all
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = all
    override suspend fun getById(id: CategoryId): Category? = null
    override suspend fun insert(category: Category) = Unit
    override suspend fun update(category: Category) = Unit
    override suspend fun delete(id: CategoryId) = Unit
}

private class EmptySessionPointers : SessionPointers {
    override val currentUserId: Pref<UserId?> = Pref.inMemory(null)
    override val currentWorkspaceId: Pref<WorkspaceId?> = Pref.inMemory(null)
    override val currentFirebaseUid: Pref<String?> = Pref.inMemory(null)
    override val hasUsedDemo: Pref<Boolean> = Pref.inMemory(false)
}
