package com.georgeci.moneysurfer.feature.workspace.firstrun

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.CurrencyRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.usecase.GetCurrenciesUseCase
import com.georgeci.moneysurfer.domain.usecase.UpdateWorkspaceCurrencyUseCase
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
 * FirstRunCurrencyViewModel exercised against in-memory fakes wired to the real
 * `GetCurrenciesUseCase` + `UpdateWorkspaceCurrencyUseCase`, so the test covers the VM
 * reducer plus the workspace/account currency propagation it triggers.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirstRunCurrencyViewModelTest : StringSpec({

    val ws: WorkspaceId = workspaceId("ws-1")

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "loads currencies ordered with preferred codes first" {
        runTest {
            val fixture = Fixture(ws)
            val vm = fixture.createViewModel()
            try {
                val content = vm.awaitContent()
                content.currencies.map { it.code.value } shouldBe
                    listOf("USD", "EUR", "GBP", "PLN")
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "preselects the workspace base currency" {
        runTest {
            val fixture = Fixture(ws, workspaceCurrency = CurrencyCode("GBP"))
            val vm = fixture.createViewModel()
            try {
                vm.awaitContent().selectedCode shouldBe "GBP"
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "OnQueryChanged narrows the visible currencies" {
        runTest {
            val fixture = Fixture(ws)
            val vm = fixture.createViewModel()
            try {
                vm.awaitContent()
                vm.onEvent(FirstRunCurrencyEvent.OnQueryChanged("eur"))
                val content = vm.value as FirstRunCurrencyState.Content
                content.visibleCurrencies.map { it.code.value } shouldBe listOf("EUR")
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "OnConfirmClick persists the currency, flips currencyChosen, and navigates to Dashboard" {
        runTest {
            val fixture = Fixture(ws, workspaceCurrency = USD)
            fixture.accountRepository.seed(
                anAccount(id = AccountId("a-cash"), workspaceId = ws, currencyCode = USD),
            )
            val vm = fixture.createViewModel()
            try {
                vm.awaitContent()
                vm.onEvent(FirstRunCurrencyEvent.OnCurrencySelected("GBP"))

                vm.sideEffects.effectFlow.test {
                    vm.onEvent(FirstRunCurrencyEvent.OnConfirmClick)
                    awaitItem().shouldBeInstanceOf<FirstRunCurrencyEffect.NavigateToDashboard>()
                    cancelAndIgnoreRemainingEvents()
                }

                fixture.workspaceRepository.getById(ws)!!.baseCurrency shouldBe CurrencyCode("GBP")
                fixture.accountRepository.getById(AccountId("a-cash"))!!.currencyCode shouldBe
                    CurrencyCode("GBP")
                fixture.session.currencyChosen.flow.first() shouldBe true
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "OnConfirmClick surfaces an error and stays on screen when the update fails" {
        runTest {
            // No workspace row -> UpdateWorkspaceCurrencyUseCase raises WorkspaceNotFound.
            val fixture = Fixture(ws, seedWorkspace = false)
            val vm = fixture.createViewModel()
            try {
                vm.awaitContent()
                vm.onEvent(FirstRunCurrencyEvent.OnCurrencySelected("GBP"))
                vm.onEvent(FirstRunCurrencyEvent.OnConfirmClick)

                val content = vm.value as FirstRunCurrencyState.Content
                content.error shouldBe true
                content.inFlight shouldBe false
                fixture.session.currencyChosen.flow.first() shouldBe false
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }
})

private suspend fun FirstRunCurrencyViewModel.awaitContent(): FirstRunCurrencyState.Content =
    first { it is FirstRunCurrencyState.Content } as FirstRunCurrencyState.Content

private class Fixture(
    workspaceId: WorkspaceId,
    workspaceCurrency: CurrencyCode = USD,
    seedWorkspace: Boolean = true,
) {
    val workspaceRepository = FakeWorkspaceRepository()
    val accountRepository = FakeAccountRepository()
    val currencyRepository = FakeCurrencyRepository(
        listOf(
            Currency(CurrencyCode("PLN"), "zł", "Polish Zloty"),
            Currency(CurrencyCode("EUR"), "€", "Euro"),
            Currency(CurrencyCode("GBP"), "£", "British Pound"),
            Currency(USD, "$", "US Dollar"),
        ),
    )
    val session = InMemorySessionPointers(currentWorkspaceId = workspaceId, currencyChosen = false)

    init {
        if (seedWorkspace) {
            workspaceRepository.seed(aWorkspace(id = workspaceId, baseCurrency = workspaceCurrency))
        }
    }

    fun createViewModel() = FirstRunCurrencyViewModel(
        getCurrencies = GetCurrenciesUseCase(currencyRepository),
        updateWorkspaceCurrency = UpdateWorkspaceCurrencyUseCase(workspaceRepository, accountRepository),
        workspaceRepository = workspaceRepository,
        session = session,
    )
}

private class FakeWorkspaceRepository : WorkspaceRepository {
    private val byId = mutableMapOf<WorkspaceId, Workspace>()
    private val all = MutableStateFlow<List<Workspace>>(emptyList())

    fun seed(vararg workspaces: Workspace) {
        workspaces.forEach { byId[it.id] = it }
        all.value = byId.values.toList()
    }

    override fun getAll(): Flow<List<Workspace>> = all
    override fun getByUserId(userId: UserId): Flow<List<Workspace>> = all
    override suspend fun getById(id: WorkspaceId): Workspace? = byId[id]
    override suspend fun insert(workspace: Workspace) {
        byId[workspace.id] = workspace
        all.value = byId.values.toList()
    }
    override suspend fun update(workspace: Workspace) {
        byId[workspace.id] = workspace
        all.value = byId.values.toList()
    }
    override suspend fun delete(id: WorkspaceId) {
        byId.remove(id)
        all.value = byId.values.toList()
    }
}

private class FakeAccountRepository : AccountRepository {
    private val byId = mutableMapOf<AccountId, Account>()
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
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) {
        val current = byId[accountId] ?: return
        byId[accountId] = current.copy(archived = archived)
        byWorkspace.value = byId.values.toList()
    }
}

private class FakeCurrencyRepository(private val currencies: List<Currency>) : CurrencyRepository {
    override fun getAll(): Flow<List<Currency>> = MutableStateFlow(currencies)
}
