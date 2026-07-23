package com.georgeci.moneysurfer.domain.util

import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

class TransactionPeriodWindowTest : StringSpec({

    "month window spans the whole calendar month" {
        val window = periodWindow(TransactionPeriodMode.Month, LocalDate(2025, 3, 17))

        window.from shouldBe LocalDate(2025, 3, 1)
        window.to shouldBe LocalDate(2025, 3, 31)
    }

    "month window ends on the short month's real last day" {
        periodWindow(TransactionPeriodMode.Month, LocalDate(2024, 2, 10)).to shouldBe LocalDate(2024, 2, 29)
        periodWindow(TransactionPeriodMode.Month, LocalDate(2025, 2, 10)).to shouldBe LocalDate(2025, 2, 28)
    }

    "week window runs Monday to Sunday around the anchor" {
        val window = periodWindow(TransactionPeriodMode.Week, LocalDate(2025, 3, 27))

        window.from shouldBe LocalDate(2025, 3, 24)
        window.to shouldBe LocalDate(2025, 3, 30)
    }

    "a Sunday anchor still belongs to the week that started the previous Monday" {
        val window = periodWindow(TransactionPeriodMode.Week, LocalDate(2025, 3, 30))

        window.from shouldBe LocalDate(2025, 3, 24)
        window.to shouldBe LocalDate(2025, 3, 30)
    }

    "all time is unbounded and contains everything" {
        val window = periodWindow(TransactionPeriodMode.AllTime, LocalDate(2025, 3, 17))

        window shouldBe TransactionPeriodWindow.Unbounded
        window.isUnbounded shouldBe true
        (LocalDate(1970, 1, 1) in window) shouldBe true
    }

    "contains is inclusive on both ends" {
        val window = periodWindow(TransactionPeriodMode.Month, LocalDate(2025, 3, 17))

        (LocalDate(2025, 3, 1) in window) shouldBe true
        (LocalDate(2025, 3, 31) in window) shouldBe true
        (LocalDate(2025, 2, 28) in window) shouldBe false
        (LocalDate(2025, 4, 1) in window) shouldBe false
    }

    "shifting a month steps whole months without short-month drift" {
        var anchor = LocalDate(2025, 1, 31)

        anchor = shiftPeriod(TransactionPeriodMode.Month, anchor, by = 1)
        periodWindow(TransactionPeriodMode.Month, anchor).from shouldBe LocalDate(2025, 2, 1)

        anchor = shiftPeriod(TransactionPeriodMode.Month, anchor, by = 1)
        periodWindow(TransactionPeriodMode.Month, anchor).to shouldBe LocalDate(2025, 3, 31)
    }

    "shifting a month backwards crosses the year boundary" {
        val anchor = shiftPeriod(TransactionPeriodMode.Month, LocalDate(2025, 1, 15), by = -1)

        periodWindow(TransactionPeriodMode.Month, anchor).from shouldBe LocalDate(2024, 12, 1)
    }

    "shifting a week steps seven days from the week start" {
        val anchor = shiftPeriod(TransactionPeriodMode.Week, LocalDate(2025, 3, 27), by = -1)

        periodWindow(TransactionPeriodMode.Week, anchor).from shouldBe LocalDate(2025, 3, 17)
    }

    "shifting all time is a no-op" {
        shiftPeriod(TransactionPeriodMode.AllTime, LocalDate(2025, 3, 27), by = -3) shouldBe
            LocalDate(2025, 3, 27)
    }

    "ISO week numbering matches the calendar" {
        LocalDate(2025, 3, 27).isoWeek() shouldBe IsoWeek(weekNumber = 13, weekYear = 2025)
        LocalDate(2025, 1, 1).isoWeek() shouldBe IsoWeek(weekNumber = 1, weekYear = 2025)
    }

    "early January can still belong to the previous week-year" {
        // 2027-01-01 is a Friday, so its week began on 2026-12-28 — ISO week 53 of 2026.
        LocalDate(2027, 1, 1).isoWeek() shouldBe IsoWeek(weekNumber = 53, weekYear = 2026)
    }

    "late December can already belong to the next week-year" {
        // 2024-12-30 is a Monday whose Thursday falls in 2025 — ISO week 1 of 2025.
        LocalDate(2024, 12, 30).isoWeek() shouldBe IsoWeek(weekNumber = 1, weekYear = 2025)
    }
})
