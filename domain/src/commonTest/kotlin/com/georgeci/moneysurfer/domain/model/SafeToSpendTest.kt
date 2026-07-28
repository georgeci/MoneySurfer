package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.aTransaction
import com.georgeci.moneysurfer.domain.fixtures.budgetId
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.transactionId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.util.BudgetPeriodWindow
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
    baseCurrency: CurrencyCode? = USD,
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
    baseCurrency = baseCurrency,
    today = today,
)

class SafeToSpendTest : StringSpec({

    "no budgets means no number to show" {
        emptyList<BudgetProgress>().safeToSpend().shouldBeNull()
    }

    "a budget whose workspace has no base currency yields nothing to format" {
        val budget = aBudget(amount = 600.dollars, startDate = periodStart, categoryIds = emptyList())

        // The workspace row is missing behind a budget that references it — a state a pull can
        // produce for a moment. Money the app cannot name must not reach MoneyFormatter, which
        // rejects anything that is not a three-letter ISO code.
        listOf(progressOf(budget, baseCurrency = null)).safeToSpend().shouldBeNull()
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

    "a zero limit reads as no spend at all rather than dividing by it" {
        val budget = aBudget(amount = Money.zero(), startDate = periodStart, categoryIds = emptyList())

        listOf(progressOf(budget, spent = 40.dollars)).safeToSpend()?.spentFraction shouldBe 0f
    }

    "a window with no days in it reads as fully elapsed, never as a division by zero" {
        val instant = LocalDate(2026, 4, 16)
        val degenerate = SafeToSpend(
            budgetName = "Broken",
            remaining = Money.zero(),
            perDay = Money.zero(),
            daysLeft = 0,
            spent = Money.zero(),
            limit = 100.dollars,
            window = BudgetPeriodWindow(instant, instant),
            status = BudgetStatus.OK,
            currency = USD,
        )

        degenerate.elapsedFraction shouldBe 1f
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

    "the period on screen picks between two general budgets on different cadences" {
        val weekly = aBudget(
            id = budgetId("b-weekly"),
            name = "This week",
            amount = 200.dollars,
            period = BudgetPeriod.WEEKLY,
            startDate = periodStart,
            categoryIds = emptyList(),
        )
        val monthly = aBudget(
            id = budgetId("b-monthly"),
            name = "This month",
            amount = 800.dollars,
            period = BudgetPeriod.MONTHLY,
            startDate = periodStart,
            categoryIds = emptyList(),
        )
        val progresses = listOf(progressOf(weekly), progressOf(monthly))

        progresses.safeToSpend(BudgetPeriod.WEEKLY)?.budgetName shouldBe "This week"
        progresses.safeToSpend(BudgetPeriod.MONTHLY)?.budgetName shouldBe "This month"
    }

    "asking for no particular period leaves the pre-period order untouched" {
        val weekly = aBudget(
            id = budgetId("b-weekly"),
            name = "This week",
            amount = 200.dollars,
            period = BudgetPeriod.WEEKLY,
            startDate = periodStart,
            categoryIds = emptyList(),
        )
        val monthly = aBudget(
            id = budgetId("b-monthly"),
            name = "This month",
            amount = 800.dollars,
            period = BudgetPeriod.MONTHLY,
            startDate = periodStart,
            categoryIds = emptyList(),
        )

        // The larger limit still wins, exactly as it did before the switch existed.
        listOf(progressOf(weekly), progressOf(monthly)).safeToSpend()?.budgetName shouldBe "This month"
    }

    "a preferred period never promotes a category budget over a general one" {
        val general = aBudget(
            id = budgetId("b-general"),
            name = "Everyday",
            amount = 2000.dollars,
            period = BudgetPeriod.MONTHLY,
            startDate = periodStart,
            categoryIds = emptyList(),
        )
        val weeklyCoffee = aBudget(
            id = budgetId("b-coffee"),
            name = "Coffee",
            amount = 20.dollars,
            period = BudgetPeriod.WEEKLY,
            startDate = periodStart,
            categoryIds = listOf(categoryId("coffee")),
        )

        // A screen set to Week must not answer "what is safe to spend" with a number about coffee:
        // the switch is a tiebreak inside the general tier, not a filter across tiers.
        listOf(progressOf(weeklyCoffee), progressOf(general))
            .safeToSpend(BudgetPeriod.WEEKLY)?.budgetName shouldBe "Everyday"
    }

    "a period nothing is budgeted on leaves the largest limit speaking" {
        val monthly = aBudget(
            id = budgetId("b-monthly"),
            name = "This month",
            amount = 800.dollars,
            period = BudgetPeriod.MONTHLY,
            startDate = periodStart,
            categoryIds = emptyList(),
        )

        // Week selected, no weekly budget in the workspace — the widget states the monthly cap
        // rather than falling to its empty state, which would read as "no budget yet".
        listOf(progressOf(monthly)).safeToSpend(BudgetPeriod.WEEKLY)?.budgetName shouldBe "This month"
    }
})
