package com.georgeci.moneysurfer.sync.internal.repository

import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.db.dao.PendingMutationDao
import com.georgeci.moneysurfer.sync.db.entity.PendingMutationEntity
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant

private class FakePendingMutationDao : PendingMutationDao {
    val rows: MutableMap<String, PendingMutationEntity> = LinkedHashMap()
    val countFlow = MutableStateFlow(0)

    val allFlow = MutableStateFlow<List<PendingMutationEntity>>(emptyList())

    fun recompute() {
        countFlow.value = rows.values.count { it.status != PendingMutationEntity.STATUS_IN_FLIGHT }
        allFlow.value = rows.values.sortedBy { it.createdAt }
    }

    @Suppress("LongParameterList")
    override suspend fun insertIfAbsent(
        id: String,
        entityType: String,
        entityId: String,
        operation: String,
        workspaceId: String?,
        createdAt: Long,
        attempts: Int,
        status: String,
        lastError: String?,
    ) {
        val duplicate = rows.values.any {
            it.entityType == entityType &&
                it.entityId == entityId &&
                it.operation == operation &&
                // Part of the identity: `WORKSPACE_MEMBER` reuses one userId across workspaces.
                it.workspaceId == workspaceId &&
                it.status == PendingMutationEntity.STATUS_PENDING
        }
        if (duplicate) return
        rows[id] = PendingMutationEntity(
            id = id,
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            workspaceId = workspaceId,
            createdAt = createdAt,
            attempts = attempts,
            status = status,
            lastError = lastError,
        )
        recompute()
    }

    override suspend fun pending(workspaceId: String?, limit: Int): List<PendingMutationEntity> =
        rows.values
            .filter { it.status == PendingMutationEntity.STATUS_PENDING }
            .filter { workspaceId == null || it.workspaceId == workspaceId || it.workspaceId == null }
            .sortedBy { it.createdAt }
            .take(limit)

    override suspend fun markInFlight(ids: List<String>) {
        ids.forEach { id ->
            rows[id]?.let { rows[id] = it.copy(status = PendingMutationEntity.STATUS_IN_FLIGHT) }
        }
        recompute()
    }

    override suspend fun deleteByIds(ids: List<String>) {
        ids.forEach { rows.remove(it) }
        recompute()
    }

    override suspend fun markFailed(id: String, error: String) {
        rows[id]?.let {
            rows[id] = it.copy(
                status = PendingMutationEntity.STATUS_PENDING,
                attempts = it.attempts + 1,
                lastError = error,
            )
        }
        recompute()
    }

    override fun pendingCount(): Flow<Int> = countFlow

    override fun observeAll(limit: Int): Flow<List<PendingMutationEntity>> = allFlow.map { it.take(limit) }

    override suspend fun deleteAll() {
        rows.clear()
        recompute()
    }
}

private fun mutation(
    id: String,
    createdAtMs: Long,
    operation: MutationOperation = MutationOperation.INSERT,
    scopeKey: String? = "ws-1",
    attempts: Int = 0,
    lastError: String? = null,
) = PendingMutation(
    id = id,
    entityType = "TRANSACTION",
    entityId = "tx-$id",
    operation = operation,
    scopeKey = scopeKey,
    createdAt = Instant.fromEpochMilliseconds(createdAtMs),
    attempts = attempts,
    lastError = lastError,
)

