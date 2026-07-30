package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.primitives.Money
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.YearMonth

private val Window = trailingMonths(anchor = YearMonth(2026, 4), count = 3)

private fun net(month: YearMonth, income: Money = Money.zero(), expense: Money = Money.zero()) =
    MonthlyNet(month = month, income = income, expense = expense)

class BalanceTrendTest : StringSpec({

    "the newest point is the balance the caller anchored on" {
        val trend = buildBalanceTrend(
            currentBalance = 1_000.dollars,
            nets = listOf(net(YearMonth(2026, 4), income = 300.dollars, expense = 100.dollars)),
            months = Window,
        )

        trend.points.last().month shouldBe YearMonth(2026, 4)
        trend.points.last().balance shouldBe 1_000.dollars
    }

    "each earlier point backs its own month's net out of the one after it" {
        val trend = buildBalanceTrend(
            currentBalance = 1_000.dollars,
            nets = listOf(
                net(YearMonth(2026, 2), income = 500.dollars, expense = 200.dollars),
                net(YearMonth(2026, 3), income = 100.dollars, expense = 400.dollars),
                net(YearMonth(2026, 4), income = 300.dollars, expense = 100.dollars),
            ),
            months = Window,
        )

        // April netted +200, March −300: February closed on 800, and January's close (the point
        // February is measured from) is not drawn.
        trend.points.map { it.balance } shouldContainExactly listOf(
            1_100.dollars,
            800.dollars,
            1_000.dollars,
        )
    }

    "a month the aggregate returned no row for holds the balance flat instead of dropping a point" {
        val trend = buildBalanceTrend(
            currentBalance = 1_000.dollars,
            nets = listOf(net(YearMonth(2026, 4), income = 250.dollars)),
            months = Window,
        )

        trend.points.map { it.month } shouldContainExactly Window
        trend.points.map { it.balance } shouldContainExactly listOf(
            750.dollars,
            750.dollars,
            1_000.dollars,
        )
    }

    "the delta is the newest month's net, not the change across the whole window" {
        val trend = buildBalanceTrend(
            currentBalance = 1_000.dollars,
            nets = listOf(
                net(YearMonth(2026, 3), income = 100.dollars, expense = 400.dollars),
                net(YearMonth(2026, 4), income = 300.dollars, expense = 100.dollars),
            ),
            months = Window,
        )

        trend.delta shouldBe 200.dollars
        // Across the drawn window the balance is down 100. Subtracting the ends answers a different
        // question from "what moved this month", which is what the widget prints.
        trend.points.first().balance shouldBe 1_100.dollars
        trend.points.last().balance shouldBe 1_000.dollars
    }

    "rows outside the window cannot bend the curve" {
        val trend = buildBalanceTrend(
            currentBalance = 1_000.dollars,
            nets = listOf(net(YearMonth(2025, 12), income = 5_000.dollars)),
            months = Window,
        )

        trend.points.map { it.balance } shouldContainExactly listOf(
            1_000.dollars,
            1_000.dollars,
            1_000.dollars,
        )
        trend.delta shouldBe Money.zero()
    }

    "two rows for one month are summed rather than one of them winning" {
        val trend = buildBalanceTrend(
            currentBalance = 1_000.dollars,
            nets = listOf(
                net(YearMonth(2026, 4), income = 100.dollars),
                net(YearMonth(2026, 4), expense = 40.dollars),
            ),
            months = Window,
        )

        trend.delta shouldBe 60.dollars
    }

    "no months means no curve" {
        val trend = buildBalanceTrend(
            currentBalance = 1_000.dollars,
            nets = listOf(net(YearMonth(2026, 4), income = 100.dollars)),
            months = emptyList(),
        )

        trend shouldBe BalanceTrend.Empty
        trend.points.shouldBeEmpty()
    }
})
