package com.georgeci.moneysurfer.integration.sync

import com.georgeci.moneysurfer.data.sync.PendingMutationQueueImpl
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import com.georgeci.moneysurfer.sync.api.SyncEntityType
import com.georgeci.moneysurfer.sync.api.SyncScope
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
        queue = PendingMutationQueueImpl(harness.database.pendingMutationDao())
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

    "pendingCount Flow reflects active row count" {
        queue.pendingCount.first() shouldBe 0

        queue.enqueue(aMutation(id = "m-1"))
        queue.enqueue(aMutation(id = "m-2"))
        queue.pendingCount.first() shouldBe 2

        queue.markCompleted(listOf("m-1"))
        queue.pendingCount.first() shouldBe 1
    }
})

private fun aMutation(
    id: String,
    entityId: String = "entity-1",
    createdAt: kotlin.time.Instant = Clock.System.now(),
): PendingMutation = PendingMutation(
    id = id,
    entityType = SyncEntityType.TRANSACTION,
    entityId = entityId,
    operation = MutationOperation.INSERT,
    workspaceId = WorkspaceId("ws-1"),
    payload = """{"hello":"world"}""",
    createdAt = createdAt,
    attempts = 0,
    lastError = null,
)
