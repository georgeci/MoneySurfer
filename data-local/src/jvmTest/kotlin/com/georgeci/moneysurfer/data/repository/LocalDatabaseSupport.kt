package com.georgeci.moneysurfer.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.data.db.entity.CategoryEntity
import com.georgeci.moneysurfer.data.db.entity.GoalEntity
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.domain.fixtures.TEST_EPOCH_MILLIS
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal const val TEST_OWNER_ID = "u-1"
internal const val TEST_WORKSPACE_ID = "ws-1"
internal const val TEST_CATEGORY_ID = "c-1"
internal const val TEST_GOAL_ID = "g-1"

/** Mirrors the on-disk JVM builder so behaviour matches production except for persistence. */
internal fun inMemoryLocalDatabase(): MoneySurferDatabase =
    Room.inMemoryDatabaseBuilder<MoneySurferDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

/**
 * The parent rows every workspace-scoped entity needs: foreign keys are enforced, so a budget or
 * a rule inserted without them fails on the constraint rather than on the code under test.
 */
internal suspend fun MoneySurferDatabase.seedWorkspaceRows() {
    userDao().insert(UserEntity(id = TEST_OWNER_ID, displayName = "Owner", isAnon = false))
    workspaceDao().insert(
        WorkspaceEntity(
            id = TEST_WORKSPACE_ID,
            name = "WS",
            description = "",
            baseCurrency = "USD",
            ownerId = TEST_OWNER_ID,
            createdAt = TEST_EPOCH_MILLIS,
            archived = false,
            updatedAt = TEST_EPOCH_MILLIS,
        ),
    )
    categoryDao().insert(
        CategoryEntity(
            id = TEST_CATEGORY_ID,
            workspaceId = TEST_WORKSPACE_ID,
            name = "Groceries",
            type = "EXPENSE",
            parentId = null,
            createdAt = TEST_EPOCH_MILLIS,
        ),
    )
}

/** A goal to hang contributions off — `goal_contributions.goalId` is a foreign key. */
internal suspend fun MoneySurferDatabase.seedGoalRow() {
    goalDao().insert(
        GoalEntity(
            id = TEST_GOAL_ID,
            workspaceId = TEST_WORKSPACE_ID,
            title = "New bike",
            emoji = "🚲",
            hue = 210,
            target = 100_000L,
            currencyCode = "USD",
            startDate = "2024-01-01",
            status = "ACTIVE",
            createdAt = TEST_EPOCH_MILLIS,
            updatedAt = TEST_EPOCH_MILLIS,
        ),
    )
}

/** What a repository handed the sync outbox, in order. */
internal data class OutboxRecord(
    val entityType: String,
    val entityId: String,
    val scopeKey: String?,
    val operation: MutationOperation?,
)

/**
 * Records instead of enqueuing. The push side is covered by the sync module's own specs; what
 * these tests care about is that a local write is *paired* with the right outbox entry, which is
 * the invariant a missed `enqueue*` call breaks silently.
 */
internal class RecordingOutbox : OutboxEnqueuer {

    val records: MutableList<OutboxRecord> = mutableListOf()

    override suspend fun enqueueUpsert(
        entityType: String,
        entityId: String,
        scopeKey: String?,
        operation: MutationOperation,
    ) {
        records += OutboxRecord(entityType, entityId, scopeKey, operation)
    }

    override suspend fun enqueueDelete(entityType: String, entityId: String, scopeKey: String?) {
        records += OutboxRecord(entityType, entityId, scopeKey, operation = null)
    }

    override suspend fun isEnabled(): Boolean = true
}
