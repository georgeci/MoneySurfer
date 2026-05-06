package com.georgeci.moneysurfer.sync.internal.repository

import com.georgeci.moneysurfer.sync.db.dao.SyncMetaDao
import com.georgeci.moneysurfer.sync.db.entity.SyncMetaEntity
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant

private class FakeSyncMetaDao : SyncMetaDao {
    private val rows = mutableMapOf<Pair<String, String>, SyncMetaEntity>()
    private val byWorkspace = mutableMapOf<String, MutableStateFlow<List<SyncMetaEntity>>>()
    var deleteAllCalls: Int = 0

    override suspend fun get(workspaceId: String, collection: String): SyncMetaEntity? =
        rows[workspaceId to collection]

    override suspend fun upsert(entity: SyncMetaEntity) {
        rows[entity.workspaceId to entity.collection] = entity
        notify(entity.workspaceId)
    }

    override fun observeByWorkspace(workspaceId: String): Flow<List<SyncMetaEntity>> =
        byWorkspace.getOrPut(workspaceId) { MutableStateFlow(currentRowsFor(workspaceId)) }

    override suspend fun deleteByWorkspace(workspaceId: String) {
        rows.keys.filter { it.first == workspaceId }.forEach { rows.remove(it) }
        notify(workspaceId)
    }

    override suspend fun deleteAll() {
        deleteAllCalls += 1
        rows.clear()
        byWorkspace.keys.toList().forEach { notify(it) }
    }

    private fun currentRowsFor(workspaceId: String): List<SyncMetaEntity> =
        rows.values.filter { it.workspaceId == workspaceId }

    private fun notify(workspaceId: String) {
        byWorkspace[workspaceId]?.value = currentRowsFor(workspaceId)
    }
}

class SyncMetaRepositoryImplSpec : StringSpec({

    val cursorAt = Instant.fromEpochMilliseconds(1_700_000_000_000L)
    val attemptAt = Instant.fromEpochMilliseconds(1_700_000_005_000L)
    val successAt = Instant.fromEpochMilliseconds(1_700_000_010_000L)

    "cursor returns null when no row exists" {
        runTest {
            val repo = SyncMetaRepositoryImpl(FakeSyncMetaDao())
            repo.cursor("ws-1", "transactions") shouldBe null
        }
    }

    "setCursor inserts a fresh row when none exists" {
        runTest {
            val dao = FakeSyncMetaDao()
            val repo = SyncMetaRepositoryImpl(dao)

            repo.setCursor("ws-1", "transactions", cursorAt)

            repo.cursor("ws-1", "transactions") shouldBe cursorAt
            dao.get("ws-1", "transactions") shouldBe SyncMetaEntity(
                workspaceId = "ws-1",
                collection = "transactions",
                lastPulledAt = cursorAt.toEpochMilliseconds(),
                lastSyncSuccessAt = null,
                lastSyncAttemptAt = null,
            )
        }
    }

    "setCursor preserves attempt and success timestamps on existing row" {
        runTest {
            val dao = FakeSyncMetaDao()
            val repo = SyncMetaRepositoryImpl(dao)

            repo.markAttempt("ws-1", "accounts", attemptAt)
            repo.markSuccess("ws-1", "accounts", successAt)
            repo.setCursor("ws-1", "accounts", cursorAt)

            val row = dao.get("ws-1", "accounts")!!
            row.lastPulledAt shouldBe cursorAt.toEpochMilliseconds()
            row.lastSyncAttemptAt shouldBe attemptAt.toEpochMilliseconds()
            row.lastSyncSuccessAt shouldBe successAt.toEpochMilliseconds()
        }
    }

    "markAttempt only updates the attempt column" {
        runTest {
            val dao = FakeSyncMetaDao()
            val repo = SyncMetaRepositoryImpl(dao)
            repo.setCursor("ws-1", "accounts", cursorAt)

            repo.markAttempt("ws-1", "accounts", attemptAt)

            val row = dao.get("ws-1", "accounts")!!
            row.lastPulledAt shouldBe cursorAt.toEpochMilliseconds()
            row.lastSyncAttemptAt shouldBe attemptAt.toEpochMilliseconds()
            row.lastSyncSuccessAt shouldBe null
        }
    }

    "markSuccess only updates the success column" {
        runTest {
            val dao = FakeSyncMetaDao()
            val repo = SyncMetaRepositoryImpl(dao)

            repo.markSuccess("ws-1", "accounts", successAt)

            val row = dao.get("ws-1", "accounts")!!
            row.lastSyncSuccessAt shouldBe successAt.toEpochMilliseconds()
            row.lastSyncAttemptAt shouldBe null
            row.lastPulledAt shouldBe null
        }
    }

    "clearScope removes only rows for the requested workspace" {
        runTest {
            val dao = FakeSyncMetaDao()
            val repo = SyncMetaRepositoryImpl(dao)
            repo.setCursor("ws-1", "accounts", cursorAt)
            repo.setCursor("ws-2", "accounts", cursorAt)

            repo.clearScope("ws-1")

            repo.cursor("ws-1", "accounts") shouldBe null
            repo.cursor("ws-2", "accounts") shouldBe cursorAt
        }
    }

    "observe maps rows to domain SyncMeta" {
        runTest {
            val dao = FakeSyncMetaDao()
            val repo = SyncMetaRepositoryImpl(dao)
            repo.setCursor("ws-1", "accounts", cursorAt)
            repo.markSuccess("ws-1", "transactions", successAt)

            val rows = repo.observe("ws-1").first()

            rows.map { it.collection }.toSet() shouldBe setOf("accounts", "transactions")
            val accounts = rows.first { it.collection == "accounts" }
            accounts.scopeKey shouldBe "ws-1"
            accounts.lastPulledAt shouldBe cursorAt
            accounts.lastSyncSuccessAt shouldBe null
            val transactions = rows.first { it.collection == "transactions" }
            transactions.lastPulledAt shouldBe null
            transactions.lastSyncSuccessAt shouldBe successAt
        }
    }

    "observe emits empty list for a fresh workspace" {
        runTest {
            val dao = FakeSyncMetaDao()
            val repo = SyncMetaRepositoryImpl(dao)
            repo.observe("never-touched").first() shouldContainExactly emptyList()
        }
    }
})
