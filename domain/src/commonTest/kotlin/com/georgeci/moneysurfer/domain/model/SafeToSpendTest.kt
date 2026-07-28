package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.budgetId
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

private val periodStart = LocalDate(2026, 4, 1)

/** April 2026: a 30-day window, so a mid-month "today" gives round pace numbers. */
private fun progressOf(
    budget: Budget,
    spent: Money = Money.zero(),
    today: LocalDate = LocalDate(2026, 4, 16),
) = calculateBudgetProgress(
    budget = budget,
    transactions = if (spent.isZero()) {
        emptyList()
    } else {
        listOf(
            aTransaction(
                id = transactionId("tx-${budget.id.value}"),
                money = spent,
                operationDate = periodStart,
                type = TransactionType.EXPENSE,
                currencyCode = USD,
            ),
        )
    },
    baseCurrency = USD,
    today = today,
)

class SafeToSpendTest : StringSpec({

    "no budgets means no number to show" {
        emptyList<BudgetProgress>().safeToSpend().shouldBeNull()
    }

    "archived budgets never speak for the headline" {
        val archived = aBudget(amount = 600.dollars, startDate = periodStart, isActive = false)

        listOf(progressOf(archived)).safeToSpend().shouldBeNull()
    }

    "the headline carries the remainder, the per-day pace and the days left" {
        val budget = aBudget(amount = 600.dollars, startDate = periodStart, categoryIds = emptyList())

        val safeToSpend = listOf(progressOf(budget, spent = 300.dollars)).safeToSpend()

        safeToSpend?.remaining shouldBe 300.dollars
        safeToSpend?.limit shouldBe 600.dollars
        safeToSpend?.spent shouldBe 300.dollars
        // 15 days of April still to come, counting today.
        safeToSpend?.daysLeft shouldBe 15
        safeToSpend?.perDay shouldBe 20.dollars
        safeToSpend?.isOver shouldBe false
    }

    "an overspent budget reports a negative remainder rather than flooring at zero" {
        val budget = aBudget(amount = 600.dollars, startDate = periodStart, categoryIds = emptyList())

        val safeToSpend = listOf(progressOf(budget, spent = 750.dollars)).safeToSpend()

        safeToSpend?.remaining shouldBe -(150.dollars)
        safeToSpend?.isOver shouldBe true
        safeToSpend?.status shouldBe BudgetStatus.OVER
        // Nothing is left, so there is no daily pace to hold.
        safeToSpend?.perDay shouldBe Money.zero()
    }

    "the pace tick is the share of the window already behind us, today excluded" {
        val budget = aBudget(amount = 600.dollars, startDate = periodStart, categoryIds = emptyList())

        // 16 April: 15 days done, 15 to go out of 30.
        listOf(progressOf(budget)).safeToSpend()?.elapsedFraction shouldBe 0.5f
        // The first day of the window has nothing behind it yet.
        listOf(progressOf(budget, today = periodStart)).safeToSpend()?.elapsedFraction shouldBe 0f
    }

    "spend past the limit reports a fraction over 1 — the bar caps it, the maths does not" {
        val budget = aBudget(amount = 600.dollars, startDate = periodStart, categoryIds = emptyList())

        listOf(progressOf(budget, spent = 900.dollars)).safeToSpend()?.spentFraction shouldBe 1.5f
    }

    "a general budget outranks a category one, whatever their limits" {
        val general = aBudget(
            id = budgetId("b-general"),
            name = "Everyday",
            amount = 400.dollars,
            startDate = periodStart,
            categoryIds = emptyList(),
        )
        val category = aBudget(
            id = budgetId("b-groceries"),
            name = "Groceries",
            amount = 900.dollars,
            startDate = periodStart,
            categoryIds = listOf(categoryId("groceries")),
        )

        listOf(progressOf(category), progressOf(general)).safeToSpend()?.budgetName shouldBe "Everyday"
    }

    "with only category budgets the largest limit speaks" {
        val small = aBudget(
            id = budgetId("b-small"),
            name = "Coffee",
            amount = 50.dollars,
            startDate = periodStart,
            categoryIds = listOf(categoryId("coffee")),
        )
        val large = aBudget(
            id = budgetId("b-large"),
            name = "Groceries",
            amount = 500.dollars,
            startDate = periodStart,
            categoryIds = listOf(categoryId("groceries")),
        )

        listOf(progressOf(small), progressOf(large)).safeToSpend()?.budgetName shouldBe "Groceries"
    }

    "two budgets with the same limit resolve the same way every time" {
        val first = aBudget(
            id = budgetId("b-1"),
            name = "First",
            amount = 500.dollars,
            startDate = periodStart,
            categoryIds = emptyList(),
        )
        val second = aBudget(
            id = budgetId("b-2"),
            name = "Second",
            amount = 500.dollars,
            startDate = periodStart,
            categoryIds = emptyList(),
        )

        listOf(progressOf(first), progressOf(second)).safeToSpend()?.budgetName shouldBe "First"
        listOf(progressOf(second), progressOf(first)).safeToSpend()?.budgetName shouldBe "First"
    }
})
