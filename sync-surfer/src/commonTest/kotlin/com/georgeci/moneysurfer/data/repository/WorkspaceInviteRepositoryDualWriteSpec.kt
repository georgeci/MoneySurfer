package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.data.db.dao.WorkspaceInviteDao
import com.georgeci.moneysurfer.data.db.entity.WorkspaceInviteEntity
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.model.AppVersionStatus
import com.georgeci.moneysurfer.domain.model.InviteStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceInvite
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

private class FakeWorkspaceInviteDao : WorkspaceInviteDao {
    val rows: MutableList<WorkspaceInviteEntity> = mutableListOf()

    override fun getAll(): Flow<List<WorkspaceInviteEntity>> =
        MutableStateFlow(rows.toList()).asStateFlow()

    override fun getByWorkspaceId(workspaceId: String): Flow<List<WorkspaceInviteEntity>> =
        MutableStateFlow(rows.filter { it.workspaceId == workspaceId }).asStateFlow()

    override fun getByEmail(email: String): Flow<List<WorkspaceInviteEntity>> =
        MutableStateFlow(rows.filter { it.email == email }).asStateFlow()

    override fun getByTargetUserId(userId: String): Flow<List<WorkspaceInviteEntity>> =
        MutableStateFlow(rows.filter { it.targetUserId == userId }).asStateFlow()

    override suspend fun getById(id: String): WorkspaceInviteEntity? =
        rows.firstOrNull { it.id == id }

    override suspend fun findJoinableInviteId(
        workspaceId: String,
        userId: String,
        email: String?,
    ): String? = rows
        .filter { it.workspaceId == workspaceId && it.status in setOf("PENDING", "ACCEPTED") }
        .filter { it.targetUserId == userId || (email != null && it.email.equals(email, ignoreCase = true)) }
        .sortedWith(compareBy({ if (it.status == "PENDING") 0 else 1 }, { -it.updatedAt }))
        .firstOrNull()
        ?.id

    override suspend fun insert(entity: WorkspaceInviteEntity) { rows += entity }
    override suspend fun insertAll(entities: List<WorkspaceInviteEntity>) { rows += entities }

    override suspend fun update(entity: WorkspaceInviteEntity) {
        val idx = rows.indexOfFirst { it.id == entity.id }
        if (idx >= 0) rows[idx] = entity
    }

    override suspend fun upsert(entity: WorkspaceInviteEntity) {
        val idx = rows.indexOfFirst { it.id == entity.id }
        if (idx >= 0) rows[idx] = entity else rows += entity
    }

    override suspend fun delete(id: String) {
        rows.removeAll { it.id == id }
    }

    override suspend fun deleteAll() { rows.clear() }
}

private class InviteFakePendingMutationQueue : PendingMutationQueue {
    val items: MutableList<PendingMutation> = mutableListOf()
    override suspend fun enqueue(mutation: PendingMutation) { items += mutation }
    override suspend fun pending(scope: SyncScope, limit: Int) = items.take(limit)
    override suspend fun markInFlight(ids: List<String>) = Unit
    override suspend fun markCompleted(ids: List<String>) { items.removeAll { it.id in ids } }
    override suspend fun markFailed(id: String, error: String) = Unit
    override val pendingCount: Flow<Int> = MutableStateFlow(0).asStateFlow()
    override fun observeOutbox(limit: Int): Flow<List<PendingMutation>> = flowOf(emptyList())
}

private class InviteFakeAppVersionGate(
    private val current: AppVersionStatus = AppVersionStatus.Supported,
) : AppVersionGate {
    override val status = MutableStateFlow<AppVersionStatus?>(current).asStateFlow()
    override suspend fun refresh(): AppVersionStatus = current
    override fun isSyncAllowed(): Boolean = current !is AppVersionStatus.Unsupported
}

private data class InviteTestRig(
    val repo: WorkspaceInviteRepositoryImpl,
    val dao: FakeWorkspaceInviteDao,
    val queue: InviteFakePendingMutationQueue,
)

private fun buildInviteRig(uid: String?): InviteTestRig {
    val dao = FakeWorkspaceInviteDao()
    val queue = InviteFakePendingMutationQueue()
    val enqueuer = OutboxEnqueuerImpl(
        outbox = queue,
        session = InMemorySessionPointers(currentFirebaseUid = uid),
        appVersionGate = InviteFakeAppVersionGate(),
    )
    return InviteTestRig(
        WorkspaceInviteRepositoryImpl(
            dao,
            enqueuer,
            com.georgeci.moneysurfer.domain.primitives.ClockUseCase(),
            com.georgeci.moneysurfer.data.repository.TimeFormatter(),
        ),
        dao,
        queue,
    )
}

