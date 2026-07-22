package com.georgeci.moneysurfer.domain.util

import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

class BudgetPeriodWindowTest : StringSpec({

    "weekly window is the 7 days anchored on the start date's weekday" {
        // 2026-07-01 is a Wednesday; windows run Wed→Wed.
        val budget = aBudget(period = BudgetPeriod.WEEKLY, startDate = LocalDate(2026, 7, 1))

        budget.currentWindow(LocalDate(2026, 7, 5)) shouldBe
            BudgetPeriodWindow(LocalDate(2026, 7, 1), LocalDate(2026, 7, 8))
    }

    "weekly window start is inclusive and end is exclusive" {
        val budget = aBudget(period = BudgetPeriod.WEEKLY, startDate = LocalDate(2026, 7, 1))

        budget.currentWindow(LocalDate(2026, 7, 1)).startInclusive shouldBe LocalDate(2026, 7, 1)
        budget.currentWindow(LocalDate(2026, 7, 8)).startInclusive shouldBe LocalDate(2026, 7, 8)
    }

    "monthly window is anchored on the start date's day of month" {
        val budget = aBudget(period = BudgetPeriod.MONTHLY, startDate = LocalDate(2026, 3, 15))

        budget.currentWindow(LocalDate(2026, 5, 20)) shouldBe
            BudgetPeriodWindow(LocalDate(2026, 5, 15), LocalDate(2026, 6, 15))
    }

    "monthly anchor on the 31st clamps to the last day of a short month" {
        val budget = aBudget(period = BudgetPeriod.MONTHLY, startDate = LocalDate(2026, 1, 31))

        budget.currentWindow(LocalDate(2026, 3, 1)) shouldBe
            BudgetPeriodWindow(LocalDate(2026, 2, 28), LocalDate(2026, 3, 31))
    }

    "monthly clamping does not accumulate — the 31st returns after a short month" {
        val budget = aBudget(period = BudgetPeriod.MONTHLY, startDate = LocalDate(2026, 1, 31))

        // Naive "previous anchor + 1 month" would drift to Mar 28, then Apr 28, forever.
        budget.currentWindow(LocalDate(2026, 4, 15)) shouldBe
            BudgetPeriodWindow(LocalDate(2026, 3, 31), LocalDate(2026, 4, 30))
    }

    "monthly anchor on the 29th clamps in a non-leap February" {
        val budget = aBudget(period = BudgetPeriod.MONTHLY, startDate = LocalDate(2026, 1, 29))

        budget.currentWindow(LocalDate(2026, 2, 28)) shouldBe
            BudgetPeriodWindow(LocalDate(2026, 2, 28), LocalDate(2026, 3, 29))
    }

    "yearly window is anchored on the start date's month and day" {
        val budget = aBudget(period = BudgetPeriod.YEARLY, startDate = LocalDate(2024, 6, 10))

        budget.currentWindow(LocalDate(2026, 1, 5)) shouldBe
            BudgetPeriodWindow(LocalDate(2025, 6, 10), LocalDate(2026, 6, 10))
    }

    "yearly anchor on Feb 29 clamps to Feb 28 in a non-leap year" {
        val budget = aBudget(period = BudgetPeriod.YEARLY, startDate = LocalDate(2024, 2, 29))

        budget.currentWindow(LocalDate(2025, 6, 1)) shouldBe
            BudgetPeriodWindow(LocalDate(2025, 2, 28), LocalDate(2026, 2, 28))
    }

    "yearly clamping does not accumulate — Feb 29 returns in the next leap year" {
        val budget = aBudget(period = BudgetPeriod.YEARLY, startDate = LocalDate(2024, 2, 29))

        budget.currentWindow(LocalDate(2028, 6, 1)).startInclusive shouldBe LocalDate(2028, 2, 29)
    }

    "windows before the start date extend backwards so the first period has a predecessor" {
        val budget = aBudget(period = BudgetPeriod.MONTHLY, startDate = LocalDate(2026, 3, 15))

        budget.windowContaining(LocalDate(2026, 2, 20)) shouldBe
            BudgetPeriodWindow(LocalDate(2026, 2, 15), LocalDate(2026, 3, 15))
    }

    "previousWindow is the window immediately before the given one" {
        val budget = aBudget(period = BudgetPeriod.MONTHLY, startDate = LocalDate(2026, 1, 31))
        val current = budget.currentWindow(LocalDate(2026, 3, 1))

        budget.previousWindow(current) shouldBe
            BudgetPeriodWindow(LocalDate(2026, 1, 31), LocalDate(2026, 2, 28))
    }

    "previousWindow ends exactly where the given window starts — no gap, no overlap" {
        val budget = aBudget(period = BudgetPeriod.WEEKLY, startDate = LocalDate(2026, 7, 1))
        val current = budget.currentWindow(LocalDate(2026, 7, 20))

        budget.previousWindow(current).endExclusive shouldBe current.startInclusive
    }

    "contains covers the start date but not the end date" {
        val window = BudgetPeriodWindow(LocalDate(2026, 7, 1), LocalDate(2026, 8, 1))

        (LocalDate(2026, 7, 1) in window) shouldBe true
        (LocalDate(2026, 7, 31) in window) shouldBe true
        (LocalDate(2026, 8, 1) in window) shouldBe false
        (LocalDate(2026, 6, 30) in window) shouldBe false
    }
})
