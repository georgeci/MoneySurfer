package com.georgeci.moneysurfer.integration.plugin

import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.data.db.entity.GoalContributionEntity
import com.georgeci.moneysurfer.data.db.entity.GoalEntity
import com.georgeci.moneysurfer.data.remote.GoalContributionDoc
import com.georgeci.moneysurfer.data.remote.GoalDoc
import com.georgeci.moneysurfer.data.sync.plugin.GoalContributionSyncPlugin
import com.georgeci.moneysurfer.data.sync.plugin.SavingsGoalSyncPlugin
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.integration.fixtures.inMemoryRoomDatabase
import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
import com.georgeci.moneysurfer.sync.repository.LwwConflictResolver
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private const val GOAL = "g-1"
private const val CONTRIBUTION = "gc-1"

private fun goalEntity(updatedAt: Long, accountId: String? = null) = GoalEntity(
    id = GOAL,
    workspaceId = PLUGIN_WORKSPACE_ID,
    title = "Local title",
    emoji = "🚲",
    hue = 210,
    target = 100_000L,
    currencyCode = "USD",
    startDate = "2025-01-01",
    accountId = accountId,
    status = "ACTIVE",
    createdAt = 1L,
    updatedAt = updatedAt,
)

private fun goalDoc(
    updatedAt: Long = 200L,
    deletedAt: Long? = null,
    accountId: String? = null,
) = GoalDoc(
    title = "Remote title",
    emoji = "🚲",
    hue = 210,
    target = 100_000L,
    currencyCode = "USD",
    startDate = "2025-01-01",
    accountId = accountId,
    createdAt = 1L,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun contributionDoc(goalId: String = GOAL, updatedAt: Long = 200L) = GoalContributionDoc(
    goalId = goalId,
    amount = 10_000L,
    occurredOn = "2025-02-01",
    note = "Remote note",
    createdAt = 1L,
    updatedAt = updatedAt,
)

/**
 * Goals and their contributions, whose two interesting rules both exist to keep one bad document
 * from wedging the pull: a goal pointing at an account this device does not have, and a
 * contribution arriving before (or after) the goal it belongs to. Either would fail a hard Room
 * foreign key and abort the batch *before* the cursor advanced, so every later pull would refetch
 * the same document and fail again.
 */
class GoalSyncPluginIT : StringSpec({

    lateinit var database: MoneySurferDatabase
    lateinit var writer: RecordingDocumentWriter
    lateinit var goals: SavingsGoalSyncPlugin
    lateinit var contributions: GoalContributionSyncPlugin

    beforeEach {
        database = inMemoryRoomDatabase()
        writer = RecordingDocumentWriter()
        goals = SavingsGoalSyncPlugin(
            writer = writer,
            appInfo = pluginAppInfo(),
            conflictResolver = LwwConflictResolver(),
            goalDao = database.goalDao(),
            accountDao = database.accountDao(),
        )
        contributions = GoalContributionSyncPlugin(
            writer = writer,
            appInfo = pluginAppInfo(),
            conflictResolver = LwwConflictResolver(),
            contributionDao = database.goalContributionDao(),
            goalDao = database.goalDao(),
        )
        database.seedPluginWorkspace()
    }

    afterEach { database.close() }

    "a goal is pushed to the workspace's goals collection" {
        database.goalDao().insert(goalEntity(updatedAt = 100L))

        goals.push(mutationOf(SyncEntityTypes.GOAL, GOAL, MutationOperation.INSERT))

        writer.writes.single().path shouldBe "workspaces/$PLUGIN_WORKSPACE_ID/goals/$GOAL"
        writer.onlyWrite<GoalDoc>().title shouldBe "Local title"
    }

    "a pulled goal this device has never seen is inserted" {
        goals.applyDoc(StubRemoteDocument(GOAL, goalDoc()), PLUGIN_WORKSPACE_ID) shouldBe
            EntityApplyResult(applied = true, wasConflict = false)

        database.goalDao().getById(GOAL)?.title shouldBe "Remote title"
    }

    // The account pointer is decorative but the SQLite FK is not: keeping it would fail the insert
    // and wedge the pull, so the goal lands without it.
    "a goal pointing at an account this device does not have keeps everything but the pointer" {
        goals.applyDoc(
            StubRemoteDocument(GOAL, goalDoc(accountId = "acc-never-pulled")),
            PLUGIN_WORKSPACE_ID,
        ) shouldBe EntityApplyResult(applied = true, wasConflict = false)

        val stored = database.goalDao().getById(GOAL)!!
        stored.title shouldBe "Remote title"
        stored.accountId.shouldBeNull()
    }

    "a goal tombstone drops the row" {
        database.goalDao().insert(goalEntity(updatedAt = 100L))

        goals.applyDoc(
            StubRemoteDocument(GOAL, goalDoc(deletedAt = 400L)),
            PLUGIN_WORKSPACE_ID,
        ) shouldBe EntityApplyResult(applied = true, wasConflict = false)
        database.goalDao().getById(GOAL).shouldBeNull()
    }

    "a contribution is pushed to the workspace's goalContributions collection" {
        database.goalDao().insert(goalEntity(updatedAt = 100L))
        database.goalContributionDao().insert(
            GoalContributionEntity(
                id = CONTRIBUTION,
                workspaceId = PLUGIN_WORKSPACE_ID,
                goalId = GOAL,
                amount = 10_000L,
                occurredOn = "2025-02-01",
                note = "Local note",
                createdAt = 1L,
                updatedAt = 100L,
            ),
        )

        contributions.push(
            mutationOf(SyncEntityTypes.GOAL_CONTRIBUTION, CONTRIBUTION, MutationOperation.INSERT),
        )

        writer.writes.single().path shouldBe
            "workspaces/$PLUGIN_WORKSPACE_ID/goalContributions/$CONTRIBUTION"
        writer.onlyWrite<GoalContributionDoc>().note shouldBe "Local note"
    }

    "a contribution whose goal is here is applied" {
        database.goalDao().insert(goalEntity(updatedAt = 100L))

        contributions.applyDoc(
            StubRemoteDocument(CONTRIBUTION, contributionDoc()),
            PLUGIN_WORKSPACE_ID,
        ) shouldBe EntityApplyResult(applied = true, wasConflict = false)

        database.goalContributionDao().totalByGoalId(GOAL) shouldBe 10_000L
    }

    // Not a conflict — there is simply no goal for it to belong to, and the cursor has to advance
    // past it rather than retrying an insert that can only fail.
    "a contribution whose goal is not here is skipped rather than failing the batch" {
        contributions.applyDoc(
            StubRemoteDocument(CONTRIBUTION, contributionDoc(goalId = "g-never-pulled")),
            PLUGIN_WORKSPACE_ID,
        ) shouldBe EntityApplyResult(applied = false, wasConflict = false)

        database.goalContributionDao().getById(CONTRIBUTION).shouldBeNull()
    }
})
