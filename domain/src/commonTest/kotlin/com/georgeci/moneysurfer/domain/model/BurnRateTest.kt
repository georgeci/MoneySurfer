package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.budgetId
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

/** 10 April 2026: a 30-day month, ten days in, so "days ahead" and the window both stay round. */
private val today = LocalDate(2026, 4, 10)

private fun point(day: Int, amount: Money, month: Int = 4) =
    DailySpendPoint(date = LocalDate(2026, month, day), total = amount)

class BurnRateTest : StringSpec({

    "the window reaches back to whichever is earlier, the chart's first day or the 1st" {
        // Ten days in, the 1st is the earlier bound — the month-to-date needs it.
        dailySpendSeriesWindow(today) shouldBe TransactionPeriodWindow(LocalDate(2026, 4, 1), today)

        // Three days in, the chart is what reaches further back, into the previous month.
        dailySpendSeriesWindow(LocalDate(2026, 4, 3)) shouldBe
            TransactionPeriodWindow(LocalDate(2026, 3, 28), LocalDate(2026, 4, 3))
    }

    "the series is always seven days ending today, with the quiet ones filled in" {
        // The repository leaves out days nothing was booked on; the chart cannot.
        val series = dailySpendSeries(
            points = listOf(point(6, 20.dollars), point(10, 5.dollars)),
            today = today,
            currency = USD,
        )

        series.days.map { it.date.day } shouldContainExactly listOf(4, 5, 6, 7, 8, 9, 10)
        series.days.map { it.total } shouldContainExactly listOf(
            Money.zero(),
            Money.zero(),
            20.dollars,
            Money.zero(),
            Money.zero(),
            Money.zero(),
            5.dollars,
        )
    }

    "the average is the week's total over seven days, whatever the query left out" {
        val series = dailySpendSeries(
            points = listOf(point(4, 70.dollars)),
            today = today,
            currency = USD,
        )

        series.total shouldBe 70.dollars
        // 70 over seven days, not 70 over the one day that booked anything.
        series.average shouldBe 10.dollars
    }

    "month-to-date leaves out the days the window reached back into the previous month" {
        val startOfMonth = LocalDate(2026, 4, 3)
        val series = dailySpendSeries(
            points = listOf(
                point(day = 30, amount = 100.dollars, month = 3),
                point(day = 1, amount = 40.dollars),
                point(day = 3, amount = 2.dollars),
            ),
            today = startOfMonth,
            currency = USD,
        )

        // March is on the chart — it is one of the last seven days — but not in the month's total.
        series.days.map { it.total } shouldContainExactly listOf(
            Money.zero(),
            Money.zero(),
            100.dollars,
            Money.zero(),
            40.dollars,
            Money.zero(),
            2.dollars,
        )
        series.monthToDate shouldBe 42.dollars
    }

    "the projection adds the average for every day still ahead, today excluded" {
        val series = dailySpendSeries(
            points = listOf(point(4, 70.dollars), point(10, 30.dollars)),
            today = today,
            currency = USD,
        )

        // Today is already inside month-to-date, so only the 20 days after it are projected.
        series.daysAheadInMonth shouldBe 20
        // 100 booked so far, plus 20 days at the 100/7 = 14.28 average.
        series.average shouldBe Money(1428)
        series.project(monthlyLimit = null).projectedMonthTotal shouldBe 100.dollars + Money(1428 * 20)
    }

    "a month that ends the day it is read has nothing left to project" {
        val lastDay = LocalDate(2026, 4, 30)
        val series = dailySpendSeries(points = listOf(point(30, 9.dollars)), today = lastDay, currency = USD)

        series.daysAheadInMonth shouldBe 0
        series.project(monthlyLimit = null).projectedMonthTotal shouldBe series.monthToDate
    }

    "bars are scaled to the busiest day of the week" {
        val series = dailySpendSeries(
            points = listOf(point(8, 25.dollars), point(9, 100.dollars), point(10, 50.dollars)),
            today = today,
            currency = USD,
        )

        series.barFractions shouldContainExactly listOf(0f, 0f, 0f, 0f, 0.25f, 1f, 0.5f)
    }

    "a week that booked nothing draws a flat chart rather than dividing by zero" {
        val series = dailySpendSeries(points = emptyList(), today = today, currency = USD)

        series.barFractions shouldContainExactly List(BURN_RATE_DAYS) { 0f }
        series.average shouldBe Money.zero()
    }

    "with no budget the projection still stands, it just carries no verdict" {
        val burnRate = dailySpendSeries(listOf(point(10, 7.dollars)), today, USD).project(monthlyLimit = null)

        burnRate.monthlyLimit.shouldBeNull()
        burnRate.pace.shouldBeNull()
        burnRate.currency shouldBe USD
    }

    "a projection that lands on the limit is still on track" {
        // 7 a day across the whole window, all of it this month: 49 booked plus 20 days at 7 = 189.
        val series = dailySpendSeries(List(BURN_RATE_DAYS) { point(4 + it, 7.dollars) }, today, USD)

        series.project(monthlyLimit = null).projectedMonthTotal shouldBe 189.dollars
        series.project(monthlyLimit = 189.dollars).pace shouldBe BurnRatePace.OnTrack
        series.project(monthlyLimit = 188.dollars).pace shouldBe BurnRatePace.OffPace
    }

    "no budget at all caps nothing" {
        emptyList<Budget>().monthlySpendCap().shouldBeNull()
    }

    "an archived budget does not cap the month" {
        val archived = aBudget(amount = 900.dollars, categoryIds = emptyList(), isActive = false)

        listOf(archived).monthlySpendCap().shouldBeNull()
    }

    "a weekly or yearly limit is a different quantity, so neither caps a month" {
        val weekly = aBudget(amount = 200.dollars, categoryIds = emptyList(), period = BudgetPeriod.WEEKLY)
        val yearly = aBudget(
            id = budgetId("b-yearly"),
            amount = 9_000.dollars,
            categoryIds = emptyList(),
            period = BudgetPeriod.YEARLY,
        )

        listOf(weekly, yearly).monthlySpendCap().shouldBeNull()
    }

    "a category budget covers a slice of the spend, so it never judges the whole month" {
        val groceries = aBudget(amount = 300.dollars, categoryIds = listOf(categoryId("c-food")))

        listOf(groceries).monthlySpendCap().shouldBeNull()
    }

    "the largest general monthly budget sets the cap, ties broken by id" {
        val small = aBudget(id = budgetId("b-1"), amount = 400.dollars, categoryIds = emptyList())
        val large = aBudget(id = budgetId("b-2"), amount = 1_200.dollars, categoryIds = emptyList())
        val tie = aBudget(id = budgetId("b-0"), amount = 1_200.dollars, categoryIds = emptyList())
        val category = aBudget(id = budgetId("b-3"), amount = 5_000.dollars)

        listOf(small, large, category).monthlySpendCap() shouldBe 1_200.dollars
        // Two equal caps must not swap the answer between recompositions.
        listOf(large, tie).monthlySpendCap() shouldBe 1_200.dollars
    }
})
