package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.primitives.CategorySystemKind
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CategoryTreeTest : StringSpec({

    "flatten puts each child immediately after its parent" {
        val food = aCategory(id = categoryId("food"), name = "Food")
        val groceries = aCategory(id = categoryId("groceries"), name = "Groceries", parentId = food.id)
        val transport = aCategory(id = categoryId("transport"), name = "Transport")

        CategoryTree.flatten(listOf(groceries, transport, food))
            .map { it.category.name to it.depth } shouldBe listOf(
            "Transport" to 0,
            "Food" to 0,
            "Groceries" to 1,
        )
    }

    // Sync can deliver a child before its parent. Dropping it would make the category vanish.
    "a child whose parent is absent is shown as a root rather than hidden" {
        val orphan = aCategory(id = categoryId("orphan"), name = "Orphan", parentId = categoryId("gone"))

        CategoryTree.flatten(listOf(orphan))
            .map { it.category.name to it.depth } shouldBe listOf("Orphan" to 0)
    }

    "descendantsOf walks the whole subtree" {
        val a = aCategory(id = categoryId("a"))
        val b = aCategory(id = categoryId("b"), parentId = a.id)
        val c = aCategory(id = categoryId("c"), parentId = b.id)
        val d = aCategory(id = categoryId("d"))

        CategoryTree.descendantsOf(listOf(a, b, c, d), a.id) shouldBe setOf(b.id, c.id)
    }

    // Only the UI refuses to create a cycle; a pull from another client can still deliver one.
    "descendantsOf terminates on a cycle instead of looping forever" {
        val a = aCategory(id = categoryId("a"), parentId = categoryId("b"))
        val b = aCategory(id = categoryId("b"), parentId = categoryId("a"))

        CategoryTree.descendantsOf(listOf(a, b), a.id) shouldBe setOf(a.id, b.id)
    }

    "eligibleParents excludes the category itself and everything under it" {
        val root = aCategory(id = categoryId("root"))
        val child = aCategory(id = categoryId("child"), parentId = root.id)
        val other = aCategory(id = categoryId("other"))

        CategoryTree.eligibleParents(listOf(root, child, other), root.id, CategoryType.EXPENSE)
            .map { it.id } shouldBe listOf(other.id)
    }

    "eligibleParents excludes the other type and system categories" {
        val expense = aCategory(id = categoryId("expense"), type = CategoryType.EXPENSE)
        val income = aCategory(id = categoryId("income"), type = CategoryType.INCOME)
        val transfer = aCategory(id = categoryId("transfer"), type = CategoryType.EXPENSE)
            .copy(systemKind = CategorySystemKind.TRANSFER)

        CategoryTree.eligibleParents(listOf(expense, income, transfer), null, CategoryType.EXPENSE)
            .map { it.id } shouldBe listOf(expense.id)
    }

    "eligibleParents refuses a category that is already a child — the tree stays one level deep" {
        val root = aCategory(id = categoryId("root"))
        val child = aCategory(id = categoryId("child"), parentId = root.id)

        CategoryTree.eligibleParents(listOf(root, child), null, CategoryType.EXPENSE)
            .map { it.id } shouldBe listOf(root.id)
    }
})
