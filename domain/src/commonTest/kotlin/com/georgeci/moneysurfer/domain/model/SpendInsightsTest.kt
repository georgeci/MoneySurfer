package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.dashboard.DashboardPeriod
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

private val March = YearMonth(2026, 3)
private val April = YearMonth(2026, 4)

class SpendInsightsTest : StringSpec({

    "a selection cuts the same window periodWindow does" {
        val month = InsightsSelection(DashboardPeriod.Month, LocalDate(2026, 3, 17))

        month.window shouldBe TransactionPeriodWindow(LocalDate(2026, 3, 1), LocalDate(2026, 3, 31))
    }

    "a week selection covers the ISO week containing the anchor" {
        val week = InsightsSelection(DashboardPeriod.Week, LocalDate(2026, 3, 17))

        week.window shouldBe TransactionPeriodWindow(LocalDate(2026, 3, 16), LocalDate(2026, 3, 22))
    }

    "shifting a month selection steps from the first, so a short month cannot accumulate" {
        val endOf31 = InsightsSelection(DashboardPeriod.Month, LocalDate(2026, 1, 31))

        val back = endOf31.shifted(-1).shifted(-1)

        back.window shouldBe TransactionPeriodWindow(LocalDate(2025, 11, 1), LocalDate(2025, 11, 30))
    }

    "the trend keeps a column for a month nothing was booked in" {
        val trend = buildNetTrend(
            months = listOf(March, April),
            rows = listOf(MonthlyNet(April, income = 100.dollars, expense = 40.dollars)),
        )

        trend.map { it.month } shouldBe listOf(March, April)
        trend.first().income shouldBe Money.zero()
        trend.first().expense shouldBe Money.zero()
        trend.last().net shouldBe 60.dollars
    }

    "the trend ignores a row for a month it was not asked about" {
        val trend = buildNetTrend(
            months = listOf(April),
            rows = listOf(
                MonthlyNet(March, income = 900.dollars, expense = 900.dollars),
                MonthlyNet(April, income = 100.dollars, expense = 40.dollars),
            ),
        )

        trend.map { it.month } shouldBe listOf(April)
    }

    "the base-currency filter is only blamed when it is the reason nothing is drawn" {
        val filtered = insightsOf(entries = emptyList(), excluded = listOf(CurrencyTotal(USD, 50.dollars)))
        val quiet = insightsOf(entries = emptyList(), excluded = emptyList())
        val mixed = insightsOf(
            entries = listOf(CategorySpend(category = null, spent = 10.dollars, share = 1f, cap = null)),
            excluded = listOf(CurrencyTotal(USD, 50.dollars)),
        )

        filtered.hiddenByBaseCurrency shouldBe true
        quiet.hiddenByBaseCurrency shouldBe false
        mixed.hiddenByBaseCurrency shouldBe false
    }
})

private fun insightsOf(
    entries: List<CategorySpend>,
    excluded: List<CurrencyTotal>,
): SpendInsights {
    val selection = InsightsSelection(DashboardPeriod.Month, LocalDate(2026, 3, 17))
    return SpendInsights(
        selection = selection,
        currency = USD,
        breakdown = SpentByCategory(
            entries = entries,
            total = entries.fold(Money.zero()) { acc, entry -> acc + entry.spent },
            currency = USD,
            window = selection.window,
        ),
        months = emptyList(),
        merchants = emptyList(),
        excludedByCurrency = excluded,
    )
}
