package com.georgeci.moneysurfer.feature.insights

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.dashboard.DashboardPeriod
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.FakeCategoryRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalWorkspaceRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeSpendAnalyticsRepository
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.CategorySpendSlice
import com.georgeci.moneysurfer.domain.model.CurrencyTotal
import com.georgeci.moneysurfer.domain.model.MerchantSpend
import com.georgeci.moneysurfer.domain.model.MonthlyNet
import com.georgeci.moneysurfer.domain.model.SpendInsights
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import com.georgeci.moneysurfer.domain.usecase.GetSpendInsightsUseCase
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.domain.util.periodWindow
import com.georgeci.moneysurfer.domain.util.shiftPeriod
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth

private val workspace = workspaceId("ws-1")
private val DINING = categoryId("c-dining")

/**
 * Today in the default zone — the same date the view model's clock resolves to.
 *
 * Derived rather than pinned: the view model reads the system zone, so a hard-coded date would make
 * every window assertion below depend on where the test runs.
 */
private fun today(): LocalDate =
    ClockUseCase().now().toLocalDateTime(TimeZone.currentSystemDefault()).date

/** The whole-month span the trend is read over, ending in the month [anchor] falls in. */
private fun monthsWindow(anchor: LocalDate): TransactionPeriodWindow = TransactionPeriodWindow(
    from = periodWindow(
        TransactionPeriodMode.Month,
        LocalDate(anchor.year, anchor.month, 1).minus(SpendInsights.MONTH_COLUMNS - 1, DateTimeUnit.MONTH),
    ).from,
    to = periodWindow(TransactionPeriodMode.Month, anchor).to,
)

@OptIn(ExperimentalCoroutinesApi::class)
class InsightsViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "an empty workspace lands on empty Content, not on a stuck spinner" {
        val content = viewModel().value.shouldBeInstanceOf<InsightsState.Content>()

