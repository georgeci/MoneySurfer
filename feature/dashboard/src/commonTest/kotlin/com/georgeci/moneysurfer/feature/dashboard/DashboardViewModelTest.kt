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
import com.georgeci.moneysurfer.domain.fixtures.FixedClock
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.anExchangeRateTable
import com.georgeci.moneysurfer.domain.fixtures.budgetId
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.testDate
import com.georgeci.moneysurfer.domain.fixtures.testInstant
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.CategorySpendSlice
import com.georgeci.moneysurfer.domain.model.CurrencyTotal
import com.georgeci.moneysurfer.domain.model.DailySpendPoint
import com.georgeci.moneysurfer.domain.model.MerchantSpend
import com.georgeci.moneysurfer.domain.model.MonthlyNet
import com.georgeci.moneysurfer.domain.model.SpendScope
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.SplitId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import com.georgeci.moneysurfer.domain.repositories.CategoryRepository
import com.georgeci.moneysurfer.domain.repositories.SpendAnalyticsRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.ConvertAccountsTotalUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetBudgetProgressUseCase
import com.georgeci.moneysurfer.domain.usecase.GetBudgetsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategorySpendUseCase
import com.georgeci.moneysurfer.domain.usecase.GetExchangeRatesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetGoalsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetRecentTransactionsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetSafeToSpendUseCase
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
            DashboardWidgetType.SafeToSpend,
            DashboardWidgetType.SpentByCategory,
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

    "safe-to-spend stays null with no budget, so the widget can offer to set one" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
        )

        viewModel.value.shouldBeInstanceOf<DashboardState.Content>().safeToSpend shouldBe null
    }

    "safe-to-spend reads the active budget's remainder, pace and days left" {
        val ws = workspaceId("ws-1")
        val account = anAccount(id = accountId("a-1"), workspaceId = ws)
        val spend = aTransaction(
            id = transactionId("tx-1"),
            workspaceId = ws,
            accountId = account.id,
            type = TransactionType.EXPENSE,
            money = 100.dollars,
            currencyCode = USD,
            operationDate = testDate,
        )
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(account)),
            transactions = FakeTransactionRepository(listOf(spend)),
            // A January budget of 310 against 100 spent on its first day: 210 left over 31 days.
            budgets = listOf(
                aBudget(workspaceId = ws, amount = 310.dollars, categoryIds = emptyList(), startDate = testDate),
            ),
        )

        val safeToSpend = viewModel.value.shouldBeInstanceOf<DashboardState.Content>().safeToSpend
        safeToSpend?.remainingFormatted shouldBe MoneyFormatter.format(210.dollars, USD)
        safeToSpend?.perDayFormatted shouldBe MoneyFormatter.format(Money(677), USD)
        safeToSpend?.daysLeft shouldBe 31
        // Day one of the window: none of it is behind us yet, so the pace tick sits at the start.
        safeToSpend?.paceFraction shouldBe 0f
        safeToSpend?.status shouldBe BudgetStatus.OK
        safeToSpend?.isOver shouldBe false
    }

    "safe-to-spend ignores archived budgets" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            budgets = listOf(
                aBudget(id = budgetId("b-archived"), workspaceId = ws, isActive = false, startDate = testDate),
            ),
        )

        viewModel.value.shouldBeInstanceOf<DashboardState.Content>().safeToSpend shouldBe null
    }

    "spent-by-category stays empty with no spend, so the widget can say the month is bare" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
        )

        viewModel.value.shouldBeInstanceOf<DashboardState.Content>().spentByCategory.shouldBeEmpty()
    }

    "spent-by-category formats the aggregate's rows and orders them largest first" {
        val ws = workspaceId("ws-1")
        val rent = categoryId("c-rent")
        val food = categoryId("c-food")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            categories = listOf(
                aCategory(id = rent, workspaceId = ws, name = "Rent"),
                aCategory(id = food, workspaceId = ws, name = "Food"),
            ),
            spendByCategory = listOf(
                CategorySpendSlice(categoryId = food, total = 40.dollars, transactionCount = 2),
                CategorySpendSlice(categoryId = rent, total = 60.dollars, transactionCount = 1),
            ),
        )

        val rows = viewModel.value.shouldBeInstanceOf<DashboardState.Content>().spentByCategory
        rows.map { it.name } shouldContainExactly listOf("Rent", "Food")
        rows.first().spentFormatted shouldBe MoneyFormatter.format(60.dollars, USD)
        rows.first().share shouldBe 0.6f
        rows.first().sharePercent shouldBe 60
        // Nothing caps either category, so the rows carry no over/near state to draw.
        rows.all { it.cap == null } shouldBe true
    }

    "a single-category budget reaches the row it caps as a formatted limit and a status" {
        val ws = workspaceId("ws-1")
        val food = categoryId("c-food")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            budgets = listOf(
                aBudget(workspaceId = ws, categoryIds = listOf(food), amount = 100.dollars, startDate = testDate),
            ),
            categories = listOf(aCategory(id = food, workspaceId = ws, name = "Food")),
            spendByCategory = listOf(
                CategorySpendSlice(categoryId = food, total = 120.dollars, transactionCount = 3),
            ),
        )

        val cap = viewModel.value.shouldBeInstanceOf<DashboardState.Content>().spentByCategory.single().cap
        cap?.limitFormatted shouldBe MoneyFormatter.format(100.dollars, USD)
        cap?.status shouldBe BudgetStatus.OVER
        cap?.progress shouldBe 1.2f
    }

    "an uncategorized slice keeps its money and leaves the label to the screen" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            spendByCategory = listOf(
                CategorySpendSlice(categoryId = null, total = 40.dollars, transactionCount = 1),
            ),
        )

        val row = viewModel.value.shouldBeInstanceOf<DashboardState.Content>().spentByCategory.single()
        row.name shouldBe null
        row.categoryId shouldBe null
        row.hue shouldBe null
        row.spentFormatted shouldBe MoneyFormatter.format(40.dollars, USD)
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

