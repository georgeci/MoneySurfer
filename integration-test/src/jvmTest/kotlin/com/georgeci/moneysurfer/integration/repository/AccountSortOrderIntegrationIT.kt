package com.georgeci.moneysurfer.integration.repository

import com.georgeci.moneysurfer.data.repository.AccountRepositoryImpl
import com.georgeci.moneysurfer.data.repository.TimeFormatter
import com.georgeci.moneysurfer.domain.fixtures.accountId
import com.georgeci.moneysurfer.domain.fixtures.anAccount
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

/**
 * Drag-to-reorder against real Room (issue #305). The fake-backed ViewModel tests cannot reach
 * the parts that actually decide the order — the `ORDER BY`, the transactional position rewrite,
 * and the append-on-create query — so they live here.
 */
class AccountSortOrderIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness
    lateinit var outbox: RecordingOutbox
    lateinit var repository: AccountRepositoryImpl

    suspend fun seedAccounts(vararg ids: String) = ids.forEach { id ->
        repository.insert(
            anAccount(id = accountId(id), workspaceId = DEFAULT_WORKSPACE_ID, name = id.uppercase()),
        )
    }

    suspend fun storedOrder(): List<Pair<String, Int>> =
        repository.getByWorkspaceId(DEFAULT_WORKSPACE_ID).first().map { it.id.value to it.sortOrder }

    beforeEach {
        harness = IntegrationHarness()
        outbox = RecordingOutbox()
        repository = AccountRepositoryImpl(
            dao = harness.database.accountDao(),
            outboxEnqueuer = outbox,
            clock = ClockUseCase(),
            timeFormatter = TimeFormatter(),
        )
        harness.seedWorkspace()
    }

    afterEach { harness.close() }

    "a new account is appended after the ones the workspace already has" {
        seedAccounts("a-1", "a-2", "a-3")

        storedOrder() shouldBe listOf("a-1" to 0, "a-2" to 1, "a-3" to 2)
    }

    "reorder rewrites the positions and reads come back in the new order" {
        seedAccounts("a-1", "a-2", "a-3")

        repository.reorder(listOf(accountId("a-3"), accountId("a-1"), accountId("a-2")))

        storedOrder() shouldBe listOf("a-3" to 0, "a-1" to 1, "a-2" to 2)
    }

    "reorder syncs only the accounts that actually moved" {
        seedAccounts("a-1", "a-2", "a-3")
        outbox.upserts.clear()

        // a-1 stays at 0; only the last two trade places.
        repository.reorder(listOf(accountId("a-1"), accountId("a-3"), accountId("a-2")))

        outbox.upserts.toSet() shouldBe setOf("a-2", "a-3")
    }

    "dropping a row back where it started writes nothing" {
        seedAccounts("a-1", "a-2", "a-3")
        val before = repository.getByWorkspaceId(DEFAULT_WORKSPACE_ID).first().map { it.updatedAt }
        outbox.upserts.clear()

        repository.reorder(listOf(accountId("a-1"), accountId("a-2"), accountId("a-3")))

        outbox.upserts.shouldBeEmptyList()
        repository.getByWorkspaceId(DEFAULT_WORKSPACE_ID).first().map { it.updatedAt } shouldBe before
    }

    "an id the workspace does not know is skipped without leaving a hole in the numbering" {
        seedAccounts("a-1", "a-2", "a-3")

        repository.reorder(
            listOf(accountId("a-3"), accountId("gone"), accountId("a-1"), accountId("a-2")),
        )

        storedOrder() shouldBe listOf("a-3" to 0, "a-1" to 1, "a-2" to 2)
    }

    "accounts that share a position are still listed in a stable order" {
        seedAccounts("a-2", "a-3", "a-1")
        // What a workspace pulled from a client that predates sortOrder looks like: every row 0.
        harness.database.accountDao().setSortOrders(mapOf("a-1" to 0, "a-2" to 0, "a-3" to 0), 1L)

        // The name tiebreak decides, rather than whatever order the rows happen to come out in.
        storedOrder().map { it.first } shouldBe listOf("a-1", "a-2", "a-3")
    }
})

private fun List<*>.shouldBeEmptyList() {
    if (isNotEmpty()) error("expected no outbox rows, got $this")
}

/** Records what would be pushed, so a test can assert that an unmoved account is left alone. */
private class RecordingOutbox : OutboxEnqueuer {
    val upserts = mutableListOf<String>()

    override suspend fun enqueueUpsert(
        entityType: String,
        entityId: String,
        scopeKey: String?,
        operation: MutationOperation,
    ) {
        upserts += entityId
    }

    override suspend fun enqueueDelete(entityType: String, entityId: String, scopeKey: String?) = Unit

    override suspend fun isEnabled(): Boolean = true
}
