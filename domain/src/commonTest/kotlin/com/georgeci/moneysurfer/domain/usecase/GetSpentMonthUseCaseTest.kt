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
import com.georgeci.moneysurfer.domain.insight.SpendTrend
import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.MonthlyNet
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
import kotlinx.datetime.YearMonth
import kotlin.time.Instant

private val ws = workspaceId("ws-1")

/** 2026-04-20T09:00Z — the 20th, well past the elapsed-day guard on the delta. */
private val nowInstant = Instant.parse("2026-04-20T09:00:00Z")

private val thisMonth = TransactionPeriodWindow(LocalDate(2026, 4, 1), LocalDate(2026, 4, 20))
private val lastMonth = TransactionPeriodWindow(LocalDate(2026, 3, 1), LocalDate(2026, 3, 20))

private fun net(month: Int, expense: Money, income: Money = Money.zero()) =
    MonthlyNet(month = YearMonth(2026, month), income = income, expense = expense)

private fun spendOf(
    current: Money? = null,
    previous: Money? = null,
) = FakeSpendAnalyticsRepository(
    monthlyNetsByWindow = buildMap {
        current?.let { put(thisMonth, listOf(net(4, it))) }
        previous?.let { put(lastMonth, listOf(net(3, it))) }
    },
)

private fun useCaseOf(
    spend: FakeSpendAnalyticsRepository = FakeSpendAnalyticsRepository(),
    budgets: List<Budget> = emptyList(),
    workspaces: List<Workspace> = listOf(aWorkspace(id = ws, baseCurrency = USD)),
    session: SessionPointers = InMemorySessionPointers(currentWorkspaceId = ws),
    now: Instant = nowInstant,
): GetSpentMonthUseCase = GetSpentMonthUseCase(
    spendAnalytics = spend,
    workspaceRepository = FakeGoalWorkspaceRepository(workspaces),
    getBudgets = GetBudgetsUseCase(SpentMonthBudgetRepository(budgets), session),
    session = session,
    clock = ClockUseCase(FixedClock(now)),
)

