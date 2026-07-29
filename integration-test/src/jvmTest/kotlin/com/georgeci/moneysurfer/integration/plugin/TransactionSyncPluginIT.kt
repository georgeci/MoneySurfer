package com.georgeci.moneysurfer.integration.plugin

import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.data.db.entity.AccountEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionEntity
import com.georgeci.moneysurfer.data.remote.TransactionDoc
import com.georgeci.moneysurfer.data.sync.plugin.TransactionSyncPlugin
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.integration.fixtures.inMemoryRoomDatabase
import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
import com.georgeci.moneysurfer.sync.repository.LwwConflictResolver
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private const val TRANSACTION = "t-1"
private const val ACCOUNT = "acc-1"

private fun transactionDoc(
    updatedAt: Long = 200L,
    deletedAt: Long? = null,
    note: String = "Remote note",
) = TransactionDoc(
    accountId = ACCOUNT,
    categoryId = PLUGIN_CATEGORY_ID,
    amount = -1_00L,
    currencyCode = "USD",
    note = note,
    operationAt = 1L,
    operationDate = "2025-01-01",
    type = "EXPENSE",
    createdAt = 1L,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun transactionEntity(updatedAt: Long) = TransactionEntity(
    id = TRANSACTION,
    workspaceId = PLUGIN_WORKSPACE_ID,
    accountId = ACCOUNT,
    amount = -1_00L,
    currencyCode = "USD",
    categoryId = PLUGIN_CATEGORY_ID,
    note = "Local note",
    merchant = "",
    operationAt = 1L,
    operationDate = "2025-01-01",
    type = "EXPENSE",
    status = "ACTUAL",
    createdAt = 1L,
    updatedAt = updatedAt,
)

/**
 * Transactions are the one entity whose pulled tombstone marks the row deleted instead of dropping
 * it (issue #346), and the one whose conflict check has to look at tombstoned rows — a locally
 * deleted row that read as "no local copy" would be resurrected by an older remote doc.
 */
class TransactionSyncPluginIT : StringSpec({

    lateinit var database: MoneySurferDatabase
    lateinit var writer: RecordingDocumentWriter
    lateinit var plugin: TransactionSyncPlugin

    beforeEach {
        database = inMemoryRoomDatabase()
        writer = RecordingDocumentWriter()
        plugin = TransactionSyncPlugin(
            writer = writer,
            appInfo = pluginAppInfo(),
            conflictResolver = LwwConflictResolver(),
            transactionDao = database.transactionDao(),
        )
        database.seedPluginWorkspace()
        database.accountDao().insert(
            AccountEntity(
                id = ACCOUNT,
                workspaceId = PLUGIN_WORKSPACE_ID,
                name = "Cash",
                type = "CASH",
                currency = "USD",
                balance = 0L,
            ),
        )
    }

    afterEach { database.close() }

    "an insert writes the row under the workspace's transactions collection" {
        database.transactionDao().insertAll(listOf(transactionEntity(updatedAt = 100L)))

        plugin.push(mutationOf(SyncEntityTypes.TRANSACTION, TRANSACTION, MutationOperation.INSERT))

        writer.writes.single().path shouldBe
            "workspaces/$PLUGIN_WORKSPACE_ID/transactions/$TRANSACTION"
        writer.onlyWrite<TransactionDoc>().note shouldBe "Local note"
    }

    // `getById` hides tombstoned rows, so a locally deleted transaction reads as absent here and
    // the push is a no-op — which is exactly why the delete arrives as its own outbox row.
    "an upsert for a locally deleted row writes nothing" {
        database.transactionDao().insertAll(listOf(transactionEntity(updatedAt = 100L)))
        database.transactionDao().softDelete(TRANSACTION, deletedAt = 150L)

        plugin.push(mutationOf(SyncEntityTypes.TRANSACTION, TRANSACTION, MutationOperation.UPDATE))

        writer.writes.shouldBeEmpty()
    }

    "a pulled transaction this device has never seen is inserted" {
        val result = plugin.applyDoc(
            StubRemoteDocument(TRANSACTION, transactionDoc()),
            PLUGIN_WORKSPACE_ID,
        )

        result shouldBe EntityApplyResult(applied = true, wasConflict = false)
        database.transactionDao().getById(TRANSACTION)?.note shouldBe "Remote note"
    }

    "a pulled tombstone marks the row deleted rather than dropping it" {
        database.transactionDao().insertAll(listOf(transactionEntity(updatedAt = 100L)))

        val result = plugin.applyDoc(
            StubRemoteDocument(TRANSACTION, transactionDoc(deletedAt = 400L)),
            PLUGIN_WORKSPACE_ID,
        )

        result shouldBe EntityApplyResult(applied = true, wasConflict = false)
        database.transactionDao().getById(TRANSACTION).shouldBeNull()
        database.transactionDao().getByIdIncludingDeleted(TRANSACTION)?.deletedAt shouldBe 400L
    }

    // The row the user just deleted must not come back because a peer's older copy is still on the
    // server: the resolver has to see the tombstone as the local state, not as "nothing here".
    "an older remote doc does not resurrect a locally deleted row" {
        database.transactionDao().insertAll(listOf(transactionEntity(updatedAt = 100L)))
        database.transactionDao().softDelete(TRANSACTION, deletedAt = 500L)

        val result = plugin.applyDoc(
            StubRemoteDocument(TRANSACTION, transactionDoc(updatedAt = 200L)),
            PLUGIN_WORKSPACE_ID,
        )

        result shouldBe EntityApplyResult(applied = false, wasConflict = true)
        database.transactionDao().getById(TRANSACTION).shouldBeNull()
    }

    // The other direction still goes through last-writer-wins: a peer's edit made after the
    // delete legitimately outranks it.
    "a newer remote edit clears the local tombstone" {
        database.transactionDao().insertAll(listOf(transactionEntity(updatedAt = 100L)))
        database.transactionDao().softDelete(TRANSACTION, deletedAt = 200L)

        plugin.applyDoc(
            StubRemoteDocument(TRANSACTION, transactionDoc(updatedAt = 900L, note = "Edited")),
            PLUGIN_WORKSPACE_ID,
        )

        database.transactionDao().getById(TRANSACTION)?.note shouldBe "Edited"
    }
})
