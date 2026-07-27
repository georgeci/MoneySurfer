package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.db.dao.PendingMutationDao
import com.georgeci.moneysurfer.sync.db.entity.PendingMutationEntity
import com.georgeci.moneysurfer.sync.internal.repository.PendingMutationQueueImpl
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant

private class FakePendingMutationDao : PendingMutationDao {
    val rows: MutableList<PendingMutationEntity> = mutableListOf()
    private val countFlow = MutableStateFlow(0)
    private val allFlow = MutableStateFlow<List<PendingMutationEntity>>(emptyList())

    override suspend fun insert(entity: PendingMutationEntity) {
        rows += entity
        recomputeCount()
    }

    override suspend fun pending(workspaceId: String?, limit: Int): List<PendingMutationEntity> =
        rows
            .filter { it.status == PendingMutationEntity.STATUS_PENDING }
            .filter { workspaceId == null || it.workspaceId == workspaceId || it.workspaceId == null }
            .sortedBy { it.createdAt }
            .take(limit)

    override suspend fun markInFlight(ids: List<String>) {
        rows.replaceAll { entity ->
            if (entity.id in ids) {
                entity.copy(status = PendingMutationEntity.STATUS_IN_FLIGHT)
            } else {
                entity
            }
        }
        recomputeCount()
    }

    override suspend fun deleteByIds(ids: List<String>) {
        rows.removeAll { it.id in ids }
        recomputeCount()
    }

    override suspend fun markFailed(id: String, error: String) {
        rows.replaceAll { entity ->
            if (entity.id == id) {
                entity.copy(
                    status = PendingMutationEntity.STATUS_PENDING,
                    attempts = entity.attempts + 1,
                    lastError = error,
                )
            } else {
                entity
            }
        }
        recomputeCount()
    }

    override fun pendingCount(): Flow<Int> = countFlow.asStateFlow()

    override fun observeAll(limit: Int): Flow<List<PendingMutationEntity>> =
        allFlow.map { rows -> rows.sortedBy { it.createdAt }.take(limit) }

    override suspend fun deleteAll() {
        rows.clear()
        recomputeCount()
    }

    private fun recomputeCount() {
        countFlow.value = rows.count { it.status != PendingMutationEntity.STATUS_IN_FLIGHT }
        allFlow.value = rows.toList()
    }
}

private fun mutation(
    id: String,
    workspaceId: String? = "ws",
    createdAt: Long = 0L,
    attempts: Int = 0,
) = PendingMutation(
    id = id,
    entityType = SyncEntityTypes.TRANSACTION,
    entityId = "$id-entity",
    operation = MutationOperation.INSERT,
    scopeKey = workspaceId,
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    attempts = attempts,
    lastError = null,
)

class PendingMutationQueueImplSpec : StringSpec({

    "enqueue persists a row with PENDING status" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)

            queue.enqueue(mutation("a"))

            dao.rows shouldHaveSize 1
            dao.rows.single().status shouldBe PendingMutationEntity.STATUS_PENDING
            dao.rows.single().attempts shouldBe 0
        }
    }

    "pending returns rows in createdAt order" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)

            queue.enqueue(mutation("third", createdAt = 30))
            queue.enqueue(mutation("first", createdAt = 10))
            queue.enqueue(mutation("second", createdAt = 20))

            val result = queue.pending(SyncScope.AllUserData)
            result.map { it.id } shouldBe listOf("first", "second", "third")
        }
    }

    "pending respects limit" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            repeat(5) { idx -> queue.enqueue(mutation("m$idx", createdAt = idx.toLong())) }

            queue.pending(SyncScope.AllUserData, limit = 2) shouldHaveSize 2
        }
    }

    "markInFlight excludes rows from the next pending batch" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            queue.enqueue(mutation("a"))
            queue.enqueue(mutation("b"))

            queue.markInFlight(listOf("a"))

            queue.pending(SyncScope.AllUserData).map { it.id } shouldBe listOf("b")
        }
    }

    "markCompleted removes rows" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            queue.enqueue(mutation("a"))
            queue.enqueue(mutation("b"))

            queue.markCompleted(listOf("a"))

            dao.rows.map { it.id } shouldBe listOf("b")
        }
    }

    "markFailed returns row to PENDING with attempts incremented" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            queue.enqueue(mutation("a"))
            queue.markInFlight(listOf("a"))

            queue.markFailed("a", error = "network down")

            val row = dao.rows.single()
            row.status shouldBe PendingMutationEntity.STATUS_PENDING
            row.attempts shouldBe 1
            row.lastError shouldBe "network down"
        }
    }

    "round-trip preserves all fields" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            val original = mutation("a", workspaceId = "ws-1", createdAt = 12345)

            queue.enqueue(original)

            val read = queue.pending(SyncScope.AllUserData).single()
            read.id shouldBe original.id
            read.entityType shouldBe original.entityType
            read.entityId shouldBe original.entityId
            read.operation shouldBe original.operation
            read.scopeKey shouldBe original.scopeKey
            read.createdAt shouldBe original.createdAt
            read.attempts shouldBe original.attempts
            read.lastError shouldBe original.lastError
        }
    }

    "DELETE mutation persists and is readable" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)

            queue.enqueue(
                PendingMutation(
                    id = "del-1",
                    entityType = SyncEntityTypes.TRANSACTION,
                    entityId = "txn-1",
                    operation = MutationOperation.DELETE,
                    scopeKey = "ws",
                    createdAt = Instant.fromEpochMilliseconds(0),
                    attempts = 0,
                    lastError = null,
                ),
            )

            val read = queue.pending(SyncScope.AllUserData).single()
            read.operation shouldBe MutationOperation.DELETE
        }
    }

    "markCompleted with empty list is a no-op" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            queue.enqueue(mutation("a"))

            queue.markCompleted(emptyList())

            dao.rows shouldHaveSize 1
        }
    }

    "markInFlight with empty list is a no-op" {
        runTest {
            val dao = FakePendingMutationDao()
            val queue = PendingMutationQueueImpl(dao)
            queue.enqueue(mutation("a"))

            queue.markInFlight(emptyList())

            queue.pending(SyncScope.AllUserData).shouldHaveSize(1)
        }
    }

    "pending returns empty when queue is empty" {
        runTest {
            val queue = PendingMutationQueueImpl(FakePendingMutationDao())
            queue.pending(SyncScope.AllUserData).shouldBeEmpty()
        }
    }
})
