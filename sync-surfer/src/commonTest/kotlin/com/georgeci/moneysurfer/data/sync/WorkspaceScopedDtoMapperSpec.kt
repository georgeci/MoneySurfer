package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.data.db.entity.BudgetEntity
import com.georgeci.moneysurfer.data.db.entity.CategoryEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.data.remote.BudgetDoc
import com.georgeci.moneysurfer.data.remote.CategoryDoc
import com.georgeci.moneysurfer.data.remote.WorkspaceDoc
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

private const val WORKSPACE = "ws-1"

private fun workspaceEntity() = WorkspaceEntity(
    id = WORKSPACE,
    name = "Household",
    description = "shared spending",
    baseCurrency = "EUR",
    ownerId = "u-1",
    createdAt = 1L,
    archived = false,
    updatedAt = 2L,
)

private fun categoryEntity() = CategoryEntity(
    id = "c-1",
    workspaceId = WORKSPACE,
    name = "Groceries",
    type = "EXPENSE",
    parentId = "c-parent",
    createdAt = 1L,
    updatedAt = 2L,
    systemKind = "TRANSFER",
    iconKey = "basket",
    hue = 120,
)

private fun budgetEntity(categoryIds: String) = BudgetEntity(
    id = "b-1",
    workspaceId = WORKSPACE,
    name = "Food",
    categoryIds = categoryIds,
    amount = 50_000L,
    period = "MONTHLY",
    startDate = "2025-03-01",
    alertPercent = 80,
    isActive = true,
    rollover = true,
    createdAt = 1L,
    updatedAt = 2L,
)

/**
 * Workspace, category and budget mappers. The workspace and category ones are field-for-field, so
 * what they are worth pinning for is the round trip staying total — a field added on one side and
 * forgotten on the other is exactly what silently stops replicating. The budget mapper does real
 * work: Room keeps its category list as a CSV column and the wire carries a list.
 */
class WorkspaceScopedDtoMapperSpec : StringSpec({

    "a workspace survives the round trip, id included" {
        workspaceEntity().toDoc().toEntity(id = WORKSPACE) shouldBe workspaceEntity()
    }

    // `deletedAt` is the tombstone marker the push path stamps separately, so a live row maps to
    // a doc that carries none — a non-null here would delete the row on every other device.
    "a live workspace maps to a doc with no tombstone" {
        workspaceEntity().toDoc().deletedAt shouldBe null
    }

    "a category survives the round trip, appearance and system marker included" {
        categoryEntity().toDoc().toEntity(id = "c-1", workspaceId = WORKSPACE) shouldBe
            categoryEntity()
    }

    "a category written before the appearance fields existed keeps its not-written sentinels" {
        val doc = CategoryDoc(name = "Legacy", type = "EXPENSE", createdAt = 1L, updatedAt = 2L)

        val entity = doc.toEntity(id = "c-1", workspaceId = WORKSPACE)

        entity.iconKey shouldBe ""
        entity.hue shouldBe -1
    }

    "a budget's CSV category column becomes a list on the wire and back again" {
        val entity = budgetEntity(categoryIds = "c-1,c-2,c-3")

        entity.toDoc().categoryIds shouldBe listOf("c-1", "c-2", "c-3")
        entity.toDoc().toEntity(id = "b-1", workspaceId = WORKSPACE) shouldBe entity
    }

    // An empty column means "every expense category", not "one category with a blank id".
    "an empty category column maps to an empty list rather than one blank id" {
        budgetEntity(categoryIds = "").toDoc().categoryIds shouldBe emptyList()
    }

    "a trailing comma left by an older client decodes to the ids that are actually there" {
        budgetEntity(categoryIds = "c-1,,c-2,").toDoc().categoryIds shouldBe listOf("c-1", "c-2")
    }

    "blank ids in a remote list are dropped instead of becoming empty CSV segments" {
        val doc = BudgetDoc(
            name = "Food",
            categoryIds = listOf("c-1", "", "c-2"),
            amount = 50_000L,
            period = "MONTHLY",
            startDate = "2025-03-01",
            alertPercent = 80,
            rollover = true,
            createdAt = 1L,
            updatedAt = 2L,
        )

        doc.toEntity(id = "b-1", workspaceId = WORKSPACE).categoryIds shouldBe "c-1,c-2"
    }

    "the rest of a budget survives the round trip" {
        val entity = budgetEntity(categoryIds = "c-1")

        entity.toDoc().toEntity(id = "b-1", workspaceId = WORKSPACE) shouldBe entity
    }

    "an inactive budget stays inactive across the wire" {
        val entity = budgetEntity(categoryIds = "c-1").copy(isActive = false)

        entity.toDoc().isActive shouldBe false
        entity.toDoc().toEntity(id = "b-1", workspaceId = WORKSPACE).isActive shouldBe false
    }

    "a workspace doc written by an older client still maps, defaults and all" {
        val doc = WorkspaceDoc(name = "Legacy", ownerId = "u-1")

        val entity = doc.toEntity(id = WORKSPACE)

        entity.baseCurrency shouldBe ""
        entity.archived shouldBe false
        entity.updatedAt shouldBe 0L
    }
})