class PendingMutationQueueImplSpec : StringSpec({

    "enqueue stores entity with PENDING status and roundtrips fields" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            val m = mutation("a", 1_000L, MutationOperation.UPDATE, scopeKey = "ws-A", attempts = 2, lastError = "x")

            queue.enqueue(m)

            val stored = dao.rows.values.single()
            stored.id shouldBe "a"
            stored.status shouldBe PendingMutationEntity.STATUS_PENDING
            stored.operation shouldBe "UPDATE"
            stored.workspaceId shouldBe "ws-A"
            stored.attempts shouldBe 2
            stored.lastError shouldBe "x"
            stored.createdAt shouldBe 1_000L
        }
    }

    "pending returns rows sorted by createdAt ascending" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            queue.enqueue(mutation("late", 3_000L))
            queue.enqueue(mutation("early", 1_000L))
            queue.enqueue(mutation("mid", 2_000L))

            val ids = queue.pending(SyncScope.AllUserData, limit = 10).map { it.id }
            ids shouldContainExactly listOf("early", "mid", "late")
        }
    }

    "pending honours the limit" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            repeat(5) { queue.enqueue(mutation("m$it", it.toLong())) }
            queue.pending(SyncScope.AllUserData, limit = 2) shouldHaveSize 2
        }
    }

    "pending excludes IN_FLIGHT rows after markInFlight" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            queue.enqueue(mutation("a", 1_000L))
            queue.enqueue(mutation("b", 2_000L))

            queue.markInFlight(listOf("a"))

            queue.pending(SyncScope.AllUserData, limit = 10).map { it.id } shouldContainExactly listOf("b")
        }
    }

    "markInFlight is a no-op for empty list" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            queue.enqueue(mutation("a", 1_000L))
            queue.markInFlight(emptyList())
            dao.rows["a"]!!.status shouldBe PendingMutationEntity.STATUS_PENDING
        }
    }

    "markCompleted deletes rows; empty list is a no-op" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            queue.enqueue(mutation("a", 1_000L))
            queue.enqueue(mutation("b", 2_000L))

            queue.markCompleted(emptyList())
            dao.rows.size shouldBe 2

            queue.markCompleted(listOf("a"))
            dao.rows.keys shouldContainExactly setOf("b")
        }
    }

    "markFailed bumps attempts, records error and resets to PENDING" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            queue.enqueue(mutation("a", 1_000L))
            queue.markInFlight(listOf("a"))

            queue.markFailed("a", "boom")

            val row = dao.rows["a"]!!
            row.status shouldBe PendingMutationEntity.STATUS_PENDING
            row.attempts shouldBe 1
            row.lastError shouldBe "boom"
        }
    }

    "pendingCount excludes IN_FLIGHT rows" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            queue.enqueue(mutation("a", 1L))
            queue.enqueue(mutation("b", 2L))
            queue.pendingCount.first() shouldBe 2

            queue.markInFlight(listOf("a"))
            queue.pendingCount.first() shouldBe 1

            queue.markCompleted(listOf("b"))
            queue.pendingCount.first() shouldBe 0
        }
    }

    "every SyncScope variant currently uses null workspace filter (cross-workspace push)" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            queue.enqueue(mutation("ws1", 1L, scopeKey = "ws-1"))
            queue.enqueue(mutation("ws2", 2L, scopeKey = "ws-2"))
            queue.enqueue(mutation("root", 3L, scopeKey = null))

            SyncScope.entries.forEach { scope ->
                queue.pending(scope, limit = 10).map { it.id }.toSet() shouldBe
                    setOf("ws1", "ws2", "root")
            }
        }
    }

    "observeOutbox keeps in-flight rows, oldest first — a stuck push must not read as an empty outbox" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            queue.enqueue(mutation("second", 2L))
            queue.enqueue(mutation("first", 1L))
            queue.markInFlight(listOf("first"))

            queue.pendingCount.first() shouldBe 1
            queue.observeOutbox().first().map { it.id } shouldContainExactly listOf("first", "second")
        }
    }

    "observeOutbox honours its row limit" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            queue.enqueue(mutation("a", 1L))
            queue.enqueue(mutation("b", 2L))

            queue.observeOutbox(limit = 1).first().map { it.id } shouldContainExactly listOf("a")
        }
    }

    "DELETE operation roundtrips through the entity layer" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            queue.enqueue(mutation("d", 1L, operation = MutationOperation.DELETE))
            val out = queue.pending(SyncScope.AllUserData, limit = 10).single()
            out.operation shouldBe MutationOperation.DELETE
        }
    }
})