@Suppress("LongParameterList")
private fun newViewModel(
    ws: WorkspaceId,
    accounts: FakeAccountRepository,
    transactions: FakeTransactionRepository,
    uiPreferences: UiPreferences = FakeUiPreferences(),
    baseCurrency: CurrencyCode = USD,
    rates: FakeExchangeRateRepository = FakeExchangeRateRepository(),
    hostCapabilities: FakeHostCapabilities = FakeHostCapabilities(isOffline = false),
    budgets: List<Budget> = emptyList(),
    categories: List<Category> = emptyList(),
    spendByCategory: List<CategorySpendSlice> = emptyList(),
    clock: ClockUseCase = ClockUseCase(FixedClock(testInstant)),
): DashboardViewModel {
    val session = InMemorySessionPointers(currentWorkspaceId = ws)
    val workspaces = FakeGoalWorkspaceRepository(listOf(aWorkspace(id = ws, baseCurrency = baseCurrency)))
    val budgetRepository = FakeBudgetRepository(budgets)
    return DashboardViewModel(
        getAccounts = GetAccountsUseCase(accounts, session),
        getRecentTransactions = GetRecentTransactionsUseCase(transactions, session),
        getGoals = GetGoalsUseCase(FakeSavingsGoalRepository(), FakeGoalContributionRepository(), session),
        getExchangeRates = GetExchangeRatesUseCase(session, workspaces, rates),
        getSafeToSpend = GetSafeToSpendUseCase(
            getBudgets = GetBudgetsUseCase(budgetRepository, session),
            getBudgetProgress = GetBudgetProgressUseCase(transactions, workspaces, clock),
        ),
        getCategorySpend = GetCategorySpendUseCase(
            spendAnalytics = FakeSpendAnalyticsRepository(spendByCategory),
            categoryRepository = FakeCategoryRepository(categories),
            budgetRepository = budgetRepository,
            workspaceRepository = workspaces,
            session = session,
            clock = clock,
        ),
        convertAccountsTotal = ConvertAccountsTotalUseCase(),
        uiPreferences = uiPreferences,
        hostCapabilities = hostCapabilities,
    )
}

private class FakeBudgetRepository(initial: List<Budget>) : BudgetRepository {
    private val state = MutableStateFlow(initial)

    override fun getAll(): Flow<List<Budget>> = state
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Budget>> = state
    override suspend fun getById(id: BudgetId): Budget? = state.value.firstOrNull { it.id == id }
    override suspend fun insert(budget: Budget) = Unit
    override suspend fun update(budget: Budget) = Unit
    override suspend fun setActive(id: BudgetId, isActive: Boolean) = Unit
    override suspend fun delete(id: BudgetId) = Unit
}

private class FakeCategoryRepository(initial: List<Category>) : CategoryRepository {
    private val state = MutableStateFlow(initial)

    override fun getAll(): Flow<List<Category>> = state
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Category>> = state
    override suspend fun getById(id: CategoryId): Category? = state.value.firstOrNull { it.id == id }
    override suspend fun insert(category: Category) = Unit
    override suspend fun update(category: Category) = Unit
    override suspend fun delete(id: CategoryId) = Unit
}

/** Only [byCategory] is wired: the dashboard reads no other rollup. */
private class FakeSpendAnalyticsRepository(
    private val slices: List<CategorySpendSlice>,
) : SpendAnalyticsRepository {
    override fun byCategory(scope: SpendScope): Flow<List<CategorySpendSlice>> = flowOf(slices)
    override fun netByMonth(scope: SpendScope): Flow<List<MonthlyNet>> = flowOf(emptyList())
    override fun daily(scope: SpendScope): Flow<List<DailySpendPoint>> = flowOf(emptyList())
    override fun topMerchants(scope: SpendScope, limit: Int): Flow<List<MerchantSpend>> = flowOf(emptyList())
    override fun excludedByCurrency(scope: SpendScope): Flow<List<CurrencyTotal>> = flowOf(emptyList())
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
