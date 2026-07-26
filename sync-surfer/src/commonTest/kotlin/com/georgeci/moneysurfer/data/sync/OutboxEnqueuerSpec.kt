package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.model.AppVersionStatus
import com.georgeci.moneysurfer.domain.repositories.AppVersionGate
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuerImpl
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import com.georgeci.moneysurfer.sync.repository.PendingMutationQueue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

private class FakePendingMutationQueue : PendingMutationQueue {
    val items: MutableList<PendingMutation> = mutableListOf()

    override suspend fun enqueue(mutation: PendingMutation) {
        items += mutation
    }

    override suspend fun pending(scope: SyncScope, limit: Int): List<PendingMutation> =
        items.take(limit)

    override suspend fun markInFlight(ids: List<String>) = Unit
    override suspend fun markCompleted(ids: List<String>) {
        items.removeAll { it.id in ids }
    }
    override suspend fun markFailed(id: String, error: String) = Unit
    override val pendingCount: Flow<Int> = MutableStateFlow(0).asStateFlow()
    override fun observeOutbox(limit: Int): Flow<List<PendingMutation>> = flowOf(emptyList())
}

private class FakeAppVersionGate(
    private var current: AppVersionStatus = AppVersionStatus.Supported,
) : AppVersionGate {
    override val status = MutableStateFlow<AppVersionStatus?>(current).asStateFlow()
    override suspend fun refresh(): AppVersionStatus = current
    override fun isSyncAllowed(): Boolean = current !is AppVersionStatus.Unsupported
}

private fun enqueuerFor(
    uid: String?,
    versionStatus: AppVersionStatus = AppVersionStatus.Supported,
): Pair<OutboxEnqueuer, FakePendingMutationQueue> {
    val queue = FakePendingMutationQueue()
    val enqueuer = OutboxEnqueuerImpl(
        outbox = queue,
        session = InMemorySessionPointers(currentFirebaseUid = uid),
        appVersionGate = FakeAppVersionGate(versionStatus),
    )
    return enqueuer to queue
}

class OutboxEnqueuerSpec : StringSpec({

    "isEnabled returns false when firebaseUid is null (demo session)" {
        runTest {
            val (enqueuer, _) = enqueuerFor(uid = null)
            enqueuer.isEnabled().shouldBeFalse()
        }
    }

    "isEnabled returns false when firebaseUid is empty string" {
        runTest {
            val (enqueuer, _) = enqueuerFor(uid = "")
            enqueuer.isEnabled().shouldBeFalse()
        }
    }

    "isEnabled returns true when firebaseUid is non-empty" {
        runTest {
            val (enqueuer, _) = enqueuerFor(uid = "real-uid")
            enqueuer.isEnabled().shouldBeTrue()
        }
    }

    "enqueueUpsert is a no-op when demo session" {
        runTest {
            val (enqueuer, queue) = enqueuerFor(uid = null)
            enqueuer.enqueueUpsert(
                entityType = SyncEntityTypes.TRANSACTION,
                entityId = "txn-1",
                scopeKey = "ws-1",
                operation = MutationOperation.INSERT,
            )
            queue.items.shouldBeEmpty()
        }
    }

    "enqueueUpsert writes a PendingMutation when signed in" {
        runTest {
            val (enqueuer, queue) = enqueuerFor(uid = "real-uid")
            enqueuer.enqueueUpsert(
                entityType = SyncEntityTypes.TRANSACTION,
                entityId = "txn-1",
                scopeKey = "ws-1",
                operation = MutationOperation.INSERT,
            )
            queue.items shouldHaveSize 1
            val mutation = queue.items.single()
            mutation.entityType shouldBe SyncEntityTypes.TRANSACTION
            mutation.entityId shouldBe "txn-1"
            mutation.operation shouldBe MutationOperation.INSERT
            mutation.scopeKey shouldBe "ws-1"
            mutation.attempts shouldBe 0
            mutation.lastError.shouldBeNull()
        }
    }

    "enqueueDelete writes DELETE mutation" {
        runTest {
            val (enqueuer, queue) = enqueuerFor(uid = "real-uid")
            enqueuer.enqueueDelete(
                entityType = SyncEntityTypes.ACCOUNT,
                entityId = "acc-1",
                scopeKey = "ws-1",
            )
            queue.items shouldHaveSize 1
            val mutation = queue.items.single()
            mutation.operation shouldBe MutationOperation.DELETE
        }
    }

    "enqueueDelete is a no-op when demo session" {
        runTest {
            val (enqueuer, queue) = enqueuerFor(uid = null)
            enqueuer.enqueueDelete(
                entityType = SyncEntityTypes.ACCOUNT,
                entityId = "acc-1",
                scopeKey = "ws-1",
            )
            queue.items.shouldBeEmpty()
        }
    }

    "enqueueUpsert rejects DELETE operation" {
        runTest {
            val (enqueuer, _) = enqueuerFor(uid = "real-uid")
            try {
                enqueuer.enqueueUpsert(
                    entityType = SyncEntityTypes.TRANSACTION,
                    entityId = "txn-1",
                    scopeKey = null,
                    operation = MutationOperation.DELETE,
                )
                error("Expected IllegalArgumentException")
            } catch (e: IllegalArgumentException) {
                (e.message ?: "").shouldBe("DELETE has no payload — call enqueueDelete instead")
            }
        }
    }

    "ids are unique across mutations" {
        runTest {
            val (enqueuer, queue) = enqueuerFor(uid = "real-uid")
            repeat(3) { idx ->
                enqueuer.enqueueDelete(
                    entityType = SyncEntityTypes.TRANSACTION,
                    entityId = "txn-$idx",
                    scopeKey = "ws",
                )
            }
            queue.items.map { it.id }.toSet() shouldHaveSize 3
        }
    }

    "isEnabled returns false when app version is Unsupported" {
        runTest {
            val (enqueuer, _) = enqueuerFor(
                uid = "real-uid",
                versionStatus = AppVersionStatus.Unsupported(message = "update"),
            )
            enqueuer.isEnabled().shouldBeFalse()
        }
    }

    "enqueueUpsert is a no-op when app version is Unsupported" {
        runTest {
            val (enqueuer, queue) = enqueuerFor(
                uid = "real-uid",
                versionStatus = AppVersionStatus.Unsupported(message = "update"),
            )
            enqueuer.enqueueUpsert(
                entityType = SyncEntityTypes.TRANSACTION,
                entityId = "txn-1",
                scopeKey = "ws-1",
                operation = MutationOperation.INSERT,
            )
            queue.items.shouldBeEmpty()
        }
    }
})
