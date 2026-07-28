package com.georgeci.moneysurfer.feature.account.creation

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.FakeHostCapabilities
import com.georgeci.moneysurfer.domain.fixtures.RUB
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.AccountExtraDetail
import com.georgeci.moneysurfer.domain.model.AccountExtraDetailKey
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CurrencyRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateAccountUseCase
import com.georgeci.moneysurfer.domain.usecase.CreateTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrenciesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.domain.usecase.UpdateAccountUseCase
import com.georgeci.moneysurfer.domain.usecase.UpdateWorkspaceCurrencyUseCase
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_created_snackbar
import com.georgeci.moneysurfer.navigation.SnackbarController
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
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

    "the name required error appears only after the field was touched and left blank" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                val pristine = vm.awaitCurrencies()
                pristine.nameMissing shouldBe false

                vm.onEvent(AccountCreationEvent.OnNameChanged("Wallet"))
                (vm.value as AccountCreationState.Content).nameMissing shouldBe false

                vm.onEvent(AccountCreationEvent.OnNameChanged(""))
                val cleared = vm.value as AccountCreationState.Content
                cleared.nameMissing shouldBe true
                cleared.canSave shouldBe false

                vm.onEvent(AccountCreationEvent.OnSaveClick)
                fixture.accountRepository.inserted.shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "online build keeps the extra details section visible" {
        runTest {
            val vm = Fixture(workspaceId = ws).createViewModel(offline = false)
            try {
                vm.awaitCurrencies().extraDetailsEnabled shouldBe true
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "offline build hides the extra details section" {
        runTest {
            val vm = Fixture(workspaceId = ws).createViewModel(offline = true)
            try {
                vm.awaitCurrencies().extraDetailsEnabled shouldBe false
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "save persists the filled extra details and drops the ones left empty" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.awaitCurrencies()

                vm.onEvent(AccountCreationEvent.OnNameChanged("Everyday"))
                vm.onEvent(AccountCreationEvent.OnAddExtraField(AccountExtraDetailKey.IBAN))
                vm.onEvent(AccountCreationEvent.OnAddExtraField(AccountExtraDetailKey.BIC))
                vm.onEvent(
                    AccountCreationEvent.OnExtraFieldValueChanged(
                        AccountExtraDetailKey.IBAN.name,
                        " PL61 1090 1014 ",
                    ),
                )
                vm.onEvent(AccountCreationEvent.OnSaveClick)

                fixture.accountRepository.inserted.single().extraDetails shouldBe listOf(
                    AccountExtraDetail(key = "IBAN", value = "PL61 1090 1014"),
                )
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a custom field is stored under the name the user typed" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.awaitCurrencies()

                vm.onEvent(AccountCreationEvent.OnNameChanged("Brokerage"))
                vm.onEvent(AccountCreationEvent.OnAddCustomExtraField("  Broker   code "))
                vm.onEvent(AccountCreationEvent.OnExtraFieldValueChanged("Broker code", "MS-4417"))
                vm.onEvent(AccountCreationEvent.OnSaveClick)

                fixture.accountRepository.inserted.single().extraDetails shouldBe listOf(
                    AccountExtraDetail(key = "Broker code", value = "MS-4417"),
                )
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a custom field that duplicates a field already on the form is ignored" {
        runTest {
            val vm = Fixture(workspaceId = ws).createViewModel()
            try {
                vm.awaitCurrencies()

                vm.onEvent(AccountCreationEvent.OnAddExtraField(AccountExtraDetailKey.IBAN))
                vm.onEvent(AccountCreationEvent.OnAddCustomExtraField("iban"))
                vm.onEvent(AccountCreationEvent.OnAddCustomExtraField("   "))

                (vm.value as AccountCreationState.Content).extraFields.map { it.key } shouldBe
                    listOf(AccountExtraDetailKey.IBAN.name)
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a custom field shadowing a well-known key withdraws that key's chip" {
        runTest {
            val vm = Fixture(workspaceId = ws).createViewModel()
            try {
                vm.awaitCurrencies()

                // The chip and the add-guard have to agree: leaving IBAN on offer here would
                // render a control that silently does nothing, since addExtraField rejects it.
                vm.onEvent(AccountCreationEvent.OnAddCustomExtraField("iban"))

                (vm.value as AccountCreationState.Content)
                    .availableExtraFieldKeys shouldNotContain AccountExtraDetailKey.IBAN
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "removing a row drops it and offers its chip again" {
        runTest {
            val vm = Fixture(workspaceId = ws).createViewModel()
            try {
                vm.awaitCurrencies()

                vm.onEvent(AccountCreationEvent.OnAddExtraField(AccountExtraDetailKey.BANK_URL))
                (vm.value as AccountCreationState.Content)
                    .availableExtraFieldKeys shouldNotContain AccountExtraDetailKey.BANK_URL

                vm.onEvent(AccountCreationEvent.OnRemoveExtraField(AccountExtraDetailKey.BANK_URL.name))

                val state = vm.value as AccountCreationState.Content
                state.extraFields.shouldBeEmpty()
                state.availableExtraFieldKeys shouldContain AccountExtraDetailKey.BANK_URL
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "editing an account loads its extra details and saves them back" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val existing = Account(
                id = AccountId("acc-1"),
                workspaceId = ws,
                name = "Everyday",
                type = AccountType.BANK,
                currencyCode = EUR,
                balance = Money.zero(),
                extraDetails = listOf(AccountExtraDetail(key = "IBAN", value = "PL61")),
            )
            fixture.accountRepository.insert(existing)
            val vm = fixture.createViewModel(editing = existing.id)
            try {
                val loaded = vm.first {
                    it is AccountCreationState.Content && it.extraFields.isNotEmpty()
                } as AccountCreationState.Content
                loaded.extraFields shouldBe listOf(AccountExtraField(key = "IBAN", value = "PL61"))

                vm.onEvent(AccountCreationEvent.OnExtraFieldValueChanged("IBAN", "PL99"))
                vm.onEvent(AccountCreationEvent.OnSaveClick)

                fixture.accountRepository.getById(existing.id)?.extraDetails shouldBe
                    listOf(AccountExtraDetail(key = "IBAN", value = "PL99"))
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "the offline build still carries an edited account's extra details through a save" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val existing = Account(
                id = AccountId("acc-2"),
                workspaceId = ws,
                name = "Everyday",
                type = AccountType.BANK,
                currencyCode = EUR,
                balance = Money.zero(),
                extraDetails = listOf(AccountExtraDetail(key = "IBAN", value = "PL61")),
            )
            fixture.accountRepository.insert(existing)
            val vm = fixture.createViewModel(editing = existing.id, offline = true)
            try {
                val loaded = vm.first {
                    it is AccountCreationState.Content && it.extraFields.isNotEmpty()
                } as AccountCreationState.Content
                // Hidden, not discarded — the section is invisible offline, the data is not gone.
                loaded.extraDetailsEnabled shouldBe false

                vm.onEvent(AccountCreationEvent.OnSaveClick)

                fixture.accountRepository.getById(existing.id)?.extraDetails shouldBe
                    existing.extraDetails
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "balance input strips letters and collapses extra decimal separators" {
        runTest {
            val vm = Fixture(workspaceId = ws).createViewModel()
            try {
                vm.awaitCurrencies()

                vm.onEvent(AccountCreationEvent.OnBalanceChanged("1a2,3.4"))

                (vm.value as AccountCreationState.Content).balance shouldBe "12.34"
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "save is blocked with an inline error when a non-credit account has a negative balance" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.awaitCurrencies()

                vm.onEvent(AccountCreationEvent.OnNameChanged("Wallet"))
                vm.onEvent(AccountCreationEvent.OnTypeChanged(AccountType.CASH))
                vm.onEvent(AccountCreationEvent.OnBalanceChanged("-50"))

                val state = vm.value as AccountCreationState.Content
                state.balanceError shouldBe InitialBalanceError.NEGATIVE_NOT_ALLOWED
                state.canSave shouldBe false

                vm.onEvent(AccountCreationEvent.OnSaveClick)
                fixture.accountRepository.inserted.shouldBeEmpty()
                fixture.transactionRepository.inserted.shouldBeEmpty()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "save keeps a negative opening balance for a credit (card) account" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.awaitCurrencies()

                vm.onEvent(AccountCreationEvent.OnNameChanged("Credit card"))
                vm.onEvent(AccountCreationEvent.OnTypeChanged(AccountType.CARD))
                vm.onEvent(AccountCreationEvent.OnBalanceChanged("-50"))

                (vm.value as AccountCreationState.Content).balanceError shouldBe null

                vm.onEvent(AccountCreationEvent.OnSaveClick)

                val openingTx = fixture.transactionRepository.inserted.single()
                openingTx.type shouldBe TransactionType.OPENING_BALANCE
                openingTx.money shouldBe Money.fromDouble(-50.0)
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

    "first-run save adopts the picked currency as the workspace base currency and lands on Dashboard" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel(firstRun = true)
            try {
                vm.awaitCurrencies()

                vm.sideEffects.effectFlow.test {
                    vm.onEvent(AccountCreationEvent.OnNameChanged("Cash"))
                    vm.onEvent(AccountCreationEvent.OnCurrencyChanged(RUB))
                    vm.onEvent(AccountCreationEvent.OnSaveClick)
                    awaitItem().shouldBeInstanceOf<AccountCreationEffect.NavigateToDashboard>()
                    cancelAndIgnoreRemainingEvents()
                }

                fixture.accountRepository.inserted.single().currencyCode shouldBe RUB
                fixture.workspaceRepository.getById(ws)?.baseCurrency shouldBe RUB
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a regular save leaves the workspace base currency untouched" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel()
            try {
                vm.awaitCurrencies()

                vm.onEvent(AccountCreationEvent.OnNameChanged("Cash"))
                vm.onEvent(AccountCreationEvent.OnCurrencyChanged(RUB))
                vm.onEvent(AccountCreationEvent.OnSaveClick)

                fixture.workspaceRepository.getById(ws)?.baseCurrency shouldBe USD
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "the onboarding's pre-selected type is what the screen opens on" {
        runTest {
            val fixture = Fixture(workspaceId = ws)
            val vm = fixture.createViewModel(firstRun = true, initialType = AccountType.CASH)
            try {
                vm.awaitCurrencies().type shouldBe AccountType.CASH

                vm.onEvent(AccountCreationEvent.OnNameChanged("Wallet"))
                vm.onEvent(AccountCreationEvent.OnSaveClick)

                fixture.accountRepository.inserted.single().type shouldBe AccountType.CASH
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
    val workspaceRepository = FakeWorkspaceRepository(aWorkspace(id = workspaceId, baseCurrency = USD))
    val session = InMemorySessionPointers(currentWorkspaceId = workspaceId)
    val clock = ClockUseCase()
    val snackbar = SnackbarController()
    val createTransaction = CreateTransactionUseCase(
        ApplyTransactionChangeUseCase(transactionRepository, accountRepository),
    )

    fun createViewModel(
        editing: AccountId? = null,
        offline: Boolean = false,
        firstRun: Boolean = false,
        initialType: AccountType = AccountType.SAVINGS,
    ) = AccountCreationViewModel(
        accountId = editing,
        firstRun = firstRun,
        initialType = initialType,
        getAccountById = GetAccountByIdUseCase(accountRepository),
        createAccount = CreateAccountUseCase(accountRepository),
        updateAccount = UpdateAccountUseCase(accountRepository),
        createTransaction = createTransaction,
        session = session,
        getCurrentTime = GetCurrentTimeUseCase(clock),
        getCurrencies = GetCurrenciesUseCase(currencyRepository),
        updateWorkspaceCurrency = UpdateWorkspaceCurrencyUseCase(workspaceRepository, accountRepository),
        snackbar = snackbar,
        hostCapabilities = FakeHostCapabilities(isOffline = offline),
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
    override suspend fun reorder(orderedIds: List<AccountId>) = Unit
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

private class FakeWorkspaceRepository(workspace: Workspace) : WorkspaceRepository {
    private val byId = mutableMapOf(workspace.id to workspace)

    override fun getAll(): Flow<List<Workspace>> = MutableStateFlow(byId.values.toList())
    override fun getByUserId(userId: UserId): Flow<List<Workspace>> = MutableStateFlow(byId.values.toList())
    override suspend fun getById(id: WorkspaceId): Workspace? = byId[id]
    override suspend fun insert(workspace: Workspace) {
        byId[workspace.id] = workspace
    }
    override suspend fun update(workspace: Workspace) {
        byId[workspace.id] = workspace
    }
    override suspend fun delete(id: WorkspaceId) {
        byId.remove(id)
    }
}

private class FakeCurrencyRepository(private val currencies: List<Currency>) : CurrencyRepository {
    override fun getAll(): Flow<List<Currency>> = MutableStateFlow(currencies)
}
