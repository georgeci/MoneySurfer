package com.georgeci.moneysurfer.feature.category.picker

import com.georgeci.moneysurfer.domain.fixtures.aCategory
import com.georgeci.moneysurfer.domain.fixtures.categoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CategoryPickerRowsTest : StringSpec({

    "a pinned filter type wins over the selected category type" {
        val selected = aCategory(
            id = categoryId("salary"),
            type = CategoryType.INCOME,
        )

        CategoryPickerRows.initialType(
            categories = listOf(selected),
            selectedId = selected.id,
            filterType = CategoryType.TRANSFER,
        ) shouldBe CategoryType.TRANSFER
    }

    "the selected category type is used when the caller did not pin one" {
        val selected = aCategory(
            id = categoryId("salary"),
            type = CategoryType.INCOME,
        )

        CategoryPickerRows.initialType(listOf(selected), selected.id, null) shouldBe
            CategoryType.INCOME
    }

    "expense is the safe default when the selection is absent or stale" {
        CategoryPickerRows.initialType(emptyList(), categoryId("missing"), null) shouldBe
            CategoryType.EXPENSE
    }

    "a selected child expands exactly its parent" {
        val parent = aCategory(id = categoryId("food"))
        val child = aCategory(id = categoryId("groceries"), parentId = parent.id)

        CategoryPickerRows.expandedForSelection(listOf(parent, child), child.id) shouldBe
            setOf(parent.id)
        CategoryPickerRows.expandedForSelection(listOf(parent, child), parent.id) shouldBe
            emptySet()
    }

    "grid search trims whitespace and ignores case" {
        val categories = listOf(
            aCategory(id = categoryId("food"), name = "Groceries"),
            aCategory(id = categoryId("travel"), name = "Travel"),
        )

        val rows = CategoryPickerRows.grid(
            categories = categories,
            type = CategoryType.EXPENSE,
            query = "  GRO  ",
            selectedId = categoryId("food"),
        )

        rows.map { it.name } shouldBe listOf("Groceries")
        rows.single().selected shouldBe true
    }

    "collapsed tree parents hide children but retain the complete child count" {
        val parent = aCategory(id = categoryId("food"), name = "Food")
        val children = listOf(
            aCategory(id = categoryId("groceries"), parentId = parent.id),
            aCategory(id = categoryId("dining"), parentId = parent.id),
        )

        val group = CategoryPickerRows.tree(
            categories = listOf(parent) + children,
            type = CategoryType.EXPENSE,
            query = "",
            selectedId = null,
            expandedIds = emptySet(),
        ).single()

        group.expanded shouldBe false
        group.children shouldBe emptyList()
        group.childCount shouldBe 2
    }

    "searching for a parent does not expose unrelated children" {
        val parent = aCategory(id = categoryId("food"), name = "Food")
        val child = aCategory(
            id = categoryId("groceries"),
            name = "Groceries",
            parentId = parent.id,
        )

        val group = CategoryPickerRows.tree(
            categories = listOf(parent, child),
            type = CategoryType.EXPENSE,
            query = "food",
            selectedId = null,
            expandedIds = setOf(parent.id),
        ).single()

        group.parent.name shouldBe "Food"
        group.expanded shouldBe false
        group.children shouldBe emptyList()
        group.childCount shouldBe 1
    }
})
