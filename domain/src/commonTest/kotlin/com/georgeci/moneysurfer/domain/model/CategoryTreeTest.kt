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

    // The UI will not build a three-level tree, but another client's write can deliver one.
    // Rendering it flat is fine; dropping the grandchild from the manage screen is not.
    "a subtree deeper than the indent cap is still rendered, just not indented further" {
        val root = aCategory(id = categoryId("root"), name = "Food")
        val child = aCategory(id = categoryId("child"), name = "Groceries", parentId = root.id)
        val grandchild = aCategory(id = categoryId("grand"), name = "Produce", parentId = child.id)

        CategoryTree.flatten(listOf(root, child, grandchild))
            .map { it.category.name to it.depth } shouldBe listOf(
            "Food" to 0,
            "Groceries" to 1,
            "Produce" to CategoryTree.MAX_DEPTH,
        )
    }

    "a parent cycle is shown at the top level rather than vanishing" {
        // Neither is a root, so a roots-only walk would emit nothing at all.
        val a = aCategory(id = categoryId("a"), name = "A", parentId = categoryId("b"))
        val b = aCategory(id = categoryId("b"), name = "B", parentId = categoryId("a"))

        CategoryTree.flatten(listOf(a, b)).map { it.category.name } shouldBe listOf("A", "B")
    }

    "every input category comes out exactly once, whatever shape it is in" {
        val root = aCategory(id = categoryId("root"))
        val child = aCategory(id = categoryId("child"), parentId = root.id)
        val grandchild = aCategory(id = categoryId("grand"), parentId = child.id)
        val orphan = aCategory(id = categoryId("orphan"), parentId = categoryId("gone"))
        val cycleA = aCategory(id = categoryId("cycle-a"), parentId = categoryId("cycle-b"))
        val cycleB = aCategory(id = categoryId("cycle-b"), parentId = categoryId("cycle-a"))
        val input = listOf(root, child, grandchild, orphan, cycleA, cycleB)

        val flattened = CategoryTree.flatten(input)

        flattened.map { it.category.id }.toSet() shouldBe input.map { it.id }.toSet()
        flattened.size shouldBe input.size
    }

    "descendantsOf walks the whole subtree" {
        val a = aCategory(id = categoryId("a"))
        val b = aCategory(id = categoryId("b"), parentId = a.id)
        val c = aCategory(id = categoryId("c"), parentId = b.id)
        val d = aCategory(id = categoryId("d"))

        CategoryTree.descendantsOf(listOf(a, b, c, d), a.id) shouldBe setOf(b.id, c.id)
    }

    // Only the UI refuses to create a cycle; a pull from another client can still deliver one.
    // The root comes back in the result here because it genuinely is reachable from itself —
    // which is what keeps a cycle member from being offered as its own parent.
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
