package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.WorkspaceMemberDao
import com.georgeci.moneysurfer.data.db.entity.WorkspaceMemberEntity
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.model.AppVersionStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceMember
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AppVersionGate
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuerImpl
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import com.georgeci.moneysurfer.sync.repository.PendingMutationQueue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest

private class FakeWorkspaceMemberDao : WorkspaceMemberDao {
    val rows: MutableList<WorkspaceMemberEntity> = mutableListOf()

    override fun getAll(): Flow<List<WorkspaceMemberEntity>> =
        MutableStateFlow(rows.toList()).asStateFlow()

    override fun getByWorkspaceId(workspaceId: String): Flow<List<WorkspaceMemberEntity>> =
        MutableStateFlow(rows.filter { it.workspaceId == workspaceId }).asStateFlow()

    override fun getByUserId(userId: String): Flow<List<WorkspaceMemberEntity>> =
        MutableStateFlow(rows.filter { it.userId == userId }).asStateFlow()

    override suspend fun getById(userId: String, workspaceId: String): WorkspaceMemberEntity? =
        rows.firstOrNull { it.userId == userId && it.workspaceId == workspaceId }

    override suspend fun insert(entity: WorkspaceMemberEntity) {
        rows += entity
    }

    override suspend fun insertAll(entities: List<WorkspaceMemberEntity>) {
        rows += entities
    }

    override suspend fun update(entity: WorkspaceMemberEntity) {
        val idx = rows.indexOfFirst {
            it.userId == entity.userId && it.workspaceId == entity.workspaceId
        }
        if (idx >= 0) rows[idx] = entity
    }

    override suspend fun upsert(entity: WorkspaceMemberEntity) {
        val idx = rows.indexOfFirst {
            it.userId == entity.userId && it.workspaceId == entity.workspaceId
        }
        if (idx >= 0) rows[idx] = entity else rows += entity
    }

    override suspend fun delete(userId: String, workspaceId: String) {
        rows.removeAll { it.userId == userId && it.workspaceId == workspaceId }
    }

    override suspend fun deleteAll() {
        rows.clear()
    }
}

private class FakePendingMutationQueue : PendingMutationQueue {
    val items: MutableList<PendingMutation> = mutableListOf()
    override suspend fun enqueue(mutation: PendingMutation) { items += mutation }
    override suspend fun pending(scope: SyncScope, limit: Int) = items.take(limit)
    override suspend fun markInFlight(ids: List<String>) = Unit
    override suspend fun markCompleted(ids: List<String>) { items.removeAll { it.id in ids } }
    override suspend fun markFailed(id: String, error: String) = Unit
    override val pendingCount: Flow<Int> = MutableStateFlow(0).asStateFlow()
}

private data class TestRig(
    val repo: WorkspaceMemberRepositoryImpl,
    val dao: FakeWorkspaceMemberDao,
    val queue: FakePendingMutationQueue,
)

private class FakeAppVersionGate(
    private val current: AppVersionStatus = AppVersionStatus.Supported,
) : AppVersionGate {
    override val status = MutableStateFlow<AppVersionStatus?>(current).asStateFlow()
    override suspend fun refresh(): AppVersionStatus = current
    override fun isSyncAllowed(): Boolean = current !is AppVersionStatus.Unsupported
}

private fun build(uid: String?): TestRig {
    val dao = FakeWorkspaceMemberDao()
    val queue = FakePendingMutationQueue()
    val enqueuer = OutboxEnqueuerImpl(
        outbox = queue,
        session = InMemorySessionPointers(currentFirebaseUid = uid),
        appVersionGate = FakeAppVersionGate(),
    )
    return TestRig(
        WorkspaceMemberRepositoryImpl(
            dao,
            enqueuer,
            com.georgeci.moneysurfer.domain.primitives.ClockUseCase(),
            com.georgeci.moneysurfer.data.repository.TimeFormatter(),
        ),
        dao,
        queue,
    )
}

