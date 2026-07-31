package com.georgeci.moneysurfer.feature.dashboard

import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.dashboard.DashboardPeriod
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
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.budgetId
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.testDate
import com.georgeci.moneysurfer.domain.fixtures.testInstant
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.CategorySpendSlice
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
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AccountRepository
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import com.georgeci.moneysurfer.domain.usecase.ConvertAccountsTotalUseCase
import com.georgeci.moneysurfer.domain.usecase.GenerateInsightsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetActiveBudgetProgressUseCase
import com.georgeci.moneysurfer.domain.usecase.GetBudgetProgressUseCase
import com.georgeci.moneysurfer.domain.usecase.GetBudgetsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetBurnRateUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategorySpendUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentWorkspaceUseCase
import com.georgeci.moneysurfer.domain.usecase.GetDailySpendSeriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetExchangeRatesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetGoalsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetMonthlyNetHistoryUseCase
import com.georgeci.moneysurfer.domain.usecase.GetRecentTransactionsUseCase
import com.georgeci.moneysurfer.domain.usecase.GetSafeToSpendUseCase
import com.georgeci.moneysurfer.domain.usecase.GetSpentMonthUseCase
import com.georgeci.moneysurfer.domain.usecase.GetUpcomingRecurringUseCase
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/**
 * The collaborators every dashboard spec builds a view model out of.
 *
 * Shared rather than file-private because the dashboard's specs are split per widget — the one
 * file they used to share grew past detekt's size limit and conflicted on every dashboard PR.
 * Follows the `WorkspaceTestFakes` precedent.
 */

/** A general budget — no category filter, so every expense in the workspace counts against it. */
internal fun budgetOf(ws: WorkspaceId, id: String, name: String, amount: Money) = aBudget(
    id = budgetId(id),
    workspaceId = ws,
    name = name,
    amount = amount,
    categoryIds = emptyList(),
    startDate = testDate,
)

/** Mid-month, so the comparison rules are past their minimum-sample guard. */
internal val INSIGHTS_TODAY = LocalDate(2026, 7, 15)

internal val DINING = categoryId("cat-dining")

/**
 * Spend answered for exactly the window the dashboard asks for under the shared [testInstant]
 * clock. Derived from [DashboardPeriod.DEFAULT] rather than spelled out, so the fake follows the
 * same period the screen feeds the use case instead of drifting into answering nothing.
 */
internal fun spendInTestMonth(vararg slices: CategorySpendSlice) = FakeSpendAnalyticsRepository(
    mapOf(DashboardPeriod.DEFAULT.window(testDate) to slices.toList()),
)

/** One category's spend in both windows the engine reads for [INSIGHTS_TODAY]. */
internal fun spendOf(current: Money, previous: Money) = FakeSpendAnalyticsRepository(
    mapOf(
        TransactionPeriodWindow(LocalDate(2026, 7, 1), INSIGHTS_TODAY) to
            listOf(CategorySpendSlice(DINING, current, 4)),
        TransactionPeriodWindow(LocalDate(2026, 6, 1), LocalDate(2026, 6, 15)) to
            listOf(CategorySpendSlice(DINING, previous, 3)),
    ),
)

