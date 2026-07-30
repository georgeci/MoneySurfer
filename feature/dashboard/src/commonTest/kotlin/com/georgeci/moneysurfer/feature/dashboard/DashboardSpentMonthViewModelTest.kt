package com.georgeci.moneysurfer.feature.dashboard

import com.georgeci.moneysurfer.domain.fixtures.FakeSpendAnalyticsRepository
import com.georgeci.moneysurfer.domain.fixtures.FixedClock
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.insight.SpendTrend
import com.georgeci.moneysurfer.domain.model.Budget
import com.georgeci.moneysurfer.domain.model.MonthlyNet
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn

private val ws = workspaceId("ws-1")

/**
 * The two windows the card reads under [INSIGHTS_TODAY] — the 15th of July, mid-month, so the
 * elapsed-day guard on the delta is clear and the comparison is a fortnight against a fortnight.
 */
private val thisMonth = TransactionPeriodWindow(LocalDate(2026, 7, 1), INSIGHTS_TODAY)
private val lastMonth = TransactionPeriodWindow(LocalDate(2026, 6, 1), LocalDate(2026, 6, 15))

private fun spentMonthOf(current: Money? = null, previous: Money? = null) = FakeSpendAnalyticsRepository(
    monthlyNetsByWindow = buildMap {
        current?.let { put(thisMonth, listOf(MonthlyNet(YearMonth(2026, 7), Money.zero(), it))) }
        previous?.let { put(lastMonth, listOf(MonthlyNet(YearMonth(2026, 6), Money.zero(), it))) }
    },
)

private fun viewModelOf(
    spendAnalytics: FakeSpendAnalyticsRepository,
    budgets: List<Budget> = emptyList(),
    today: LocalDate = INSIGHTS_TODAY,
) = newViewModel(
    ws = ws,
    accounts = FakeAccountRepository(listOf(anAccount(id = accountId("a-1"), workspaceId = ws))),
    transactions = FakeTransactionRepository(emptyList()),
    budgets = budgets,
    // Mid-month rather than the shared testInstant, which is the 1st — where the delta is
    // correctly suppressed and these specs are about what it says once it is not.
    clock = ClockUseCase(FixedClock(today.atStartOfDayIn(TimeZone.UTC))),
    spendAnalytics = spendAnalytics,
)

private fun DashboardViewModel.spentMonth() =
    value.shouldBeInstanceOf<DashboardState.Content>().spentMonth

/**
 * What the month has cost, the budget it is measured against, and how it compares to the last one.
 *
 * Split out of `DashboardViewModelTest` for the same reason the burn-rate spec is: one file for the
 * whole dashboard grew past detekt's size limit. Shared fakes live in `DashboardTestFakes.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardSpentMonthViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "the month's spend is formatted in the workspace base currency" {
        val spentMonth = viewModelOf(spentMonthOf(current = 400.dollars)).spentMonth()

        spentMonth?.spentFormatted shouldBe MoneyFormatter.format(400.dollars, USD)
    }

    "with no budget the card says so and leaves the bar empty" {
        val spentMonth = viewModelOf(spentMonthOf(current = 400.dollars)).spentMonth()

        spentMonth?.capFormatted.shouldBeNull()
        spentMonth?.progress shouldBe 0f
    }

    "a general monthly budget becomes the caption and fills the bar" {
        val budget = budgetOf(ws, id = "b-1", name = "Everything", amount = 800.dollars)

        val spentMonth = viewModelOf(spentMonthOf(current = 600.dollars), budgets = listOf(budget)).spentMonth()

        spentMonth?.capFormatted shouldBe MoneyFormatter.format(800.dollars, USD)
        spentMonth?.progress shouldBe 0.75f
    }

    "an overspent month reports past the limit rather than flooring at the cap" {
        val budget = budgetOf(ws, id = "b-1", name = "Everything", amount = 400.dollars)

        val spentMonth = viewModelOf(spentMonthOf(current = 600.dollars), budgets = listOf(budget)).spentMonth()

        // The bar clamps this; the number itself stays honest so nothing downstream has to guess.
        spentMonth?.progress shouldBe 1.5f
    }

    "spending more than the same stretch of last month reads as up" {
        val spentMonth = viewModelOf(spentMonthOf(current = 600.dollars, previous = 500.dollars)).spentMonth()

        spentMonth?.delta?.trend shouldBe SpendTrend.Up
        spentMonth?.delta?.percent shouldBe 20
    }

    "spending less reads as down, with the direction carrying the sign" {
        val spentMonth = viewModelOf(spentMonthOf(current = 400.dollars, previous = 500.dollars)).spentMonth()

        spentMonth?.delta?.trend shouldBe SpendTrend.Down
        spentMonth?.delta?.percent shouldBe 20
    }

    "a month with no spend behind it gets no delta rather than an infinite one" {
        val spentMonth = viewModelOf(spentMonthOf(current = 400.dollars)).spentMonth()

        spentMonth?.delta.shouldBeNull()
    }

    "on the 1st the comparison is a single day, so the card shows the amount without a delta" {
        val firstOfMonth = LocalDate(2026, 7, 1)
        val spend = FakeSpendAnalyticsRepository(
            monthlyNetsByWindow = mapOf(
                TransactionPeriodWindow(firstOfMonth, firstOfMonth) to
                    listOf(MonthlyNet(YearMonth(2026, 7), Money.zero(), 40.dollars)),
                TransactionPeriodWindow(LocalDate(2026, 6, 1), LocalDate(2026, 6, 1)) to
                    listOf(MonthlyNet(YearMonth(2026, 6), Money.zero(), 500.dollars)),
            ),
        )

        val spentMonth = viewModelOf(spend, today = firstOfMonth).spentMonth()

        spentMonth?.spentFormatted shouldBe MoneyFormatter.format(40.dollars, USD)
        spentMonth?.delta.shouldBeNull()
    }

    "a month that booked nothing is a formatted zero, not an absent card" {
        val spentMonth = viewModelOf(spentMonthOf()).spentMonth()

        spentMonth?.spentFormatted shouldBe MoneyFormatter.format(Money.zero(), USD)
    }
})