        content.mode shouldBe DashboardPeriod.DEFAULT
        content.isEmpty shouldBe true
        content.hiddenByBaseCurrency shouldBe false
        content.inFlight shouldBe false
    }

    "the breakdown, the trend and the merchants all answer for the opened period" {
        val analytics = FakeSpendAnalyticsRepository(
            slicesByWindow = mapOf(
                periodWindow(TransactionPeriodMode.Month, today()) to listOf(
                    CategorySpendSlice(DINING, 300.dollars, 4),
                    CategorySpendSlice(null, 100.dollars, 1),
                ),
            ),
            merchants = listOf(MerchantSpend("Albert Heijn", 120.dollars, 7)),
        )

        val content = viewModel(analytics).value.shouldBeInstanceOf<InsightsState.Content>()

        content.categories.map { it.name } shouldBe listOf("Dining", null)
        content.categories.map { it.sharePercent } shouldBe listOf(75, 25)
        content.merchants.single().merchant shouldBe "Albert Heijn"
        content.merchants.single().transactionCount shouldBe 7
        analytics.topMerchantsScopes.first().second shouldBe SpendInsights.TOP_MERCHANTS
    }

    "a month the workspace booked nothing in still draws a column" {
        val analytics = FakeSpendAnalyticsRepository(
            nets = listOf(MonthlyNet(today().yearMonth, 900.dollars, 400.dollars)),
        )

        val content = viewModel(analytics).value.shouldBeInstanceOf<InsightsState.Content>()

        content.months.size shouldBe SpendInsights.MONTH_COLUMNS
        content.months.last().income shouldBe 900.dollars.minor
        content.months.first().income shouldBe 0L
        content.months.first().expense shouldBe 0L
    }

    "the base-currency filter is named as the reason when it hides everything" {
        val analytics = FakeSpendAnalyticsRepository(
            excluded = listOf(CurrencyTotal(USD, 500.dollars)),
        )

        val content = viewModel(analytics, baseCurrency = EUR)
            .value.shouldBeInstanceOf<InsightsState.Content>()

        content.hiddenByBaseCurrency shouldBe true
        content.hiddenCurrencies shouldBe listOf("USD")
        content.baseCurrency shouldBe "EUR"
    }

    "spend in the base currency is not blamed on the filter, even beside excluded rows" {
        val analytics = FakeSpendAnalyticsRepository(
            slicesByWindow = mapOf(
                periodWindow(TransactionPeriodMode.Month, today()) to
                    listOf(CategorySpendSlice(DINING, 300.dollars, 4)),
            ),
            excluded = listOf(CurrencyTotal(USD, 500.dollars)),
        )

        val content = viewModel(analytics, baseCurrency = EUR)
            .value.shouldBeInstanceOf<InsightsState.Content>()

        content.hiddenByBaseCurrency shouldBe false
        content.hiddenCurrencies shouldBe listOf("USD")
    }

    "the forward arrow is inert on the period containing today" {
        val content = viewModel().value.shouldBeInstanceOf<InsightsState.Content>()

        content.canGoToNextPeriod shouldBe false
    }

    "paging back re-reads the previous period and lets the user page forward again" {
        val analytics = FakeSpendAnalyticsRepository()
        val vm = viewModel(analytics)

        vm.onEvent(InsightsEvent.OnPreviousPeriodClick)

        val expected = periodWindow(
            TransactionPeriodMode.Month,
            shiftPeriod(TransactionPeriodMode.Month, today(), -1),
        )
        analytics.byCategoryScopes.last().window shouldBe expected
        val content = vm.value.shouldBeInstanceOf<InsightsState.Content>()
        content.canGoToNextPeriod shouldBe true
        // The rollups answered inside the same turn, so the flag is already back down.
        content.inFlight shouldBe false
    }

    "the forward arrow's bound is the view model's, not just the arrow's" {
        val analytics = FakeSpendAnalyticsRepository()
        val vm = viewModel(analytics)
        val queriesBefore = analytics.byCategoryScopes.size

        // The composable draws this arrow inert, so this event should not arrive at all — but a
        // period that has not happened is a dead end (its own forward arrow is inert too), so the
        // rule belongs to the state rather than to the control.
        vm.onEvent(InsightsEvent.OnNextPeriodClick)

        analytics.byCategoryScopes.size shouldBe queriesBefore
        vm.value.shouldBeInstanceOf<InsightsState.Content>().canGoToNextPeriod shouldBe false
    }

    "paging forward is allowed once there is a period to page into" {
        val analytics = FakeSpendAnalyticsRepository()
        val vm = viewModel(analytics)

        vm.onEvent(InsightsEvent.OnPreviousPeriodClick)
        vm.onEvent(InsightsEvent.OnNextPeriodClick)

        analytics.byCategoryScopes.last().window shouldBe
            periodWindow(TransactionPeriodMode.Month, today())
    }

    "switching cadence narrows the window to the ISO week without moving the anchor" {
        val analytics = FakeSpendAnalyticsRepository()
        val vm = viewModel(analytics)

        vm.onEvent(InsightsEvent.OnPeriodModeChanged(DashboardPeriod.Week))

        analytics.byCategoryScopes.last().window shouldBe
            periodWindow(TransactionPeriodMode.Week, today())
        val content = vm.value.shouldBeInstanceOf<InsightsState.Content>()
        content.mode shouldBe DashboardPeriod.Week
        content.period.shouldBeInstanceOf<InsightsPeriodUi.Week>()
    }

    "the trend spans whole months whichever cadence is selected" {
        val analytics = FakeSpendAnalyticsRepository()
        val vm = viewModel(analytics)

        analytics.netByMonthScopes.last().window shouldBe monthsWindow(today())

        vm.onEvent(InsightsEvent.OnPeriodModeChanged(DashboardPeriod.Week))

        // A week-wide window would come back as one part-month column under a full month's name.
        analytics.netByMonthScopes.last().window shouldBe monthsWindow(today())
    }

    "re-selecting the period the screen is already on changes nothing" {
        val analytics = FakeSpendAnalyticsRepository()
        val vm = viewModel(analytics)
        val queriesBefore = analytics.byCategoryScopes.size

        vm.onEvent(InsightsEvent.OnPeriodModeChanged(DashboardPeriod.Month))

        analytics.byCategoryScopes.size shouldBe queriesBefore
    }

    "OnBackClick asks the host to pop" {
        val vm = viewModel()

        vm.sideEffects.effectFlow.test {
            vm.onEvent(InsightsEvent.OnBackClick)
            awaitItem() shouldBe InsightsEffect.NavigateBack
        }
    }
})

private fun viewModel(
    analytics: FakeSpendAnalyticsRepository = FakeSpendAnalyticsRepository(),
    baseCurrency: CurrencyCode = USD,
): InsightsViewModel {
    val session = InMemorySessionPointers(currentWorkspaceId = workspace)
    return InsightsViewModel(
        getSpendInsights = GetSpendInsightsUseCase(
            spendAnalytics = analytics,
            categoryRepository = FakeCategoryRepository(
                listOf(aCategory(id = DINING, workspaceId = workspace, name = "Dining")),
            ),
            budgetRepository = NoBudgetRepository,
            workspaceRepository = FakeGoalWorkspaceRepository(
                listOf(aWorkspace(id = workspace, baseCurrency = baseCurrency)),
            ),
            session = session,
        ),
        clock = ClockUseCase(),
    )
}

/** No budgets: this screen draws no cap overlay, so the builder's cap arm has nothing to resolve. */
private object NoBudgetRepository : BudgetRepository {
    override fun getAll(): Flow<List<Budget>> = flowOf(emptyList())
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Budget>> = flowOf(emptyList())
    override suspend fun getById(id: BudgetId): Budget? = null
    override suspend fun insert(budget: Budget) = Unit
    override suspend fun update(budget: Budget) = Unit
    override suspend fun setActive(id: BudgetId, isActive: Boolean) = Unit
    override suspend fun delete(id: BudgetId) = Unit
}