@Suppress("LongParameterList")
internal fun newViewModel(
    ws: WorkspaceId,
    accounts: FakeAccountRepository,
    transactions: FakeTransactionRepository,
    uiPreferences: UiPreferences = FakeUiPreferences(),
    baseCurrency: CurrencyCode = USD,
    workspaceName: String = "Personal",
    /**
     * The workspace rows the device holds. Seeded with the one the session points at; pass an empty
     * repository to model a device that has not pulled the row yet, which is the state the toolbar
     * falls back to the app's own name for.
     *
     * Passing one **supersedes** [baseCurrency] and [workspaceName] — they are only read to build
     * this default. Seed the row yourself if you need either, or a spec asking for `EUR` gets a
     * workspace in `aWorkspace`'s default USD and fails inside the formatter rather than here.
     */
    workspaces: FakeGoalWorkspaceRepository = FakeGoalWorkspaceRepository(
        listOf(aWorkspace(id = ws, baseCurrency = baseCurrency, name = workspaceName)),
    ),
    rates: FakeExchangeRateRepository = FakeExchangeRateRepository(),
    goals: FakeSavingsGoalRepository = FakeSavingsGoalRepository(),
    hostCapabilities: FakeHostCapabilities = FakeHostCapabilities(isOffline = false),
    budgets: List<Budget> = emptyList(),
    categories: List<Category> = emptyList(),
    clock: ClockUseCase = ClockUseCase(FixedClock(testInstant)),
    spendAnalytics: FakeSpendAnalyticsRepository = FakeSpendAnalyticsRepository(),
    recurringRules: FakeRecurringRuleRepository = FakeRecurringRuleRepository(),
): DashboardViewModel {
    val session = InMemorySessionPointers(currentWorkspaceId = ws)
    // One instance for both readers: the headline is a projection of the rows' own progress list.
    val activeBudgetProgress = GetActiveBudgetProgressUseCase(
        getBudgets = GetBudgetsUseCase(FakeBudgetRepository(budgets), session),
        getBudgetProgress = GetBudgetProgressUseCase(transactions, workspaces, clock),
    )
    return DashboardViewModel(
        getAccounts = GetAccountsUseCase(accounts, session),
        getRecentTransactions = GetRecentTransactionsUseCase(transactions, session),
        getGoals = GetGoalsUseCase(goals, FakeGoalContributionRepository(), session),
        getExchangeRates = GetExchangeRatesUseCase(session, workspaces, rates),
        getSafeToSpend = GetSafeToSpendUseCase(activeBudgetProgress),
        getActiveBudgetProgress = activeBudgetProgress,
        getCategorySpend = GetCategorySpendUseCase(
            spendAnalytics = spendAnalytics,
            categoryRepository = FakeCategoryRepository(categories),
            budgetRepository = FakeBudgetRepository(budgets),
            workspaceRepository = workspaces,
            session = session,
            clock = clock,
        ),
        getBurnRate = GetBurnRateUseCase(
            getDailySpendSeries = GetDailySpendSeriesUseCase(spendAnalytics, workspaces, session, clock),
            getBudgets = GetBudgetsUseCase(FakeBudgetRepository(budgets), session),
        ),
        getSpentMonth = GetSpentMonthUseCase(
            spendAnalytics = spendAnalytics,
            workspaceRepository = workspaces,
            getBudgets = GetBudgetsUseCase(FakeBudgetRepository(budgets), session),
            session = session,
            clock = clock,
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
        getUpcomingRecurring = GetUpcomingRecurringUseCase(
            recurringRuleRepository = recurringRules,
            workspaceRepository = workspaces,
            session = session,
            clock = clock,
        ),
        getMonthlyNetHistory = GetMonthlyNetHistoryUseCase(spendAnalytics, workspaces, session, clock),
        clock = clock,
        getCurrentWorkspace = GetCurrentWorkspaceUseCase(workspaces, session),
        uiPreferences = uiPreferences,
        hostCapabilities = hostCapabilities,
    )
}

internal class FakeBudgetRepository(initial: List<Budget>) : BudgetRepository {
    private val state = MutableStateFlow(initial)

    override fun getAll(): Flow<List<Budget>> = state
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Budget>> = state
    override suspend fun getById(id: BudgetId): Budget? = state.value.firstOrNull { it.id == id }
    override suspend fun insert(budget: Budget) = Unit
    override suspend fun update(budget: Budget) = Unit
    override suspend fun setActive(id: BudgetId, isActive: Boolean) = Unit
    override suspend fun delete(id: BudgetId) = Unit
}

internal class FakeTransactionRepository(
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

internal class FakeAccountRepository(
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
