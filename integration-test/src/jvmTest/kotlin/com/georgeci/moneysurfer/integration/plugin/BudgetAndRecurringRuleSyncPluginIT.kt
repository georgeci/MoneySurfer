package com.georgeci.moneysurfer.integration.plugin

import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.data.db.entity.BudgetEntity
import com.georgeci.moneysurfer.data.db.entity.RecurringRuleEntity
import com.georgeci.moneysurfer.data.remote.BudgetDoc
import com.georgeci.moneysurfer.data.remote.RecurringRuleDoc
import com.georgeci.moneysurfer.data.sync.plugin.BudgetSyncPlugin
import com.georgeci.moneysurfer.data.sync.plugin.RecurringRuleSyncPlugin
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.integration.fixtures.inMemoryRoomDatabase
import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
import com.georgeci.moneysurfer.sync.repository.LwwConflictResolver
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private const val BUDGET = "b-1"
private const val RULE = "r-1"

private fun budgetEntity(updatedAt: Long) = BudgetEntity(
    id = BUDGET,
    workspaceId = PLUGIN_WORKSPACE_ID,
    name = "Local name",
    categoryIds = PLUGIN_CATEGORY_ID,
    amount = 50_000L,
    period = "MONTHLY",
    startDate = "2025-01-01",
    alertPercent = 80,
    isActive = true,
    createdAt = 1L,
    updatedAt = updatedAt,
)

