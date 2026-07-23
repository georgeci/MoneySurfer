package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.util.BudgetPeriodWindow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

private val periodStart = LocalDate(2026, 4, 1)

private fun expense(
    id: String,
    amount: Money,
    date: LocalDate,
) = aTransaction(
    id = transactionId(id),
    money = amount,
    operationDate = date,
    type = TransactionType.EXPENSE,
    currencyCode = USD,
)

class BudgetProgressTest : StringSpec({

    "spent sums the expenses inside the window and leaves the rest out" {
        val budget = aBudget(amount = 400.dollars, startDate = periodStart, categoryIds = emptyList())
        val transactions = listOf(
            expense("in-1", 100.dollars, LocalDate(2026, 4, 2)),
            expense("in-2", 50.dollars, LocalDate(2026, 4, 15)),
            expense("before", 999.dollars, LocalDate(2026, 3, 31)),
            expense("after", 999.dollars, LocalDate(2026, 5, 1)),
        )

        val progress = calculateBudgetProgress(budget, transactions, USD, today = LocalDate(2026, 4, 15))

        progress.spent shouldBe 150.dollars
        progress.window shouldBe BudgetPeriodWindow(periodStart, LocalDate(2026, 5, 1))
    }

    "transfers never count against a budget" {
        val budget = aBudget(amount = 400.dollars, startDate = periodStart, categoryIds = emptyList())
        val transactions = listOf(
            expense("plain", 100.dollars, LocalDate(2026, 4, 2)),
            expense("leg", 300.dollars, LocalDate(2026, 4, 3)).copy(transferId = TransferId("tr-1")),
        )

        calculateBudgetProgress(budget, transactions, USD, today = LocalDate(2026, 4, 15)).spent shouldBe 100.dollars
    }

    "income, planned and other-workspace rows are ignored" {
        val budget = aBudget(amount = 400.dollars, startDate = periodStart, categoryIds = emptyList())
        val transactions = listOf(
            expense("expense", 100.dollars, LocalDate(2026, 4, 2)),
            expense("income", 500.dollars, LocalDate(2026, 4, 2)).copy(type = TransactionType.INCOME),
            expense("planned", 500.dollars, LocalDate(2026, 4, 2)).copy(status = TransactionStatus.PLANNED),
        )

        calculateBudgetProgress(budget, transactions, USD, today = LocalDate(2026, 4, 15)).spent shouldBe 100.dollars
    }

    "a budget with categories only counts those categories" {
        val budget = aBudget(
            amount = 400.dollars,
            startDate = periodStart,
            categoryIds = listOf(categoryId("groceries")),
        )
        val transactions = listOf(
            expense("match", 100.dollars, LocalDate(2026, 4, 2)).copy(categoryId = categoryId("groceries")),
            expense("other", 200.dollars, LocalDate(2026, 4, 2)).copy(categoryId = categoryId("dining")),
        )

        calculateBudgetProgress(budget, transactions, USD, today = LocalDate(2026, 4, 15)).spent shouldBe 100.dollars
    }

    "foreign-currency expenses are skipped and raise the mixed-currency flag" {
        val budget = aBudget(amount = 400.dollars, startDate = periodStart, categoryIds = emptyList())
        val transactions = listOf(
            expense("base", 100.dollars, LocalDate(2026, 4, 2)),
            expense("foreign", 200.dollars, LocalDate(2026, 4, 3)).copy(currencyCode = EUR),
        )

        val progress = calculateBudgetProgress(budget, transactions, USD, today = LocalDate(2026, 4, 15))

        progress.spent shouldBe 100.dollars
        progress.hasMixedCurrency shouldBe true
    }

    "status is OK below the alert threshold, WARN at it and OVER past the limit" {
        val budget = aBudget(
            amount = 100.dollars,
            startDate = periodStart,
            alertPercent = 80,
            categoryIds = emptyList(),
        )
        fun statusAt(spent: Int) = calculateBudgetProgress(
            budget = budget,
            transactions = listOf(expense("e", spent.dollars, LocalDate(2026, 4, 2))),
            baseCurrency = USD,
            today = LocalDate(2026, 4, 15),
        ).status

        statusAt(79) shouldBe BudgetStatus.OK
        statusAt(80) shouldBe BudgetStatus.WARN
        statusAt(100) shouldBe BudgetStatus.WARN
        statusAt(101) shouldBe BudgetStatus.OVER
    }

    "rollover carries the previous window's leftover into the effective limit" {
        val budget = aBudget(
            amount = 400.dollars,
            startDate = periodStart,
            rollover = true,
            categoryIds = emptyList(),
        )
        val transactions = listOf(
            expense("previous", 300.dollars, LocalDate(2026, 3, 10)),
            expense("current", 100.dollars, LocalDate(2026, 4, 2)),
        )

        val progress = calculateBudgetProgress(budget, transactions, USD, today = LocalDate(2026, 4, 15))

        progress.rolloverCarry shouldBe 100.dollars
        progress.effectiveLimit shouldBe 500.dollars
        progress.remaining shouldBe 400.dollars
        progress.percent shouldBe 20
    }

    "an overspent previous window carries nothing — the debt is not doubled" {
        val budget = aBudget(
            amount = 400.dollars,
            startDate = periodStart,
            rollover = true,
            categoryIds = emptyList(),
        )
        val transactions = listOf(expense("previous", 500.dollars, LocalDate(2026, 3, 10)))

        val progress = calculateBudgetProgress(budget, transactions, USD, today = LocalDate(2026, 4, 15))

        progress.rolloverCarry shouldBe Money.zero()
        progress.effectiveLimit shouldBe 400.dollars
    }

    "rollover off ignores the previous window entirely" {
        val budget = aBudget(amount = 400.dollars, startDate = periodStart, categoryIds = emptyList())
        val transactions = listOf(expense("previous", 10.dollars, LocalDate(2026, 3, 10)))

        calculateBudgetProgress(budget, transactions, USD, today = LocalDate(2026, 4, 15))
            .effectiveLimit shouldBe 400.dollars
    }

    "forecast tiles extrapolate the elapsed days across the nominal period" {
        val budget = aBudget(amount = 300.dollars, startDate = periodStart, categoryIds = emptyList())
        // 10 days elapsed (Apr 1..10 inclusive), 100 spent → 10/day → 300 projected over 30 days.
        val transactions = listOf(expense("e", 100.dollars, LocalDate(2026, 4, 2)))

        val progress = calculateBudgetProgress(budget, transactions, USD, today = LocalDate(2026, 4, 10))

        progress.dailyAverage shouldBe 10.dollars
        progress.projectedTotal shouldBe 300.dollars
        progress.daysLeft shouldBe 21
        // 200 left over 21 remaining days.
        progress.perDayRemaining shouldBe Money(952)
    }

    "the first day of a window divides by one, not by zero" {
        val budget = aBudget(amount = 300.dollars, startDate = periodStart, categoryIds = emptyList())
        val transactions = listOf(expense("e", 50.dollars, periodStart))

        val progress = calculateBudgetProgress(budget, transactions, USD, today = periodStart)

        progress.dailyAverage shouldBe 50.dollars
        progress.daysLeft shouldBe 30
    }

    "an overspent budget reports zero per-day remaining instead of a negative allowance" {
        val budget = aBudget(amount = 100.dollars, startDate = periodStart, categoryIds = emptyList())
        val transactions = listOf(expense("e", 150.dollars, LocalDate(2026, 4, 2)))

        val progress = calculateBudgetProgress(budget, transactions, USD, today = LocalDate(2026, 4, 10))

        progress.remaining shouldBe (-50).dollars
        progress.perDayRemaining shouldBe Money.zero()
        progress.percent shouldBe 150
    }

    "a zero limit reads as 0 % rather than dividing by zero" {
        val budget = aBudget(amount = Money.zero(), startDate = periodStart, categoryIds = emptyList())
        val transactions = listOf(expense("e", 10.dollars, LocalDate(2026, 4, 2)))

        val progress = calculateBudgetProgress(budget, transactions, USD, today = LocalDate(2026, 4, 10))

        progress.percent shouldBe 0
        progress.status shouldBe BudgetStatus.OVER
    }

    "transactionsIn returns the spend behind the number, newest first" {
        val budget = aBudget(amount = 400.dollars, startDate = periodStart, categoryIds = emptyList())
        val transactions = listOf(
            expense("old", 10.dollars, LocalDate(2026, 4, 2)),
            expense("new", 20.dollars, LocalDate(2026, 4, 20)),
            expense("outside", 30.dollars, LocalDate(2026, 5, 2)),
        )
        val window = BudgetPeriodWindow(periodStart, LocalDate(2026, 5, 1))

        budget.transactionsIn(transactions, window, USD).map { it.id.value } shouldBe listOf("new", "old")
    }
})
