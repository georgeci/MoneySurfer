package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.budgetId
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.testDate
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private val groceries = categoryId("c-groceries")
private val rent = categoryId("c-rent")

private val window = TransactionPeriodWindow(testDate, testDate)

private fun slice(categoryId: CategoryId?, total: Money) =
    CategorySpendSlice(categoryId = categoryId, total = total, transactionCount = 1)

private fun breakdown(
    slices: List<CategorySpendSlice>,
    budgets: List<Budget> = emptyList(),
    capPeriod: BudgetPeriod = BudgetPeriod.MONTHLY,
): SpentByCategory = buildSpentByCategory(
    slices = slices,
    categories = listOf(
        aCategory(id = groceries, name = "Groceries"),
        aCategory(id = rent, name = "Rent"),
    ),
    budgets = budgets,
    currency = USD,
    window = window,
    capPeriod = capPeriod,
)

class SpentByCategoryTest : StringSpec({

    "entries come out largest first, whatever order the query answered in" {
        val result = breakdown(listOf(slice(groceries, 40.dollars), slice(rent, 60.dollars)))

        result.entries.map { it.category?.name } shouldContainExactly listOf("Rent", "Groceries")
        result.total shouldBe 100.dollars
    }

    "a share is the entry's fraction of the period total" {
        val result = breakdown(listOf(slice(rent, 60.dollars), slice(groceries, 40.dollars)))

        result.entries[0].share shouldBe 0.6f
        result.entries[1].share shouldBe 0.4f
    }

    "the uncategorized bucket is an entry with no category, and it counts towards the total" {
        val result = breakdown(listOf(slice(rent, 60.dollars), slice(categoryId = null, total = 40.dollars)))

        val uncategorized = result.entries.single { it.category == null }
        uncategorized.spent shouldBe 40.dollars
        uncategorized.share shouldBe 0.4f
        // Nothing caps a bucket that is not a category, so the cap lookup must not match one.
        uncategorized.cap.shouldBeNull()
        result.total shouldBe 100.dollars
    }

    "a period with nothing in it reads as zero shares rather than a NaN" {
        val result = breakdown(listOf(slice(rent, Money.zero())))

        result.total shouldBe Money.zero()
        result.entries.single().share shouldBe 0f
    }

    "a budget naming exactly this category caps it" {
        val result = breakdown(
            slices = listOf(slice(groceries, 40.dollars)),
            budgets = listOf(aBudget(categoryIds = listOf(groceries), amount = 100.dollars)),
        )

        val entry = result.entries.single()
        entry.cap?.limit shouldBe 100.dollars
        entry.cap?.status shouldBe BudgetStatus.OK
        entry.capFraction shouldBe 0.4f
    }

    "the near-limit threshold is the budget's own alert percent" {
        val result = breakdown(
            slices = listOf(slice(groceries, 60.dollars)),
            budgets = listOf(
                aBudget(categoryIds = listOf(groceries), amount = 100.dollars, alertPercent = 50),
            ),
        )

        result.entries.single().cap?.status shouldBe BudgetStatus.WARN
    }

    "spending past the cap reads OVER, and the fraction is allowed past one" {
        val result = breakdown(
            slices = listOf(slice(groceries, 120.dollars)),
            budgets = listOf(aBudget(categoryIds = listOf(groceries), amount = 100.dollars)),
        )

        val entry = result.entries.single()
        entry.cap?.status shouldBe BudgetStatus.OVER
        entry.capFraction shouldBe 1.2f
    }

    "a budget spanning several categories caps none of them on its own" {
        val result = breakdown(
            slices = listOf(slice(groceries, 40.dollars)),
            // Its limit covers the pair, so overlaying it on one member would read as headroom
            // the pair does not have.
            budgets = listOf(aBudget(categoryIds = listOf(groceries, rent), amount = 100.dollars)),
        )

        result.entries.single().cap.shouldBeNull()
    }

    "the general budget is the envelope above per-category caps, not a cap on each of them" {
        val result = breakdown(
            slices = listOf(slice(groceries, 40.dollars)),
            budgets = listOf(aBudget(categoryIds = emptyList(), amount = 100.dollars)),
        )

        result.entries.single().cap.shouldBeNull()
    }

    "an archived budget caps nothing" {
        val result = breakdown(
            slices = listOf(slice(groceries, 40.dollars)),
            budgets = listOf(
                aBudget(categoryIds = listOf(groceries), amount = 100.dollars, isActive = false),
            ),
        )

        result.entries.single().cap.shouldBeNull()
    }

    "two budgets both naming only this category leave it uncapped rather than picking one" {
        val result = breakdown(
            slices = listOf(slice(groceries, 40.dollars)),
            budgets = listOf(
                aBudget(id = budgetId("b-1"), categoryIds = listOf(groceries), amount = 100.dollars),
                aBudget(id = budgetId("b-2"), categoryIds = listOf(groceries), amount = 200.dollars),
            ),
        )

        result.entries.single().cap.shouldBeNull()
    }

    "a budget on another cadence is not a figure for this window, so it caps nothing here" {
        val result = breakdown(
            slices = listOf(slice(groceries, 40.dollars)),
            budgets = listOf(
                aBudget(
                    categoryIds = listOf(groceries),
                    amount = 100.dollars,
                    period = BudgetPeriod.WEEKLY,
                ),
            ),
        )

        result.entries.single().cap.shouldBeNull()
    }

    "under a weekly period it is the weekly cap that speaks, and the monthly one that does not" {
        val weekly = aBudget(
            categoryIds = listOf(groceries),
            amount = 100.dollars,
            period = BudgetPeriod.WEEKLY,
        )

        // The same spend, read at the two cadences: each may only be judged by its own.
        breakdown(listOf(slice(groceries, 40.dollars)), listOf(weekly), BudgetPeriod.WEEKLY)
            .entries.single().cap?.limit shouldBe 100.dollars
        breakdown(listOf(slice(groceries, 40.dollars)), listOf(weekly), BudgetPeriod.MONTHLY)
            .entries.single().cap.shouldBeNull()
    }

    "a zero cap reads as no progress rather than a divide by zero" {
        val result = breakdown(
            slices = listOf(slice(groceries, 40.dollars)),
            budgets = listOf(aBudget(categoryIds = listOf(groceries), amount = Money.zero())),
        )

        val entry = result.entries.single()
        entry.capFraction shouldBe 0f
        // Spending anything at all against a zero limit is still over it.
        entry.cap?.status shouldBe BudgetStatus.OVER
    }

    "a category the workspace no longer names still carries its spend" {
        val orphan = categoryId("c-deleted")

        val result = breakdown(listOf(slice(orphan, 40.dollars)))

        val entry = result.entries.single()
        entry.category.shouldBeNull()
        entry.spent shouldBe 40.dollars
    }
})
