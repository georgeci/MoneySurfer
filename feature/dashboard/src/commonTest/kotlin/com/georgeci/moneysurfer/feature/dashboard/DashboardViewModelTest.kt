package com.georgeci.moneysurfer.feature.dashboard

import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutItem
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.FakeCategoryRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeExchangeRateRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalContributionRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalWorkspaceRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeHostCapabilities
import com.georgeci.moneysurfer.domain.fixtures.FakeRecurringRuleRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeSavingsGoalRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeSpendAnalyticsRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeUiPreferences
import com.georgeci.moneysurfer.domain.fixtures.FixedClock
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aRecurringRule
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
import com.georgeci.moneysurfer.domain.insight.InsightTone
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.domain.model.BurnRatePace
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.CategorySpendSlice
import com.georgeci.moneysurfer.domain.model.DailySpendPoint
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.TransactionTotal
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.BudgetId
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
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.ConvertAccountsTotalUseCase
import com.georgeci.moneysurfer.domain.usecase.GenerateInsightsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetBudgetProgressUseCase
import com.georgeci.moneysurfer.domain.usecase.GetBudgetsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetBurnRateUseCase
import com.georgeci.moneysurfer.domain.usecase.GetDailySpendSeriesUseCase
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

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
            DashboardWidgetType.BurnRate,
            DashboardWidgetType.Accounts,
            DashboardWidgetType.Insights,
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

    "the burn rate formats the week's pace and the projection it implies" {
        val ws = workspaceId("ws-1")
        // testDate is 1 January 2024: day one of a 31-day month, so 30 days are still ahead.
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            spendAnalytics = FakeSpendAnalyticsRepository(
                daily = listOf(DailySpendPoint(date = testDate, total = 70.dollars)),
            ),
        )

        val burnRate = viewModel.value.shouldBeInstanceOf<DashboardState.Content>().burnRate
        // 70 over seven days is 10 a day; 70 booked plus 30 days at 10 is 370 by month end.
        burnRate?.averageFormatted shouldBe MoneyFormatter.format(10.dollars, USD)
        burnRate?.projectedFormatted shouldBe MoneyFormatter.format(370.dollars, USD)
        burnRate?.weekTotalFormatted shouldBe MoneyFormatter.format(70.dollars, USD)
        burnRate?.days?.map { it.dayOfMonth } shouldContainExactly listOf(26, 27, 28, 29, 30, 31, 1)
        // Only the last bar is today, and it is the only day that booked anything.
        burnRate?.days?.map { it.isToday } shouldContainExactly listOf(
            false, false, false, false, false, false, true,
        )
        burnRate?.days?.map { it.fraction } shouldContainExactly listOf(0f, 0f, 0f, 0f, 0f, 0f, 1f)
        // Nothing caps the month, so the card draws the projection with no verdict on it.
        burnRate?.pace shouldBe null
    }

    "the burn rate is judged against a general monthly budget when there is one" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
            transactions = FakeTransactionRepository(emptyList()),
            budgets = listOf(
                aBudget(workspaceId = ws, amount = 300.dollars, categoryIds = emptyList(), startDate = testDate),
            ),
            spendAnalytics = FakeSpendAnalyticsRepository(
                daily = listOf(DailySpendPoint(date = testDate, total = 70.dollars)),
            ),
        )

        // A 370 projection against a 300 cap.
        viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
            .burnRate?.pace shouldBe BurnRatePace.OffPace
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

    "a rise maps to the warning sentence, with both amounts formatted in the base currency" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(emptyList()),
            transactions = FakeTransactionRepository(emptyList()),
            spendAnalytics = spendOf(current = 400.dollars, previous = 300.dollars),
            recurringRules = FakeRecurringRuleRepository(
                listOf(aRecurringRule(workspaceId = ws, categoryId = DINING, amount = 12.dollars)),
            ),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        // Warn before Neutral: the compact card shows one, and it should be the actionable one.
        content.insights.map { it.kind } shouldContainExactly listOf(
            InsightKind.CategoryUp,
            InsightKind.PeriodUp,
            InsightKind.Subscriptions,
        )

        val category = content.insights.first()
        category.tone shouldBe InsightTone.Warn
        category.label shouldBe "Dining"
        category.percent shouldBe 33
        category.amount shouldBe MoneyFormatter.format(400.dollars, USD)
        category.comparison shouldBe MoneyFormatter.format(300.dollars, USD)

        val subscriptions = content.insights.last()
        subscriptions.tone shouldBe InsightTone.Neutral
        subscriptions.count shouldBe 1
        subscriptions.amount shouldBe MoneyFormatter.format(12.dollars, USD)
    }

    "a fall maps to the saving sentence rather than the same one with a sign" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(emptyList()),
            transactions = FakeTransactionRepository(emptyList()),
            spendAnalytics = spendOf(current = 200.dollars, previous = 300.dollars),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.insights.map { it.kind } shouldContainExactly listOf(
            InsightKind.CategoryDown,
            InsightKind.PeriodDown,
        )
        content.insights.forEach { it.tone shouldBe InsightTone.Good }
    }

    "a period that barely moved is neutral, not a win" {
        val ws = workspaceId("ws-1")
        val viewModel = newViewModel(
            ws = ws,
            accounts = FakeAccountRepository(emptyList()),
            transactions = FakeTransactionRepository(emptyList()),
            spendAnalytics = spendOf(current = 305.dollars, previous = 300.dollars),
        )

        val content = viewModel.value.shouldBeInstanceOf<DashboardState.Content>()
        content.insights.map { it.kind } shouldContainExactly listOf(InsightKind.PeriodFlat)
        content.insights.single().tone shouldBe InsightTone.Neutral
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

/** Mid-month, so the comparison rules are past their minimum-sample guard. */
private val INSIGHTS_TODAY = LocalDate(2026, 7, 15)

private val DINING = categoryId("cat-dining")

/** One category's spend in both windows the engine reads for [INSIGHTS_TODAY]. */
private fun spendOf(current: Money, previous: Money) = FakeSpendAnalyticsRepository(
    mapOf(
        TransactionPeriodWindow(LocalDate(2026, 7, 1), INSIGHTS_TODAY) to
            listOf(CategorySpendSlice(DINING, current, 4)),
        TransactionPeriodWindow(LocalDate(2026, 6, 1), LocalDate(2026, 6, 15)) to
            listOf(CategorySpendSlice(DINING, previous, 3)),
    ),
)

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
    clock: ClockUseCase = ClockUseCase(FixedClock(testInstant)),
    spendAnalytics: FakeSpendAnalyticsRepository = FakeSpendAnalyticsRepository(),
    recurringRules: FakeRecurringRuleRepository = FakeRecurringRuleRepository(),
): DashboardViewModel {
    val session = InMemorySessionPointers(currentWorkspaceId = ws)
    val workspaces = FakeGoalWorkspaceRepository(listOf(aWorkspace(id = ws, baseCurrency = baseCurrency)))
    return DashboardViewModel(
        getAccounts = GetAccountsUseCase(accounts, session),
        getRecentTransactions = GetRecentTransactionsUseCase(transactions, session),
        getGoals = GetGoalsUseCase(FakeSavingsGoalRepository(), FakeGoalContributionRepository(), session),
        getExchangeRates = GetExchangeRatesUseCase(session, workspaces, rates),
        getSafeToSpend = GetSafeToSpendUseCase(
            getBudgets = GetBudgetsUseCase(FakeBudgetRepository(budgets), session),
            getBudgetProgress = GetBudgetProgressUseCase(transactions, workspaces, clock),
        ),
        getBurnRate = GetBurnRateUseCase(
            getDailySpendSeries = GetDailySpendSeriesUseCase(spendAnalytics, workspaces, session, clock),
            getBudgets = GetBudgetsUseCase(FakeBudgetRepository(budgets), session),
        ),
        generateInsights = GenerateInsightsUseCase(
            spendAnalytics = spendAnalytics,
            categoryRepository = FakeCategoryRepository(
                listOf(aCategory(id = DINING, workspaceId = ws, name = "Dining")),
            ),
            recurringRuleRepository = recurringRules,
            workspaceRepository = workspaces,
            session = session,
            // Its own clock rather than the shared one: that is pinned to testInstant, the 1st of
            // a month, where the engine's minimum-sample guard correctly silences every
            // comparison rule — and these specs are about what those rules produce.
            clock = ClockUseCase(FixedClock(INSIGHTS_TODAY.atStartOfDayIn(TimeZone.UTC))),
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
    override suspend fun restore(id: TransactionId): Transaction? = null
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
