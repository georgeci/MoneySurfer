package com.georgeci.moneysurfer.domain.usecase

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalWorkspaceRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeSpendAnalyticsRepository
import com.georgeci.moneysurfer.domain.fixtures.FixedClock
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.BURN_RATE_DAYS
import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.BurnRatePace
import com.georgeci.moneysurfer.domain.model.DailySpendPoint
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.BudgetRepository
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

private val ws = workspaceId("ws-1")

/** 2026-04-10T09:00Z — mid-morning on the 10th of a 30-day month. */
private val nowInstant = Instant.parse("2026-04-10T09:00:00Z")
private val todayDate = LocalDate(2026, 4, 10)

private fun point(day: Int, amount: Money, month: Int = 4) =
    DailySpendPoint(date = LocalDate(2026, month, day), total = amount)

private fun useCaseOf(
    spend: FakeSpendAnalyticsRepository = FakeSpendAnalyticsRepository(),
    budgets: List<Budget> = emptyList(),
    workspaces: List<Workspace> = listOf(aWorkspace(id = ws, baseCurrency = USD)),
    session: SessionPointers = InMemorySessionPointers(currentWorkspaceId = ws),
): GetBurnRateUseCase = GetBurnRateUseCase(
    getDailySpendSeries = GetDailySpendSeriesUseCase(
        spendAnalytics = spend,
        workspaceRepository = FakeGoalWorkspaceRepository(workspaces),
        session = session,
        clock = ClockUseCase(FixedClock(nowInstant)),
    ),
    getBudgets = GetBudgetsUseCase(FakeBudgetRepository(budgets), session),
)

class GetBurnRateUseCaseTest : StringSpec({

    "nobody signed in means no series to draw" {
        runTest {
            useCaseOf(session = InMemorySessionPointers())(TimeZone.UTC).test {
                awaitItem().shouldBeNull()
            }
        }
    }

    "a workspace the device has not pulled yet has no currency to name the numbers in" {
        // The session pointer lives in preferences and is restored ahead of the workspace row.
        runTest {
            useCaseOf(workspaces = emptyList())(TimeZone.UTC).test {
                awaitItem().shouldBeNull()
            }
        }
    }

    "the query covers the chart's week and the month behind it in one window" {
        val spend = FakeSpendAnalyticsRepository()

        runTest {
            useCaseOf(spend = spend)(TimeZone.UTC).test {
                awaitItem()
                val scope = spend.dailyScopes.last()
                scope.workspaceId shouldBe ws
                scope.baseCurrency shouldBe USD
                scope.window shouldBe TransactionPeriodWindow(LocalDate(2026, 4, 1), todayDate)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "the series is the week ending today, with the days the query skipped filled in" {
        val spend = FakeSpendAnalyticsRepository(daily = listOf(point(6, 20.dollars), point(10, 15.dollars)))

        runTest {
            useCaseOf(spend = spend)(TimeZone.UTC).test {
                val burnRate = awaitItem()
                burnRate?.series?.days?.size shouldBe BURN_RATE_DAYS
                burnRate?.series?.days?.map { it.date.day } shouldContainExactly listOf(4, 5, 6, 7, 8, 9, 10)
                burnRate?.series?.today shouldBe todayDate
                burnRate?.series?.monthToDate shouldBe 35.dollars
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "with no budget the projection still emits, without a verdict on it" {
        val spend = FakeSpendAnalyticsRepository(daily = listOf(point(10, 70.dollars)))

        runTest {
            useCaseOf(spend = spend)(TimeZone.UTC).test {
                val burnRate = awaitItem()
                // 70 booked, a 10-a-day average, 20 days of April left.
                burnRate?.projectedMonthTotal shouldBe 270.dollars
                burnRate?.monthlyLimit.shouldBeNull()
                burnRate?.pace.shouldBeNull()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "a general monthly budget turns the projection into a verdict" {
        val spend = FakeSpendAnalyticsRepository(daily = listOf(point(10, 70.dollars)))
        val budget = aBudget(workspaceId = ws, amount = 500.dollars, categoryIds = emptyList())

        runTest {
            useCaseOf(spend = spend, budgets = listOf(budget))(TimeZone.UTC).test {
                val burnRate = awaitItem()
                burnRate?.monthlyLimit shouldBe 500.dollars
                burnRate?.pace shouldBe BurnRatePace.OnTrack
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "a projection past the cap reads as off pace" {
        val spend = FakeSpendAnalyticsRepository(daily = listOf(point(10, 70.dollars)))
        val budget = aBudget(workspaceId = ws, amount = 200.dollars, categoryIds = emptyList())

        runTest {
            useCaseOf(spend = spend, budgets = listOf(budget))(TimeZone.UTC).test {
                awaitItem()?.pace shouldBe BurnRatePace.OffPace
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "a category budget leaves the verdict off rather than judging every expense by it" {
        val spend = FakeSpendAnalyticsRepository(daily = listOf(point(10, 70.dollars)))
        val groceries = aBudget(workspaceId = ws, amount = 50.dollars)

        runTest {
            useCaseOf(spend = spend, budgets = listOf(groceries))(TimeZone.UTC).test {
                val burnRate = awaitItem()
                burnRate?.monthlyLimit.shouldBeNull()
                burnRate?.pace.shouldBeNull()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
})

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