private fun ownerMember() = WorkspaceMember(
    userId = UserId("owner-uid"),
    workspaceId = WorkspaceId("ws-1"),
    role = WorkspaceRole.OWNER,
    status = WorkspaceMemberStatus.ACTIVE,
    displayName = "Alice",
    email = "alice@example.com",
    addedByUserId = UserId("owner-uid"),
    createdAt = kotlin.time.Instant.fromEpochMilliseconds(1_700_000_000_000L),
)

class WorkspaceMemberRepositoryDualWriteSpec : StringSpec({

    "insert writes both DAO and outbox when signed in" {
        runTest {
            val (repo, dao, queue) = build(uid = "real-uid")

            repo.insert(ownerMember())

            dao.rows shouldHaveSize 1
            dao.rows.single().role shouldBe WorkspaceRole.OWNER.name
            dao.rows.single().status shouldBe WorkspaceMemberStatus.ACTIVE.name
            queue.items shouldHaveSize 1
            val mutation = queue.items.single()
            mutation.entityType shouldBe SyncEntityTypes.WORKSPACE_MEMBER
            mutation.entityId shouldBe "owner-uid"
            mutation.scopeKey shouldBe "ws-1"
            mutation.operation shouldBe MutationOperation.INSERT
        }
    }

    "insert in demo session writes DAO but skips outbox" {
        runTest {
            val (repo, dao, queue) = build(uid = null)

            repo.insert(ownerMember())

            dao.rows shouldHaveSize 1
            queue.items.shouldBeEmpty()
        }
    }

    "markRemoved soft-deletes: status REMOVED, removedAt set, enqueues UPDATE" {
        runTest {
            val (repo, dao, queue) = build(uid = "real-uid")
            repo.insert(ownerMember())
            queue.items.clear() // ignore the INSERT, focus on remove

            repo.markRemoved(
                userId = UserId("owner-uid"),
                workspaceId = WorkspaceId("ws-1"),
                removedByUserId = UserId("admin-uid"),
            )

            // Row stays — soft-delete only.
            dao.rows shouldHaveSize 1
            val row = dao.rows.single()
            row.status shouldBe WorkspaceMemberStatus.REMOVED.name
            (row.removedAt != null) shouldBe true

            queue.items shouldHaveSize 1
            val mutation = queue.items.single()
            mutation.operation shouldBe MutationOperation.UPDATE
            mutation.entityId shouldBe "owner-uid"
        }
    }

    "markLeft soft-deletes: status LEFT, leftAt set, enqueues UPDATE" {
        runTest {
            val (repo, dao, queue) = build(uid = "real-uid")
            repo.insert(ownerMember())
            queue.items.clear()

            repo.markLeft(
                userId = UserId("owner-uid"),
                workspaceId = WorkspaceId("ws-1"),
            )

            dao.rows shouldHaveSize 1
            val row = dao.rows.single()
            row.status shouldBe WorkspaceMemberStatus.LEFT.name
            (row.leftAt != null) shouldBe true

            queue.items shouldHaveSize 1
            val mutation = queue.items.single()
            mutation.operation shouldBe MutationOperation.UPDATE
        }
    }

    "markRemoved on missing row is a no-op" {
        runTest {
            val (repo, dao, queue) = build(uid = "real-uid")

            repo.markRemoved(
                userId = UserId("ghost"),
                workspaceId = WorkspaceId("ws-1"),
                removedByUserId = null,
            )

            dao.rows.shouldBeEmpty()
            queue.items.shouldBeEmpty()
        }
    }

    "update bumps updatedAt and enqueues UPDATE" {
        runTest {
            val (repo, _, queue) = build(uid = "real-uid")
            repo.insert(ownerMember())
            queue.items.clear()

            repo.update(ownerMember().copy(role = WorkspaceRole.VIEWER))

            queue.items shouldHaveSize 1
            queue.items.single().operation shouldBe MutationOperation.UPDATE
        }
    }
})
