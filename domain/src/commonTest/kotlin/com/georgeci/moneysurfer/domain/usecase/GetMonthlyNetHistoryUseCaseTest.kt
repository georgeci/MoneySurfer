package com.georgeci.moneysurfer.domain.usecase

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalWorkspaceRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeSpendAnalyticsRepository
import com.georgeci.moneysurfer.domain.fixtures.FixedClock
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.BalanceTrend
import com.georgeci.moneysurfer.domain.model.MonthlyNet
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlin.time.Instant

private val ws = workspaceId("ws-1")

/** 2026-04-10T09:00Z — mid-month, so the window's ends have to be snapped to month boundaries. */
private val nowInstant = Instant.parse("2026-04-10T09:00:00Z")

private fun net(year: Int, month: Int, income: Money = Money.zero(), expense: Money = Money.zero()) =
    MonthlyNet(month = YearMonth(year, month), income = income, expense = expense)

private fun useCaseOf(
    spend: FakeSpendAnalyticsRepository = FakeSpendAnalyticsRepository(),
    workspaces: List<Workspace> = listOf(aWorkspace(id = ws, baseCurrency = USD)),
    session: SessionPointers = InMemorySessionPointers(currentWorkspaceId = ws),
): GetMonthlyNetHistoryUseCase = GetMonthlyNetHistoryUseCase(
    spendAnalytics = spend,
    workspaceRepository = FakeGoalWorkspaceRepository(workspaces),
    session = session,
    clock = ClockUseCase(FixedClock(nowInstant)),
)

class GetMonthlyNetHistoryUseCaseTest : StringSpec({

    "nobody signed in means no history to fold" {
        runTest {
            useCaseOf(session = InMemorySessionPointers())(timeZone = TimeZone.UTC).test {
                awaitItem().shouldBeNull()
            }
        }
    }

    "a workspace the device has not pulled yet has no currency the query could filter on" {
        runTest {
            useCaseOf(workspaces = emptyList())(timeZone = TimeZone.UTC).test {
                awaitItem().shouldBeNull()
            }
        }
    }

    "the window runs whole months, ending on the last day of the current one" {
        val spend = FakeSpendAnalyticsRepository()

        runTest {
            useCaseOf(spend = spend)(timeZone = TimeZone.UTC).test {
                awaitItem()
                val scope = spend.netByMonthScopes.last()
                scope.workspaceId shouldBe ws
                scope.baseCurrency shouldBe USD
                // Six months back from April, and April's own 30th — not the 10th, or the aggregate
                // would report a part-month under a full month's name.
                scope.window shouldBe TransactionPeriodWindow(
                    LocalDate(2025, 11, 1),
                    LocalDate(2026, 4, 30),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "the months come back oldest first, ending at the current one" {
        runTest {
            useCaseOf()(timeZone = TimeZone.UTC).test {
                val history = awaitItem()
                history?.months?.size shouldBe BalanceTrend.TREND_MONTHS
                history?.months?.first() shouldBe YearMonth(2025, 11)
                history?.months?.last() shouldBe YearMonth(2026, 4)
                history?.currency shouldBe USD
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "a shorter window asks for fewer months" {
        val spend = FakeSpendAnalyticsRepository()

        runTest {
            useCaseOf(spend = spend)(months = 2, timeZone = TimeZone.UTC).test {
                awaitItem()?.months shouldContainExactly listOf(YearMonth(2026, 3), YearMonth(2026, 4))
                spend.netByMonthScopes.last().window shouldBe TransactionPeriodWindow(
                    LocalDate(2026, 3, 1),
                    LocalDate(2026, 4, 30),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "only the rows the window covers are handed back" {
        val spend = FakeSpendAnalyticsRepository(
            nets = listOf(
                net(2025, 9, income = 500.dollars),
                net(2026, 4, income = 300.dollars, expense = 100.dollars),
            ),
        )

        runTest {
            useCaseOf(spend = spend)(timeZone = TimeZone.UTC).test {
                val history = awaitItem()
                history?.nets?.map { it.month } shouldContainExactly listOf(YearMonth(2026, 4))
                history?.nets?.single()?.net shouldBe 200.dollars
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    "a window of no months is rejected rather than queried between missing ends" {
        shouldThrow<IllegalArgumentException> { useCaseOf()(months = 0, timeZone = TimeZone.UTC) }
    }

    "a window that booked nothing is an empty history, not a missing one" {
        // Null means "nothing to read it from"; a signed-in workspace with a quiet half-year still
        // has months, and it is the caller that decides a curve of zeroes is not worth drawing.
        runTest {
            useCaseOf()(timeZone = TimeZone.UTC).test {
                val history = awaitItem()
                history?.months?.size shouldBe BalanceTrend.TREND_MONTHS
                history?.nets?.shouldBeEmpty()
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
})
