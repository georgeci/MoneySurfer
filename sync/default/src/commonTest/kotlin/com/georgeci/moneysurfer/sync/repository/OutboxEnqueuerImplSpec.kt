package com.georgeci.moneysurfer.sync.repository

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.AppVersionStatus
import com.georgeci.moneysurfer.domain.preferences.Pref
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.AppVersionGate
import com.georgeci.moneysurfer.sync.api.SyncScope
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest

private class FakeQueue : PendingMutationQueue {
    val enqueued = mutableListOf<PendingMutation>()
    override suspend fun enqueue(mutation: PendingMutation) { enqueued += mutation }
    override suspend fun pending(scope: SyncScope, limit: Int): List<PendingMutation> = emptyList()
    override suspend fun markInFlight(ids: List<String>) = Unit
    override suspend fun markCompleted(ids: List<String>) = Unit
    override suspend fun markFailed(id: String, error: String) = Unit
    override val pendingCount: Flow<Int> = MutableStateFlow(0)
}

private class FakeSessionPointers(uid: String?) : SessionPointers {
    override val currentUserId: Pref<UserId?> = Pref.inMemory(null)
    override val currentWorkspaceId: Pref<WorkspaceId?> = Pref.inMemory(null)
    override val currentFirebaseUid: Pref<String?> = Pref.inMemory(uid)
    override val hasUsedDemo: Pref<Boolean> = Pref.inMemory(false)
    override val currencyChosen: Pref<Boolean> = Pref.inMemory(true)
    override val onboardingSkipped: Pref<Boolean> = Pref.inMemory(false)
}

private class FakeAppVersionGate(private var allowed: Boolean) : AppVersionGate {
    override val status: StateFlow<AppVersionStatus?> = MutableStateFlow(AppVersionStatus.Supported)
    override suspend fun refresh(): AppVersionStatus = AppVersionStatus.Supported
    override fun isSyncAllowed(): Boolean = allowed
}

class OutboxEnqueuerImplSpec : StringSpec({

    "enqueueUpsert pushes a PendingMutation when signed in and gate allows" {
        runTest {
            val queue = FakeQueue()
            val enqueuer = OutboxEnqueuerImpl(
                outbox = queue,
                session = FakeSessionPointers(uid = "firebase-uid-42"),
                appVersionGate = FakeAppVersionGate(allowed = true),
            )

            enqueuer.enqueueUpsert(
                entityType = "TRANSACTION",
                entityId = "tx-1",
                scopeKey = "ws-1",
                operation = MutationOperation.INSERT,
            )

            queue.enqueued.size shouldBe 1
            val m = queue.enqueued.single()
            m.entityType shouldBe "TRANSACTION"
            m.entityId shouldBe "tx-1"
            m.scopeKey shouldBe "ws-1"
            m.operation shouldBe MutationOperation.INSERT
            m.attempts shouldBe 0
            m.lastError shouldBe null
            m.id.isNotBlank() shouldBe true
        }
    }

    "enqueueUpsert generates a unique id per call" {
        runTest {
            val queue = FakeQueue()
            val enqueuer = OutboxEnqueuerImpl(
                outbox = queue,
                session = FakeSessionPointers(uid = "fb"),
                appVersionGate = FakeAppVersionGate(allowed = true),
            )

            repeat(2) {
                enqueuer.enqueueUpsert("ACCOUNT", "a-$it", null, MutationOperation.UPDATE)
            }

            queue.enqueued.map { it.id }.toSet().size shouldBe 2
        }
    }

    "enqueueUpsert rejects DELETE — must call enqueueDelete instead" {
        runTest {
            val queue = FakeQueue()
            val enqueuer = OutboxEnqueuerImpl(
                outbox = queue,
                session = FakeSessionPointers(uid = "fb"),
                appVersionGate = FakeAppVersionGate(allowed = true),
            )

            val ex = shouldThrow<IllegalArgumentException> {
                enqueuer.enqueueUpsert("ACCOUNT", "a-1", null, MutationOperation.DELETE)
            }
            ex.message!! shouldContain "enqueueDelete"
            queue.enqueued.size shouldBe 0
        }
    }

    "enqueueDelete pushes a DELETE mutation" {
        runTest {
            val queue = FakeQueue()
            val enqueuer = OutboxEnqueuerImpl(
                outbox = queue,
                session = FakeSessionPointers(uid = "fb"),
                appVersionGate = FakeAppVersionGate(allowed = true),
            )

            enqueuer.enqueueDelete("ACCOUNT", "a-1", scopeKey = "ws-1")

            queue.enqueued.single().operation shouldBe MutationOperation.DELETE
        }
    }

    "no enqueue when not signed in to Firebase (demo session)" {
        runTest {
            val queue = FakeQueue()
            val enqueuer = OutboxEnqueuerImpl(
                outbox = queue,
                session = FakeSessionPointers(uid = null),
                appVersionGate = FakeAppVersionGate(allowed = true),
            )

            enqueuer.enqueueUpsert("ACCOUNT", "a-1", null, MutationOperation.INSERT)
            enqueuer.enqueueDelete("ACCOUNT", "a-1", null)

            queue.enqueued shouldBe emptyList()
            enqueuer.isEnabled() shouldBe false
        }
    }

    "no enqueue when uid is empty string" {
        runTest {
            val queue = FakeQueue()
            val enqueuer = OutboxEnqueuerImpl(
                outbox = queue,
                session = FakeSessionPointers(uid = ""),
                appVersionGate = FakeAppVersionGate(allowed = true),
            )

            enqueuer.enqueueUpsert("ACCOUNT", "a", null, MutationOperation.INSERT)

            queue.enqueued shouldBe emptyList()
            enqueuer.isEnabled() shouldBe false
        }
    }

    "no enqueue when app-version gate denies sync" {
        runTest {
            val queue = FakeQueue()
            val enqueuer = OutboxEnqueuerImpl(
                outbox = queue,
                session = FakeSessionPointers(uid = "fb"),
                appVersionGate = FakeAppVersionGate(allowed = false),
            )

            enqueuer.enqueueUpsert("ACCOUNT", "a", null, MutationOperation.INSERT)
            enqueuer.enqueueDelete("ACCOUNT", "a", null)

            queue.enqueued shouldBe emptyList()
            enqueuer.isEnabled() shouldBe false
        }
    }

    "isEnabled is true when signed in and gate allows" {
        runTest {
            val enqueuer = OutboxEnqueuerImpl(
                outbox = FakeQueue(),
                session = FakeSessionPointers(uid = "fb"),
                appVersionGate = FakeAppVersionGate(allowed = true),
            )
            enqueuer.isEnabled() shouldBe true
        }
    }
})
