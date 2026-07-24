package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.fixtures.aBudget
import com.georgeci.moneysurfer.domain.fixtures.budgetId
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.fixtures.testInstant
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlin.time.Duration.Companion.hours

/**
 * The rule the category form's monthly-cap shortcut runs on: which budget it speaks for, and when
 * it must step aside rather than add a second limit to a category that already has one.
 */
class CategoryCapCoverageTest : StringSpec({

    val food = categoryId("c-food")

    "a category that does not exist yet is never covered" {
        val coverage = resolveCategoryCapCoverage(
            categoryId = null,
            budgets = listOf(aBudget(categoryIds = listOf(food))),
        )

        coverage shouldBe CategoryCapCoverage.Editable(budget = null)
    }

    "no budgets at all leaves the control editable with nothing behind it" {
        resolveCategoryCapCoverage(food, budgets = emptyList()) shouldBe
            CategoryCapCoverage.Editable(budget = null)
    }

    "a budget naming only this category becomes the backing budget" {
        val backing = aBudget(categoryIds = listOf(food))

        resolveCategoryCapCoverage(food, budgets = listOf(backing)) shouldBe
            CategoryCapCoverage.Editable(budget = backing)
    }

    "a budget naming other categories too takes the control read-only" {
        val shared = aBudget(name = "Living", categoryIds = listOf(food, categoryId("c-rent")))

        val coverage = resolveCategoryCapCoverage(food, budgets = listOf(shared))

        coverage.shouldBeInstanceOf<CategoryCapCoverage.Managed>().budget shouldBe shared
    }

    "budgets that do not name this category are ignored" {
        val other = aBudget(categoryIds = listOf(categoryId("c-rent")))

        resolveCategoryCapCoverage(food, budgets = listOf(other)) shouldBe
            CategoryCapCoverage.Editable(budget = null)
    }

    // The all-categories budget is the global envelope per-category budgets already live inside.
    // Counting it as coverage would leave the shortcut permanently dead for anyone who keeps one.
    "the all-categories budget is not coverage" {
        val envelope = aBudget(name = "Everything", categoryIds = emptyList())

        resolveCategoryCapCoverage(food, budgets = listOf(envelope)) shouldBe
            CategoryCapCoverage.Editable(budget = null)
    }

    "an archived budget limits nothing, so a cap set now is a fresh budget" {
        val archived = aBudget(categoryIds = listOf(food), isActive = false)

        resolveCategoryCapCoverage(food, budgets = listOf(archived)) shouldBe
            CategoryCapCoverage.Editable(budget = null)
    }

    "two budgets on one category take the control read-only, naming the older one" {
        val older = aBudget(
            id = budgetId("b-older"),
            name = "Older",
            categoryIds = listOf(food),
            createdAt = testInstant,
        )
        val newer = aBudget(
            id = budgetId("b-newer"),
            name = "Newer",
            categoryIds = listOf(food),
            createdAt = testInstant + 1.hours,
        )

        val coverage = resolveCategoryCapCoverage(food, budgets = listOf(newer, older))

        coverage.shouldBeInstanceOf<CategoryCapCoverage.Managed>().budget shouldBe older
    }

    "an archived duplicate does not push the control read-only" {
        val active = aBudget(id = budgetId("b-active"), categoryIds = listOf(food))
        val archived = aBudget(id = budgetId("b-archived"), categoryIds = listOf(food), isActive = false)

        resolveCategoryCapCoverage(food, budgets = listOf(active, archived)) shouldBe
            CategoryCapCoverage.Editable(budget = active)
    }
})
