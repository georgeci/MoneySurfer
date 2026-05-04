package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.sync.db.dao.SyncMetaDao
import com.georgeci.moneysurfer.sync.db.entity.SyncMetaEntity
import com.georgeci.moneysurfer.sync.internal.repository.SyncMetaRepositoryImpl
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant

private class FakeSyncMetaDao : SyncMetaDao {
    val rows: MutableMap<Pair<String, String>, SyncMetaEntity> = mutableMapOf()
    private val observable = MutableStateFlow<Map<Pair<String, String>, SyncMetaEntity>>(emptyMap())

    override suspend fun get(workspaceId: String, collection: String): SyncMetaEntity? =
        rows[workspaceId to collection]

    override suspend fun upsert(entity: SyncMetaEntity) {
        rows[entity.workspaceId to entity.collection] = entity
        observable.value = rows.toMap()
    }

    override fun observeByWorkspace(workspaceId: String): Flow<List<SyncMetaEntity>> =
        kotlinx.coroutines.flow.flow {
            observable.collect { snapshot ->
                emit(snapshot.values.filter { it.workspaceId == workspaceId })
            }
        }

    override suspend fun deleteByWorkspace(workspaceId: String) {
        rows.entries.removeAll { it.key.first == workspaceId }
        observable.value = rows.toMap()
    }

    override suspend fun deleteAll() {
        rows.clear()
        observable.value = rows.toMap()
    }
}

class SyncMetaRepositoryImplSpec : StringSpec({

    "cursor returns null for unknown (workspace, collection)" {
        runTest {
            val repo = SyncMetaRepositoryImpl(FakeSyncMetaDao())
            repo.cursor("ws", "transactions").shouldBeNull()
        }
    }

    "setCursor stores Instant — cursor returns the same value back" {
        runTest {
            val repo = SyncMetaRepositoryImpl(FakeSyncMetaDao())
            val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)

            repo.setCursor("ws", "transactions", now)

            repo.cursor("ws", "transactions") shouldBe now
        }
    }

    "setCursor preserves earlier markAttempt / markSuccess timestamps" {
        runTest {
            val dao = FakeSyncMetaDao()
            val repo = SyncMetaRepositoryImpl(dao)
            val attempt = Instant.fromEpochMilliseconds(100)
            val success = Instant.fromEpochMilliseconds(200)
            val cursor = Instant.fromEpochMilliseconds(300)

            repo.markAttempt("ws", "accounts", attempt)
            repo.markSuccess("ws", "accounts", success)
            repo.setCursor("ws", "accounts", cursor)

            val row = dao.rows[("ws" to "accounts")]!!
            row.lastSyncAttemptAt shouldBe attempt.toEpochMilliseconds()
            row.lastSyncSuccessAt shouldBe success.toEpochMilliseconds()
            row.lastPulledAt shouldBe cursor.toEpochMilliseconds()
        }
    }

    "markSuccess preserves cursor" {
        runTest {
            val dao = FakeSyncMetaDao()
            val repo = SyncMetaRepositoryImpl(dao)
            val cursor = Instant.fromEpochMilliseconds(500)
            repo.setCursor("ws", "transactions", cursor)

            repo.markSuccess(
                "ws",
                "transactions",
                Instant.fromEpochMilliseconds(600),
            )

            repo.cursor("ws", "transactions") shouldBe cursor
        }
    }

    "clearScope drops all rows for that workspace" {
        runTest {
            val dao = FakeSyncMetaDao()
            val repo = SyncMetaRepositoryImpl(dao)
            repo.setCursor("a", "accounts", Instant.fromEpochMilliseconds(1))
            repo.setCursor("a", "transactions", Instant.fromEpochMilliseconds(2))
            repo.setCursor("b", "accounts", Instant.fromEpochMilliseconds(3))

            repo.clearScope("a")

            dao.rows.keys shouldHaveSize 1
            dao.rows[("b" to "accounts")]?.lastPulledAt shouldBe 3L
        }
    }

    "isolated cursors per (workspace, collection)" {
        runTest {
            val repo = SyncMetaRepositoryImpl(FakeSyncMetaDao())
            repo.setCursor("ws", "accounts", Instant.fromEpochMilliseconds(1))
            repo.setCursor("ws", "transactions", Instant.fromEpochMilliseconds(2))

            repo.cursor("ws", "accounts") shouldBe Instant.fromEpochMilliseconds(1)
            repo.cursor("ws", "transactions") shouldBe Instant.fromEpochMilliseconds(2)
            repo.cursor("ws", "categories").shouldBeNull()
        }
    }

    "fresh repository starts empty" {
        runTest {
            val dao = FakeSyncMetaDao()
            SyncMetaRepositoryImpl(dao)
            dao.rows.values.shouldBeEmpty()
        }
    }
})