private fun pendingInvite() = WorkspaceInvite(
    id = WorkspaceInviteId("inv-1"),
    workspaceId = WorkspaceId("ws-1"),
    email = "invitee@example.com",
    targetUserId = null,
    role = WorkspaceRole.EDITOR,
    status = InviteStatus.PENDING,
    invitedByUserId = UserId("owner-uid"),
    createdAt = kotlin.time.Instant.fromEpochMilliseconds(1_700_000_000_000L),
    updatedAt = kotlin.time.Instant.fromEpochMilliseconds(1_700_000_000_000L),
    expiresAt = kotlin.time.Instant.fromEpochMilliseconds(1_700_000_000_000L + 14L * 24L * 60L * 60L * 1000L),
)

class WorkspaceInviteRepositoryDualWriteSpec : StringSpec({

    "upsert (new) writes both DAO and outbox as INSERT when signed in" {
        runTest {
            val (repo, dao, queue) = buildInviteRig(uid = "real-uid")

            repo.upsert(pendingInvite())

            dao.rows shouldHaveSize 1
            dao.rows.single().status shouldBe InviteStatus.PENDING.name
            queue.items shouldHaveSize 1
            val mutation = queue.items.single()
            mutation.entityType shouldBe SyncEntityTypes.WORKSPACE_INVITE
            mutation.entityId shouldBe "inv-1"
            mutation.scopeKey shouldBe "ws-1"
            mutation.operation shouldBe MutationOperation.INSERT
        }
    }

    "upsert in demo session writes DAO but skips outbox" {
        runTest {
            val (repo, dao, queue) = buildInviteRig(uid = null)

            repo.upsert(pendingInvite())

            dao.rows shouldHaveSize 1
            queue.items.shouldBeEmpty()
        }
    }

    "upsert (existing) enqueues UPDATE" {
        runTest {
            val (repo, _, queue) = buildInviteRig(uid = "real-uid")
            repo.upsert(pendingInvite())
            queue.items.clear()

            repo.upsert(pendingInvite().copy(role = WorkspaceRole.VIEWER))

            queue.items shouldHaveSize 1
            queue.items.single().operation shouldBe MutationOperation.UPDATE
        }
    }

    "markAccepted transitions status and stamps respondedAt" {
        runTest {
            val (repo, dao, queue) = buildInviteRig(uid = "real-uid")
            repo.upsert(pendingInvite())
            queue.items.clear()

            repo.markAccepted(WorkspaceInviteId("inv-1"))

            val row = dao.rows.single()
            row.status shouldBe InviteStatus.ACCEPTED.name
            (row.respondedAt != null) shouldBe true

            queue.items shouldHaveSize 1
            queue.items.single().operation shouldBe MutationOperation.UPDATE
        }
    }

    "markDeclined transitions status to DECLINED" {
        runTest {
            val (repo, dao, queue) = buildInviteRig(uid = "real-uid")
            repo.upsert(pendingInvite())
            queue.items.clear()

            repo.markDeclined(WorkspaceInviteId("inv-1"))

            dao.rows.single().status shouldBe InviteStatus.DECLINED.name
            queue.items shouldHaveSize 1
            queue.items.single().operation shouldBe MutationOperation.UPDATE
        }
    }

    "cancel transitions status to CANCELLED" {
        runTest {
            val (repo, dao, queue) = buildInviteRig(uid = "real-uid")
            repo.upsert(pendingInvite())
            queue.items.clear()

            repo.cancel(WorkspaceInviteId("inv-1"))

            dao.rows.single().status shouldBe InviteStatus.CANCELLED.name
            queue.items shouldHaveSize 1
            queue.items.single().operation shouldBe MutationOperation.UPDATE
        }
    }

    "transition on missing row is a no-op" {
        runTest {
            val (repo, dao, queue) = buildInviteRig(uid = "real-uid")

            repo.markAccepted(WorkspaceInviteId("ghost"))

            dao.rows.shouldBeEmpty()
            queue.items.shouldBeEmpty()
        }
    }
})
