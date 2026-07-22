package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.YearMonth

private val Window = listOf(
    YearMonth(2025, 11),
    YearMonth(2025, 12),
    YearMonth(2026, 1),
    YearMonth(2026, 2),
    YearMonth(2026, 3),
    YearMonth(2026, 4),
)

private fun total(
    id: CategoryId,
    month: YearMonth,
    minor: Long,
    count: Int = 1,
) = CategoryMonthlyTotal(
    categoryId = id,
    month = month,
    total = Money.fromMinor(minor),
    transactionCount = count,
)

class CategorySpendHistoryTest : StringSpec({

    "trailingMonths ends at the anchor and runs oldest first" {
        trailingMonths(anchor = YearMonth(2026, 4), count = 6) shouldBe Window
    }

    "trailingMonths crosses a year boundary backwards" {
        trailingMonths(anchor = YearMonth(2026, 1), count = 3) shouldBe listOf(
            YearMonth(2025, 11),
            YearMonth(2025, 12),
            YearMonth(2026, 1),
        )
    }

    "every month in the window is emitted, including the ones with no spend" {
        val food = aCategory(id = categoryId("food"))

        val history = buildCategorySpendHistory(
            categories = listOf(food),
            rootId = food.id,
            totals = listOf(total(food.id, YearMonth(2026, 4), 12_00)),
            months = Window,
        )

        // The query only returns months that booked something — a six-month chart that shrank to
        // one bar would be a lie about the window it is labelled with.
        history.months.map { it.month } shouldBe Window
        history.months.map { it.total.minor } shouldBe listOf(0L, 0L, 0L, 0L, 0L, 1200L)
    }

    "a parent's total rolls up its whole subtree" {
        val food = aCategory(id = categoryId("food"))
        val groceries = aCategory(id = categoryId("groceries"), parentId = food.id)
        val fresh = aCategory(id = categoryId("fresh"), parentId = groceries.id)
        val april = YearMonth(2026, 4)

        val history = buildCategorySpendHistory(
            categories = listOf(food, groceries, fresh),
            rootId = food.id,
            totals = listOf(
                total(food.id, april, 10_00, count = 1),
                total(groceries.id, april, 20_00, count = 2),
                total(fresh.id, april, 5_00, count = 3),
            ),
            months = Window,
        )

        history.currentMonth shouldBe Money.fromMinor(35_00)
        history.transactionCount shouldBe 6
    }

    "a grandchild's spend lands on the direct child's breakdown row, not on its own" {
        val food = aCategory(id = categoryId("food"))
        val groceries = aCategory(id = categoryId("groceries"), parentId = food.id)
        val fresh = aCategory(id = categoryId("fresh"), parentId = groceries.id)
        val april = YearMonth(2026, 4)

        val history = buildCategorySpendHistory(
            categories = listOf(food, groceries, fresh),
            rootId = food.id,
            totals = listOf(
                total(groceries.id, april, 20_00),
                total(fresh.id, april, 5_00),
            ),
            months = Window,
        )

        history.subcategories.map { it.categoryId } shouldBe listOf(groceries.id)
        history.subcategories.single().total shouldBe Money.fromMinor(25_00)
    }

    "a leaf category reports no subcategories" {
        val coffee = aCategory(id = categoryId("coffee"))

        val history = buildCategorySpendHistory(
            categories = listOf(coffee),
            rootId = coffee.id,
            totals = listOf(total(coffee.id, YearMonth(2026, 4), 9_00)),
            months = Window,
        )

        history.subcategories.shouldBeEmpty()
    }

    "totals for categories outside the subtree are ignored" {
        val food = aCategory(id = categoryId("food"))
        val transport = aCategory(id = categoryId("transport"))
        val april = YearMonth(2026, 4)

        val history = buildCategorySpendHistory(
            categories = listOf(food, transport),
            rootId = food.id,
            totals = listOf(
                total(food.id, april, 10_00),
                total(transport.id, april, 99_00),
            ),
            months = Window,
        )

        history.currentMonth shouldBe Money.fromMinor(10_00)
    }

    "totals outside the requested window do not reach the trend" {
        val food = aCategory(id = categoryId("food"))

        val history = buildCategorySpendHistory(
            categories = listOf(food),
            rootId = food.id,
            totals = listOf(
                total(food.id, YearMonth(2026, 4), 10_00),
                total(food.id, YearMonth(2025, 6), 99_00),
            ),
            months = Window,
        )

        history.months.sumOf { it.total.minor } shouldBe 1000L
    }

    "the monthly average divides by the whole window, not by the months with spend" {
        val food = aCategory(id = categoryId("food"))

        val history = buildCategorySpendHistory(
            categories = listOf(food),
            rootId = food.id,
            totals = listOf(total(food.id, YearMonth(2026, 4), 60_00)),
            months = Window,
        )

        // 6000 minor over six months. Averaging over the single month that had spend would
        // report 60.00 and make a one-off purchase look like a monthly habit.
        history.monthlyAverage shouldBe Money.fromMinor(10_00)
    }

    "per-transaction is null when nothing was booked this month" {
        val food = aCategory(id = categoryId("food"))

        val history = buildCategorySpendHistory(
            categories = listOf(food),
            rootId = food.id,
            totals = emptyList(),
            months = Window,
        )

        history.perTransaction shouldBe null
        history.currentMonth shouldBe Money.zero()
    }

    "a category that is not in the list yields an empty trend rather than throwing" {
        val history = buildCategorySpendHistory(
            categories = emptyList(),
            rootId = categoryId("ghost"),
            totals = emptyList(),
            months = Window,
        )

        history.months.map { it.total.minor } shouldBe List(Window.size) { 0L }
        history.subcategories.shouldBeEmpty()
    }
})
