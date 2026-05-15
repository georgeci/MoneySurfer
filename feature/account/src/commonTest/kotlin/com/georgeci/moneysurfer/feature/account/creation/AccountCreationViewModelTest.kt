package com.georgeci.moneysurfer.feature.account.creation

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.RUB
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CurrencyRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrenciesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_created_snackbar
import com.georgeci.moneysurfer.navigation.SnackbarController
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
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

/**
 * AccountCreationViewModel save flow. We exercise the real use cases against in-memory
 * fake repositories so we cover both the VM's reducer logic and the
 * `accountRepository.insert` + opening-balance `createTransaction` chain it triggers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountCreationViewModelTest : StringSpec({

    val ws: WorkspaceId = workspaceId("ws-1")

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "save inserts an account in the selected currency without an opening-balance transaction when balance is blank" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.awaitCurrencies()

                vm.onEvent(AccountCreationEvent.OnNameChanged("Wallet"))
                vm.onEvent(AccountCreationEvent.OnCurrencyChanged(EUR))
                vm.onEvent(AccountCreationEvent.OnSaveClick)

                fixture.accountRepository.inserted.size shouldBe 1
                val saved = fixture.accountRepository.inserted.single()
                saved.name shouldBe "Wallet"
                saved.currencyCode shouldBe EUR
                saved.workspaceId shouldBe ws
                saved.balance shouldBe Money.zero()

                fixture.transactionRepository.inserted.shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "save with non-zero balance also creates an OPENING_BALANCE transaction in the selected currency" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.awaitCurrencies()

                vm.onEvent(AccountCreationEvent.OnNameChanged("Savings"))
                vm.onEvent(AccountCreationEvent.OnCurrencyChanged(RUB))
                vm.onEvent(AccountCreationEvent.OnBalanceChanged("250"))
                vm.onEvent(AccountCreationEvent.OnSaveClick)

                val savedAccount = fixture.accountRepository.inserted.single()
                savedAccount.currencyCode shouldBe RUB

                val openingTx = fixture.transactionRepository.inserted.single()
                openingTx.type shouldBe TransactionType.OPENING_BALANCE
                openingTx.money shouldBe Money.fromMajor(250)
                openingTx.currencyCode shouldBe RUB
                openingTx.accountId shouldBe savedAccount.id
                openingTx.categoryId shouldBe null
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "save with blank name is a no-op (Save button is wired to require a name)" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.awaitCurrencies()

                vm.onEvent(AccountCreationEvent.OnNameChanged("   "))
                vm.onEvent(AccountCreationEvent.OnSaveClick)

                fixture.accountRepository.inserted.shouldBeEmpty()
                fixture.transactionRepository.inserted.shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "loadCurrencies populates available list and emits NavigateBack on save" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                val ready = vm.awaitCurrencies()
                ready.currencies.map { it.code } shouldBe listOf(USD, EUR, RUB)

                vm.sideEffects.effectFlow.test {
                    vm.onEvent(AccountCreationEvent.OnNameChanged("X"))
                    vm.onEvent(AccountCreationEvent.OnSaveClick)
                    awaitItem().shouldBeInstanceOf<AccountCreationEffect.NavigateBack>()
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "save shows a created snackbar carrying the account name" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.awaitCurrencies()

                fixture.snackbar.requests.test {
                    vm.onEvent(AccountCreationEvent.OnNameChanged("Wallet"))
                    vm.onEvent(AccountCreationEvent.OnSaveClick)
                    val request = awaitItem()
                    request.message shouldBe Res.string.account_creation_created_snackbar
                    request.messageArgs shouldBe listOf("Wallet")
                }
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }
})

/** Wait until the async `loadCurrencies()` populates the currency list — the only piece of
 * VM init that's launched in `viewModelScope`. Returns the resolved [Content] state. */
private suspend fun AccountCreationViewModel.awaitCurrencies(): AccountCreationState.Content =
    first { it is AccountCreationState.Content && it.currencies.isNotEmpty() }
        as AccountCreationState.Content

private fun List<*>.shouldBeEmpty() {
    if (isNotEmpty()) error("Expected empty list, got $this")
}

private class Fixture(val workspaceId: WorkspaceId) {
    val accountRepository = FakeAccountRepository()
    val transactionRepository = FakeTransactionRepository()
    val currencyRepository = FakeCurrencyRepository(
        listOf(
            Currency(USD, "$", "US Dollar"),
            Currency(EUR, "€", "Euro"),
            Currency(RUB, "₽", "Russian Ruble"),
        ),
    )
    val session = InMemorySessionPointers(currentWorkspaceId = workspaceId)
    val clock = ClockUseCase()
    val snackbar = SnackbarController()
    val createTransaction = CreateTransactionUseCase(
        ApplyTransactionChangeUseCase(transactionRepository, accountRepository),
    )

    fun createViewModel(editing: AccountId? = null) = AccountCreationViewModel(
        accountId = editing,
        accountRepository = accountRepository,
        createTransaction = createTransaction,
        session = session,
        getCurrentTime = GetCurrentTimeUseCase(clock),
        getCurrencies = GetCurrenciesUseCase(currencyRepository),
        snackbar = snackbar,
    )
}

private class FakeAccountRepository : AccountRepository {
    val inserted = mutableListOf<Account>()
    private val byId = mutableMapOf<AccountId, Account>()
    private val byWorkspace = MutableStateFlow<List<Account>>(emptyList())

    override fun getAll(): Flow<List<Account>> = byWorkspace
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Account>> = byWorkspace
    override suspend fun getById(id: AccountId): Account? = byId[id]
    override suspend fun insert(account: Account) {
        inserted += account
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
    val inserted = mutableListOf<Transaction>()
    private val byId = mutableMapOf<TransactionId, Transaction>()
    private val all = MutableStateFlow<List<Transaction>>(emptyList())

    override fun getAll(): Flow<List<Transaction>> = all
    override fun getAllCategorized() = error("not used")
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

private class FakeCurrencyRepository(private val currencies: List<Currency>) : CurrencyRepository {
    override fun getAll(): Flow<List<Currency>> = MutableStateFlow(currencies)
}
