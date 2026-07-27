package com.georgeci.moneysurfer.integration.sync

import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.internal.repository.PendingMutationQueueImpl
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlin.time.Clock

/**
 * End-to-end integration of `PendingMutationQueueImpl` against the real Room
 * schema (in-memory). Unit tests use a list-backed fake queue; this test
 * proves:
 * - DAO + entity + indexes wire up,
 * - status transitions are durable across queries,
 * - `pending()` filter + ordering matches the SQL clauses.
 *
 * No emulator required — this runs alongside default `:integration-test:jvmTest`.
 */
class PendingMutationQueueIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness
    lateinit var queue: PendingMutationQueueImpl

    beforeEach {
        harness = IntegrationHarness()
        queue = PendingMutationQueueImpl(harness.syncDatabase.pendingMutationDao())
    }

    afterEach {
        harness.close()
    }

    "enqueue then pending() returns the row in createdAt order" {
        val now = Clock.System.now()
        val first = aMutation(id = "m-1", entityId = "tx-1", createdAt = now)
        val second = aMutation(id = "m-2", entityId = "tx-2", createdAt = now)

        queue.enqueue(first)
        queue.enqueue(second)

        val pending = queue.pending(SyncScope.AllUserData)
        pending shouldHaveSize 2
        pending.map { it.id } shouldContainExactly listOf("m-1", "m-2")
    }

    "markCompleted removes rows from pending()" {
        queue.enqueue(aMutation(id = "m-1"))
        queue.enqueue(aMutation(id = "m-2"))
        queue.enqueue(aMutation(id = "m-3"))

        queue.markCompleted(listOf("m-1", "m-3"))

        val pending = queue.pending(SyncScope.AllUserData)
        pending.map { it.id } shouldContainExactly listOf("m-2")
    }

    "markInFlight excludes rows from pending() but markFailed brings them back" {
        queue.enqueue(aMutation(id = "m-1"))
        queue.enqueue(aMutation(id = "m-2"))

        queue.markInFlight(listOf("m-1"))
        queue.pending(SyncScope.AllUserData).map { it.id } shouldContainExactly listOf("m-2")

        queue.markFailed("m-1", "transient error")
        queue.pending(SyncScope.AllUserData).map { it.id } shouldContainExactly listOf("m-1", "m-2")
    }

    "enqueueing the same change twice queues one push" {
        // Outbox rows carry no payload — the push re-reads the entity — so N rows for one entity all
        // send the identical current value. Renaming an account five times used to queue five.
        queue.enqueue(aMutation(id = "m-1", entityId = "tx-1"))
        queue.enqueue(aMutation(id = "m-2", entityId = "tx-1"))

        queue.pending(SyncScope.AllUserData).map { it.id } shouldContainExactly listOf("m-1")
    }

    "a write landing while the first is IN_FLIGHT queues a second row" {
        // The correctness half of the dedup: the in-flight push already read the entity, so without
        // a new row the change made after that read would never reach the server.
        queue.enqueue(aMutation(id = "m-1", entityId = "tx-1"))
        queue.markInFlight(listOf("m-1"))

        queue.enqueue(aMutation(id = "m-2", entityId = "tx-1"))

        queue.pending(SyncScope.AllUserData).map { it.id } shouldContainExactly listOf("m-2")
    }

    "dedup does not collapse different operations or different entities" {
        queue.enqueue(aMutation(id = "m-1", entityId = "tx-1"))
        queue.enqueue(aMutation(id = "m-2", entityId = "tx-2"))
        queue.enqueue(
            aMutation(id = "m-3", entityId = "tx-1").copy(operation = MutationOperation.DELETE),
        )

        queue.pending(SyncScope.AllUserData).map { it.id } shouldContainExactly
            listOf("m-1", "m-2", "m-3")
    }

    "the same entity id under two workspaces queues both" {
        // `WORKSPACE_MEMBER` is the one entity whose id is not a UUID: it is the *user* id, scoped
        // by workspace. Deduping without the scope would let leaving one workspace swallow the
        // enqueue for leaving another, and the surviving row pushes only its own workspace — the
        // other would keep the user ACTIVE on Firestore forever.
        queue.enqueue(
            aMutation(id = "m-1", entityId = "user-1").copy(
                entityType = SyncEntityTypes.WORKSPACE_MEMBER,
                operation = MutationOperation.UPDATE,
                scopeKey = "ws-1",
            ),
        )
        queue.enqueue(
            aMutation(id = "m-2", entityId = "user-1").copy(
                entityType = SyncEntityTypes.WORKSPACE_MEMBER,
                operation = MutationOperation.UPDATE,
                scopeKey = "ws-2",
            ),
        )

        queue.pending(SyncScope.AllUserData).map { it.scopeKey } shouldContainExactly
            listOf("ws-1", "ws-2")
    }

    "a scopeless row still dedupes against another scopeless row" {
        // `workspaceId IS :workspaceId` rather than `=`: SQL equality never matches NULL to NULL,
        // so a plain `=` would have made every settings enqueue a fresh row.
        queue.enqueue(
            aMutation(id = "m-1", entityId = "ui.theme_mode").copy(
                entityType = SyncEntityTypes.USER_CONFIG,
                scopeKey = null,
            ),
        )
        queue.enqueue(
            aMutation(id = "m-2", entityId = "ui.theme_mode").copy(
                entityType = SyncEntityTypes.USER_CONFIG,
                scopeKey = null,
            ),
        )

        queue.pending(SyncScope.AllUserData).map { it.id } shouldContainExactly listOf("m-1")
    }

    "a settings key queues one push however often it is toggled" {
        // The case that motivated the change: `entityId` is the key name and `scopeKey` is null,
        // and a settings screen bound to a switch produces runs of writes.
        repeat(times = 5) { attempt ->
            queue.enqueue(
                aMutation(id = "m-$attempt", entityId = "ui.theme_mode").copy(
                    entityType = SyncEntityTypes.USER_CONFIG,
                    scopeKey = null,
                ),
            )
        }

        queue.pending(SyncScope.AllUserData) shouldHaveSize 1
    }

    "pendingCount Flow reflects active row count" {
        queue.pendingCount.first() shouldBe 0

        queue.enqueue(aMutation(id = "m-1"))
        queue.enqueue(aMutation(id = "m-2"))
        queue.pendingCount.first() shouldBe 2

        queue.markCompleted(listOf("m-1"))
        queue.pendingCount.first() shouldBe 1
    }
})

/**
 * [entityId] defaults to [id] so rows are distinct by default: `enqueue` is insert-if-absent among
 * pending rows, and a test about status transitions should not accidentally exercise the dedup.
 */
private fun aMutation(
    id: String,
    entityId: String = id,
    createdAt: kotlin.time.Instant = Clock.System.now(),
): PendingMutation = PendingMutation(
    id = id,
    entityType = SyncEntityTypes.TRANSACTION,
    entityId = entityId,
    operation = MutationOperation.INSERT,
    scopeKey = "ws-1",
    createdAt = createdAt,
    attempts = 0,
    lastError = null,
)
