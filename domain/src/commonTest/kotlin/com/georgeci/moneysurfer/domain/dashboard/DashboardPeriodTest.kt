package com.georgeci.moneysurfer.domain.dashboard

import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import com.georgeci.moneysurfer.domain.util.periodWindow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

class DashboardPeriodTest : StringSpec({

    "the dashboard opens on Month" {
        DashboardPeriod.DEFAULT shouldBe DashboardPeriod.Month
    }

    "Week cuts the ISO week containing the day, Monday to Sunday" {
        // 2026-04-16 is a Thursday.
        DashboardPeriod.Week.window(LocalDate(2026, 4, 16)) shouldBe TransactionPeriodWindow(
            from = LocalDate(2026, 4, 13),
            to = LocalDate(2026, 4, 19),
        )
    }

    "Month cuts the calendar month containing the day" {
        DashboardPeriod.Month.window(LocalDate(2026, 4, 16)) shouldBe TransactionPeriodWindow(
            from = LocalDate(2026, 4, 1),
            to = LocalDate(2026, 4, 30),
        )
    }

    "every period is bounded — the dashboard offers no all-time span" {
        DashboardPeriod.entries.forEach { period ->
            period.window(LocalDate(2026, 4, 16)).isUnbounded shouldBe false
        }
    }

    "a dashboard period and a transactions list on the same mode cut the same dates" {
        val today = LocalDate(2026, 12, 31)

        // The two screens must agree, which is why `window` delegates rather than re-deriving:
        // a New Year's Eve that belongs to next year's ISO week 53 is exactly where a second
        // implementation would drift.
        DashboardPeriod.entries.forEach { period ->
            period.window(today) shouldBe periodWindow(period.mode, today)
        }
    }

    "the switch's two entries map onto the transaction and budget spellings of the same span" {
        DashboardPeriod.Week.mode shouldBe TransactionPeriodMode.Week
        DashboardPeriod.Month.mode shouldBe TransactionPeriodMode.Month
        DashboardPeriod.Week.budgetPeriod shouldBe BudgetPeriod.WEEKLY
        DashboardPeriod.Month.budgetPeriod shouldBe BudgetPeriod.MONTHLY
    }
})