private fun budgetDoc(updatedAt: Long = 200L, deletedAt: Long? = null) = BudgetDoc(
    name = "Remote name",
    categoryIds = listOf(PLUGIN_CATEGORY_ID),
    amount = 50_000L,
    period = "MONTHLY",
    startDate = "2025-01-01",
    alertPercent = 80,
    createdAt = 1L,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun ruleEntity(updatedAt: Long) = RecurringRuleEntity(
    id = RULE,
    workspaceId = PLUGIN_WORKSPACE_ID,
    title = "Local title",
    amount = -10_00L,
    categoryId = PLUGIN_CATEGORY_ID,
    scheduleFrequency = "MONTHLY",
    scheduleInterval = 1,
    scheduleDaysOfWeek = "",
    scheduleDaysOfMonth = "1",
    scheduleMissingDayPolicy = "LAST_DAY_OF_MONTH",
    startDate = "2025-01-01",
    nextRunAt = null,
    autoCreate = true,
    isActive = true,
    createdAt = 1L,
    updatedAt = updatedAt,
)

private fun ruleDoc(updatedAt: Long = 200L, deletedAt: Long? = null) = RecurringRuleDoc(
    title = "Remote title",
    amount = -10_00L,
    categoryId = PLUGIN_CATEGORY_ID,
    scheduleFrequency = "MONTHLY",
    scheduleDaysOfMonth = listOf(1),
    scheduleMissingDayPolicy = "LAST_DAY_OF_MONTH",
    startDate = "2025-01-01",
    createdAt = 1L,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

/**
 * The two plainest workspace sub-collection plugins — no cascade, no soft delete, no FK to a
 * sibling entity. Worth covering anyway: they are what the shared push helper and the shared
 * last-writer-wins path look like with nothing else on top, so a regression in either shows up
 * here first.
 */
class BudgetAndRecurringRuleSyncPluginIT : StringSpec({

    lateinit var database: MoneySurferDatabase
    lateinit var writer: RecordingDocumentWriter
    lateinit var budgets: BudgetSyncPlugin
    lateinit var rules: RecurringRuleSyncPlugin

    beforeEach {
        database = inMemoryRoomDatabase()
        writer = RecordingDocumentWriter()
        budgets = BudgetSyncPlugin(
            writer = writer,
            appInfo = pluginAppInfo(),
            conflictResolver = LwwConflictResolver(),
            budgetDao = database.budgetDao(),
        )
        rules = RecurringRuleSyncPlugin(
            writer = writer,
            appInfo = pluginAppInfo(),
            conflictResolver = LwwConflictResolver(),
            recurringRuleDao = database.recurringRuleDao(),
        )
        database.seedPluginWorkspace()
    }

    afterEach { database.close() }

    "a budget is pushed to the workspace's budgets collection" {
        database.budgetDao().insert(budgetEntity(updatedAt = 100L))

        budgets.push(mutationOf(SyncEntityTypes.BUDGET, BUDGET, MutationOperation.INSERT))

        writer.writes.single().path shouldBe "workspaces/$PLUGIN_WORKSPACE_ID/budgets/$BUDGET"
        writer.onlyWrite<BudgetDoc>().name shouldBe "Local name"
    }

    "a budget delete writes a tombstone once the document exists" {
        database.budgetDao().insert(budgetEntity(updatedAt = 100L))
        budgets.push(mutationOf(SyncEntityTypes.BUDGET, BUDGET, MutationOperation.INSERT))

        budgets.push(mutationOf(SyncEntityTypes.BUDGET, BUDGET, MutationOperation.DELETE))

        writer.tombstones.single().path shouldBe "workspaces/$PLUGIN_WORKSPACE_ID/budgets/$BUDGET"
    }

    "a pulled budget is inserted, and a newer local edit is kept" {
        budgets.applyDoc(StubRemoteDocument(BUDGET, budgetDoc()), PLUGIN_WORKSPACE_ID) shouldBe
            EntityApplyResult(applied = true, wasConflict = false)
        database.budgetDao().getById(BUDGET)?.name shouldBe "Remote name"

        database.budgetDao().update(budgetEntity(updatedAt = 300L))

        budgets.applyDoc(
            StubRemoteDocument(BUDGET, budgetDoc(updatedAt = 250L)),
            PLUGIN_WORKSPACE_ID,
        ) shouldBe EntityApplyResult(applied = false, wasConflict = true)
        database.budgetDao().getById(BUDGET)?.name shouldBe "Local name"
    }

    "a budget tombstone drops the row" {
        database.budgetDao().insert(budgetEntity(updatedAt = 100L))

        budgets.applyDoc(
            StubRemoteDocument(BUDGET, budgetDoc(deletedAt = 400L)),
            PLUGIN_WORKSPACE_ID,
        ) shouldBe EntityApplyResult(applied = true, wasConflict = false)
        database.budgetDao().getById(BUDGET).shouldBeNull()
    }

    "a rule is pushed to the workspace's recurringRules collection" {
        database.recurringRuleDao().insert(ruleEntity(updatedAt = 100L))

        rules.push(mutationOf(SyncEntityTypes.RECURRING_RULE, RULE, MutationOperation.INSERT))

        writer.writes.single().path shouldBe
            "workspaces/$PLUGIN_WORKSPACE_ID/recurringRules/$RULE"
        writer.onlyWrite<RecurringRuleDoc>().title shouldBe "Local title"
    }

    "an upsert for a rule that is already gone writes nothing" {
        rules.push(mutationOf(SyncEntityTypes.RECURRING_RULE, RULE, MutationOperation.UPDATE))

        writer.writes.shouldBeEmpty()
    }

    // The rule has to replicate because a transaction's `recurringRuleId` points at it — on a
    // device that never created it the link would dangle and the cadence could not be rendered.
    "a pulled rule is inserted with its schedule" {
        rules.applyDoc(StubRemoteDocument(RULE, ruleDoc()), PLUGIN_WORKSPACE_ID) shouldBe
            EntityApplyResult(applied = true, wasConflict = false)

        val stored = database.recurringRuleDao().getById(RULE)!!
        stored.title shouldBe "Remote title"
        stored.scheduleDaysOfMonth shouldBe "1"
    }

    "a rule tombstone drops the row" {
        database.recurringRuleDao().insert(ruleEntity(updatedAt = 100L))

        rules.applyDoc(
            StubRemoteDocument(RULE, ruleDoc(deletedAt = 400L)),
            PLUGIN_WORKSPACE_ID,
        ) shouldBe EntityApplyResult(applied = true, wasConflict = false)
        database.recurringRuleDao().getById(RULE).shouldBeNull()
    }
})
