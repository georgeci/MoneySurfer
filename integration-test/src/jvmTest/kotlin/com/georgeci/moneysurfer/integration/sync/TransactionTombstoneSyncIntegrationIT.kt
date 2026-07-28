package com.georgeci.moneysurfer.integration.sync

import com.georgeci.moneysurfer.data.db.entity.AccountEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionEntity
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.data.remote.TransactionDoc
import com.georgeci.moneysurfer.data.repository.TimeFormatter
import com.georgeci.moneysurfer.data.repository.TransactionRepositoryImpl
import com.georgeci.moneysurfer.data.sync.toDoc
import com.georgeci.moneysurfer.data.sync.toEntity
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

private const val WORKSPACE = "ws-1"
private const val ACCOUNT = "acc-1"
private const val TX = "tx-1"
private const val NOW = 1_700_000_000_000L

/**
 * What a tombstoned transaction does to sync, against the real Room schema and the real DTO
 * mappers (issue #346).
 *
 * The wire contract is unchanged by soft delete, and that is the point worth pinning: Firestore
 * has never accepted a document removal (`allow delete: if false` for regular flows), so a delete
 * has always replicated as a `deletedAt` patch. What changed is only the local side of it — the
 * row stays, so a peer's delete is now as recoverable as this device's own.
 *
 * `TransactionSyncPlugin` itself is not constructed here: it needs a `FirebaseFirestore`, which
 * gitlive cannot provide on a plain JVM. The two halves the plugin owns are exercised as the DAO
 * and mapper calls it makes — see `TombstonePatchSpec` for the patch shape and
 * `integration-test/androidDeviceTest/TombstonePushIT` for the round trip on a device.
 */
class TransactionTombstoneSyncIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness
    lateinit var outbox: RecordingOutbox
    lateinit var repository: TransactionRepositoryImpl

    beforeEach {
        harness = IntegrationHarness()
        outbox = RecordingOutbox()
        repository = TransactionRepositoryImpl(
            dao = harness.database.transactionDao(),
            outboxEnqueuer = outbox,
            clock = ClockUseCase(),
            timeFormatter = TimeFormatter(),
        )
        harness.database.userDao().insert(UserEntity(id = "owner-uid", displayName = "Owner", isAnon = false))
        harness.database.workspaceDao().insert(
            WorkspaceEntity(
                id = WORKSPACE,
                name = "WS",
                description = "",
                baseCurrency = "USD",
                ownerId = "owner-uid",
                createdAt = NOW,
                archived = false,
                updatedAt = NOW,
            ),
        )
        harness.database.accountDao().insert(
            AccountEntity(
                id = ACCOUNT,
                workspaceId = WORKSPACE,
                name = "Cash",
                type = "CASH",
                currency = "USD",
                balance = 0L,
            ),
        )
        harness.database.transactionDao().insert(localRow())
    }

    afterEach { harness.close() }

    // ── push ──────────────────────────────────────────────────────────────────

    "a soft delete enqueues one DELETE mutation, scoped to the workspace" {
        repository.delete(TransactionId(TX))

        outbox.enqueued shouldContainExactly listOf(
            Enqueued(SyncEntityTypes.TRANSACTION, TX, WORKSPACE, MutationOperation.DELETE),
        )
    }

    // Two Snackbars, two swipes on the same already-deleted row — whatever the cause, a second
    // tombstone would carry a later `deletedAt` than the one peers already agreed on.
    "deleting an already-tombstoned row enqueues nothing further" {
        repository.delete(TransactionId(TX))
        repository.delete(TransactionId(TX))

        outbox.enqueued.map { it.operation } shouldContainExactly listOf(MutationOperation.DELETE)
    }

    // An upsert, not another delete: the push writes the doc whole, so `deletedAt` goes back to
    // null remotely and peers see the row return rather than a second copy appear beside it.
    "an Undo enqueues an UPDATE that clears the tombstone remotely" {
        repository.delete(TransactionId(TX))

        repository.restore(TransactionId(TX)).shouldNotBeNull()

        outbox.enqueued.map { it.operation } shouldContainExactly listOf(
            MutationOperation.DELETE,
            MutationOperation.UPDATE,
        )
        harness.database.transactionDao().getById(TX).shouldNotBeNull().toDoc().deletedAt shouldBe null
    }

    "restoring a row that was never deleted pushes nothing" {
        repository.restore(TransactionId(TX)) shouldBe null

        outbox.enqueued.shouldBeEmpty()
    }

    "the pushed doc carries the tombstone while the row is deleted" {
        repository.delete(TransactionId(TX))

        val deleted = harness.database.transactionDao().getByIdIncludingDeleted(TX).shouldNotBeNull()
        deleted.toDoc().deletedAt shouldBe deleted.deletedAt
        // The delete has to look newer than the edit it supersedes, or LWW would undo it.
        deleted.updatedAt shouldBe deleted.deletedAt
    }

    // ── pull ──────────────────────────────────────────────────────────────────

    // What `TransactionSyncPlugin.applyDoc` does for a doc with `deletedAt != null`: the local row
    // is tombstoned rather than dropped, so the peer's delete is recoverable here too.
    "a pulled tombstone hides the row locally without dropping it" {
        val remoteDeletedAt = NOW + 5_000

        harness.database.transactionDao().softDelete(TX, remoteDeletedAt)

        harness.database.transactionDao().getById(TX) shouldBe null
        val tombstoned = harness.database.transactionDao().getByIdIncludingDeleted(TX).shouldNotBeNull()
        tombstoned.deletedAt shouldBe remoteDeletedAt
        repository.getAll().first().shouldBeEmpty()

        repository.restore(TransactionId(TX)).shouldNotBeNull()
        repository.getAll().first().map { it.id.value } shouldContainExactly listOf(TX)
    }

    // The pull applies a tombstone with a targeted UPDATE for exactly this reason: a device that
    // never held the row must not have one conjured out of a patch that carries no real fields.
    "a tombstone for a row this device never had creates nothing" {
        harness.database.transactionDao().softDelete("never-seen", NOW) shouldBe 0

        harness.database.transactionDao().getByIdIncludingDeleted("never-seen") shouldBe null
    }

    "the tombstone survives the wire in both directions" {
        val doc = localRow().copy(deletedAt = NOW).toDoc()

        doc.deletedAt shouldBe NOW
        doc.toEntity(id = TX, workspaceId = WORKSPACE).deletedAt shouldBe NOW
        TransactionDoc().toEntity(id = TX, workspaceId = WORKSPACE).deletedAt shouldBe null
    }
})

private fun localRow() = TransactionEntity(
    id = TX,
    workspaceId = WORKSPACE,
    accountId = ACCOUNT,
    amount = -4_000,
    currencyCode = "USD",
    categoryId = null,
    note = "coffee",
    operationAt = NOW,
    operationDate = "2026-03-15",
    type = "EXPENSE",
    createdAt = NOW,
    updatedAt = NOW,
)

private data class Enqueued(
    val entityType: String,
    val entityId: String,
    val scopeKey: String?,
    val operation: MutationOperation,
)

private class RecordingOutbox : OutboxEnqueuer {
    val enqueued = mutableListOf<Enqueued>()

    override suspend fun enqueueUpsert(
        entityType: String,
        entityId: String,
        scopeKey: String?,
        operation: MutationOperation,
    ) {
        enqueued += Enqueued(entityType, entityId, scopeKey, operation)
    }

    override suspend fun enqueueDelete(entityType: String, entityId: String, scopeKey: String?) {
        enqueued += Enqueued(entityType, entityId, scopeKey, MutationOperation.DELETE)
    }

    override suspend fun isEnabled(): Boolean = true
}