class GetSpentMonthUseCaseTest : StringSpec({

    "nobody signed in means no figure to print" {
        runTest {
            useCaseOf(session = InMemorySessionPointers())(TimeZone.UTC).test {
                awaitItem().shouldBeNull()
            }
        }
    }

    "a workspace the device has not pulled yet has no currency to name the amount in" {
        // The session pointer lives in preferences and is restored ahead of the workspace row.
        runTest {
            useCaseOf(workspaces = emptyList())(TimeZone.UTC).test {
                awaitItem().shouldBeNull()
            }
        }
    }

    "the two queries are the month to date and the same stretch of the month before" {
        val spend = spendOf(current = 400.dollars, previous = 500.dollars)

        runTest {
            useCaseOf(spend = spend)(TimeZone.UTC).test {
                awaitItem()
                spend.netByMonthScopes.map { it.window } shouldContainExactly listOf(thisMonth, lastMonth)
                spend.netByMonthScopes.forEach {
                    it.workspaceId shouldBe ws
                    it.baseCurrency shouldBe USD
                }
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "the amount is the month's expense, and income in the same month is not netted off it" {
        val spend = FakeSpendAnalyticsRepository(
            monthlyNetsByWindow = mapOf(thisMonth to listOf(net(4, expense = 400.dollars, income = 900.dollars))),
        )

        runTest {
            useCaseOf(spend = spend)(TimeZone.UTC).test {
                awaitItem()?.spent shouldBe 400.dollars
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "a month with nothing booked is a real zero, not a missing figure" {
        runTest {
            useCaseOf(spend = spendOf())(TimeZone.UTC).test {
                val month = awaitItem()
                month?.spent shouldBe Money.zero()
                month?.currency shouldBe USD
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "with no budget the amount still emits, with no cap to measure it against" {
        runTest {
            useCaseOf(spend = spendOf(current = 400.dollars))(TimeZone.UTC).test {
                val month = awaitItem()
                month?.cap.shouldBeNull()
                month?.capFraction.shouldBeNull()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "a general monthly budget becomes the cap the bar is drawn against" {
        val budget = aBudget(workspaceId = ws, amount = 800.dollars, categoryIds = emptyList())

        runTest {
            useCaseOf(spend = spendOf(current = 400.dollars), budgets = listOf(budget))(TimeZone.UTC).test {
                val month = awaitItem()
                month?.cap shouldBe 800.dollars
                month?.capFraction shouldBe 0.5f
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "a category budget caps a slice of the spend, so it is not this card's cap" {
        val groceries = aBudget(workspaceId = ws, amount = 100.dollars)

        runTest {
            useCaseOf(spend = spendOf(current = 400.dollars), budgets = listOf(groceries))(TimeZone.UTC).test {
                awaitItem()?.cap.shouldBeNull()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "spending more than last month reads as up, by a magnitude" {
        runTest {
            useCaseOf(spend = spendOf(current = 600.dollars, previous = 500.dollars))(TimeZone.UTC).test {
                val delta = awaitItem()?.delta
                delta?.trend shouldBe SpendTrend.Up
                delta?.changePercent shouldBe 20
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "spending less than last month reads as down, and the percent stays positive" {
        runTest {
            useCaseOf(spend = spendOf(current = 400.dollars, previous = 500.dollars))(TimeZone.UTC).test {
                val delta = awaitItem()?.delta
                delta?.trend shouldBe SpendTrend.Down
                delta?.changePercent shouldBe 20
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "a month that barely moved is flat rather than a percentage nobody would act on" {
        runTest {
            useCaseOf(spend = spendOf(current = 520.dollars, previous = 500.dollars))(TimeZone.UTC).test {
                awaitItem()?.delta?.trend shouldBe SpendTrend.Flat
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "a month with no spend behind it is no baseline, so no delta is claimed" {
        runTest {
            useCaseOf(spend = spendOf(current = 400.dollars))(TimeZone.UTC).test {
                val month = awaitItem()
                month?.previousSpent shouldBe Money.zero()
                month?.delta.shouldBeNull()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "early in the month the comparison is too short to be honest, so it is left off" {
        // The 3rd: three elapsed days, where one bill's timing swings the answer outright.
        val early = TransactionPeriodWindow(LocalDate(2026, 4, 1), LocalDate(2026, 4, 3))
        val earlyLast = TransactionPeriodWindow(LocalDate(2026, 3, 1), LocalDate(2026, 3, 3))
        val spend = FakeSpendAnalyticsRepository(
            monthlyNetsByWindow = mapOf(
                early to listOf(net(4, 40.dollars)),
                earlyLast to listOf(net(3, 500.dollars)),
            ),
        )

        runTest {
            useCaseOf(spend = spend, now = Instant.parse("2026-04-03T09:00:00Z"))(TimeZone.UTC).test {
                val month = awaitItem()
                month?.spent shouldBe 40.dollars
                month?.delta.shouldBeNull()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "the baseline window is clamped to a short previous month rather than overflowing it" {
        // The 31st of March against the whole of February, which has no 31st to stop at.
        val spend = FakeSpendAnalyticsRepository()

        runTest {
            useCaseOf(spend = spend, now = Instant.parse("2026-03-31T09:00:00Z"))(TimeZone.UTC).test {
                awaitItem()
                spend.netByMonthScopes.map { it.window } shouldContainExactly listOf(
                    TransactionPeriodWindow(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31)),
                    TransactionPeriodWindow(LocalDate(2026, 2, 1), LocalDate(2026, 2, 28)),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "January reads its baseline out of the previous year" {
        val spend = FakeSpendAnalyticsRepository()

        runTest {
            useCaseOf(spend = spend, now = Instant.parse("2026-01-15T09:00:00Z"))(TimeZone.UTC).test {
                awaitItem()
                spend.netByMonthScopes.last().window shouldBe
                    TransactionPeriodWindow(LocalDate(2025, 12, 1), LocalDate(2025, 12, 15))
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
})

/**
 * Distinctly named rather than reusing `GetBurnRateUseCaseTest`'s copy: private top-level classes
 * still collide across files in one package, and hoisting a shared budget fake into
 * `domain-test-fixtures` is a cleanup of its own.
 */
private class SpentMonthBudgetRepository(initial: List<Budget>) : BudgetRepository {
    private val state = MutableStateFlow(initial)

    override fun getAll(): Flow<List<Budget>> = state
    override fun getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Budget>> = state
    override suspend fun getById(id: BudgetId): Budget? = state.value.firstOrNull { it.id == id }
    override suspend fun insert(budget: Budget) = Unit
    override suspend fun update(budget: Budget) = Unit
    override suspend fun setActive(id: BudgetId, isActive: Boolean) = Unit
    override suspend fun delete(id: BudgetId) = Unit
}
