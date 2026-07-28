package com.georgeci.moneysurfer.feature.dashboard

import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutItem
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.FakeExchangeRateRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalContributionRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalWorkspaceRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeHostCapabilities
import com.georgeci.moneysurfer.domain.fixtures.FakeSavingsGoalRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeUiPreferences
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.anExchangeRateTable
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.SplitId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.ConvertAccountsTotalUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetExchangeRatesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetGoalsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetRecentTransactionsUseCase
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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

    "the rendered layout comes from the persisted config, not a fixed list" {
        val ws = workspaceId("ws-1")
        val stored = DashboardLayoutConfig(
            items = listOf(
                DashboardLayoutItem(DashboardWidgetType.Goals),
                DashboardLayoutItem(DashboardWidgetType.Balance, enabled = false),
            ),
        )
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            uiPreferences = FakeUiPreferences(dashboardLayout = stored),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.layout.enabledItems.map { it.type } shouldContainExactly listOf(
            DashboardWidgetType.Goals,
            // widgets the stored layout never heard of are appended rather than dropped
            DashboardWidgetType.QuickActions,
            DashboardWidgetType.Accounts,
            DashboardWidgetType.RecentTransactions,
        )
    }

    "the quick-actions Transfer button asks for the transfer form, not the plain creation one" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
        )

        viewModel.onEvent(DashboardEvent.OnTransferClick)

        viewModel.sideEffects.effectFlow.first() shouldBe DashboardEffect.NavigateToTransferCreation
    }

    "a build with transfers switched off says so, so the quick-actions row can stand down" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            hostCapabilities = FakeHostCapabilities(isOffline = true, transferEnabled = false),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.transferEnabled shouldBe false
    }

    "an unset layout falls back to the default order" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.layout shouldBe DashboardLayoutConfig.DEFAULT
    }

    "the headline total folds every currency into the workspace base currency" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(
                listOf(
                    anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 100.dollars),
                    // 0.5 EUR per USD → 50 EUR is another 100 USD
                    anAccount(id = accountId("a-2"), workspaceId = ws, currencyCode = EUR, balance = 50.dollars),
                ),
            ),
            transactions = FakeTransactionRepository(emptyList()),
            rates = FakeExchangeRateRepository(mapOf(USD to anExchangeRateTable())),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.formattedTotalBalance shouldBe MoneyFormatter.format(200.dollars, USD)
        content.otherCurrencyTotals.shouldBeEmpty()
        content.ratesAsOf shouldBe "2024-01-01"
    }

    "a currency no cached rate covers is shown beside the headline, never dropped" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(
                listOf(
                    anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 100.dollars),
                    anAccount(id = accountId("a-2"), workspaceId = ws, currencyCode = EUR, balance = 50.dollars),
                ),
            ),
            transactions = FakeTransactionRepository(emptyList()),
            // Nothing cached yet — offline first run, or the offline build.
            rates = FakeExchangeRateRepository(),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.formattedTotalBalance shouldBe MoneyFormatter.format(100.dollars, USD)
        content.otherCurrencyTotals shouldContainExactly listOf(MoneyFormatter.format(50.dollars, EUR))
        content.ratesAsOf shouldBe null
    }

    "with nothing priceable in the base currency the headline is still a balance, not the empty state" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(
                listOf(
                    anAccount(id = accountId("a-1"), workspaceId = ws, currencyCode = USD, balance = 100.dollars),
                ),
            ),
            transactions = FakeTransactionRepository(emptyList()),
            baseCurrency = EUR,
            rates = FakeExchangeRateRepository(),
        )

        // A null headline is the screen's "no accounts at all" signal — an unconvertible
        // balance must not trip it.
        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.formattedTotalBalance shouldBe MoneyFormatter.format(100.dollars, USD)
        content.otherCurrencyTotals.shouldBeEmpty()
    }
})

private fun newViewModel(
    ws: WorkspaceId,
    accounts: FakeAccountRepository,
    transactions: FakeTransactionRepository,
    uiPreferences: UiPreferences = FakeUiPreferences(),
    baseCurrency: CurrencyCode = USD,
    rates: FakeExchangeRateRepository = FakeExchangeRateRepository(),
    hostCapabilities: FakeHostCapabilities = FakeHostCapabilities(isOffline = false),
): DashboardViewModel {
    val session = InMemorySessionPointers(currentWorkspaceId = ws)
    val workspaces = FakeGoalWorkspaceRepository(listOf(aWorkspace(id = ws, baseCurrency = baseCurrency)))
    return DashboardViewModel(
        getAccounts = GetAccountsUseCase(accounts, session),
        getRecentTransactions = GetRecentTransactionsUseCase(transactions, session),
        getGoals = GetGoalsUseCase(FakeSavingsGoalRepository(), FakeGoalContributionRepository(), session),
        getExchangeRates = GetExchangeRatesUseCase(session, workspaces, rates),
        convertAccountsTotal = ConvertAccountsTotalUseCase(),
        uiPreferences = uiPreferences,
        hostCapabilities = hostCapabilities,
    )
}

private class FakeTransactionRepository(
    initial: List<Transaction>,
) : TransactionRepository {
    private val state = MutableStateFlow(initial)

    override fun getAll(): Flow<List<Transaction>> = state
    override fun getByAccountId(accountId: AccountId): Flow<List<Transaction>> = state
    override fun getCategorizedWindow(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
        limit: Int,
    ): Flow<List<CategorizedTransaction>> = flowOf(emptyList())
    override fun getTotals(
        accountId: AccountId?,
        window: TransactionPeriodWindow,
    ): Flow<List<TransactionTotal>> = flowOf(emptyList())
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Transaction>> = state
    override suspend fun getById(id: TransactionId): Transaction? =
        state.value.firstOrNull { it.id == id }
    override suspend fun getByTransferId(transferId: TransferId): List<Transaction> =
        state.value.filter { it.transferId == transferId }
    override suspend fun getBySplitId(splitId: SplitId): List<Transaction> =
        state.value.filter { it.splitId == splitId }
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
    override suspend fun reorder(orderedIds: List<AccountId>) = Unit
    override suspend fun setArchived(accountId: AccountId, archived: Boolean) = Unit
}
