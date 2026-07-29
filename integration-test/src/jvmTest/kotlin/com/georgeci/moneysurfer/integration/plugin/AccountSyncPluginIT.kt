package com.georgeci.moneysurfer.integration.plugin

import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.data.db.entity.AccountEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionEntity
import com.georgeci.moneysurfer.data.remote.AccountDoc
import com.georgeci.moneysurfer.data.sync.plugin.AccountSyncPlugin
import com.georgeci.moneysurfer.data.sync.plugin.TombstonePatch
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.integration.fixtures.inMemoryRoomDatabase
import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
import com.georgeci.moneysurfer.sync.repository.LwwConflictResolver
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException

private const val ACCOUNT = "acc-1"
private const val ACCOUNT_PATH = "workspaces/$PLUGIN_WORKSPACE_ID/accounts/$ACCOUNT"

private fun accountDoc(updatedAt: Long = 200L, deletedAt: Long? = null) = AccountDoc(
    name = "Everyday",
    type = "BANK",
    currency = "EUR",
    balance = 1_000L,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

private fun accountEntity(updatedAt: Long, name: String = "Local name") = AccountEntity(
    id = ACCOUNT,
    workspaceId = PLUGIN_WORKSPACE_ID,
    name = name,
    type = "BANK",
    currency = "EUR",
    balance = 500L,
    updatedAt = updatedAt,
)

/**
 * Both halves of the account plugin against real SQLite and a recording writer: which document a
 * push addresses, and how a pulled one resolves against the local row.
 */
class AccountSyncPluginIT : StringSpec({

    lateinit var database: MoneySurferDatabase
    lateinit var writer: RecordingDocumentWriter
    lateinit var plugin: AccountSyncPlugin

    beforeEach {
        database = inMemoryRoomDatabase()
        writer = RecordingDocumentWriter()
        plugin = AccountSyncPlugin(
            writer = writer,
            appInfo = pluginAppInfo(),
            conflictResolver = LwwConflictResolver(),
            accountDao = database.accountDao(),
            transactionDao = database.transactionDao(),
        )
        database.seedPluginWorkspace()
    }

    afterEach { database.close() }

    // ── push ──────────────────────────────────────────────────────────────────

    "an insert writes the current row under the workspace's accounts collection" {
        database.accountDao().insert(accountEntity(updatedAt = 100L, name = "Everyday"))

        plugin.push(mutationOf(SyncEntityTypes.ACCOUNT, ACCOUNT, MutationOperation.INSERT))

        writer.writes.single().path shouldBe ACCOUNT_PATH
        writer.onlyWrite<AccountDoc>().name shouldBe "Everyday"
    }

    // The outbox row carries no payload, so whatever the row holds at drain time is what goes —
    // and it goes stamped with *this* build's version, which the Firestore rules check.
    "the pushed document carries this build's version code" {
        database.accountDao().insert(accountEntity(updatedAt = 100L))

        plugin.push(mutationOf(SyncEntityTypes.ACCOUNT, ACCOUNT, MutationOperation.UPDATE))

        writer.onlyWrite<AccountDoc>().clientVersionCode shouldBe PLUGIN_VERSION_CODE
    }

    // Created and deleted between two drains: the row is gone, so there is nothing to push. It has
    // to return normally — a throw would requeue an outbox row that can never drain.
    "an upsert for a row that is already gone writes nothing and does not fail" {
        plugin.push(mutationOf(SyncEntityTypes.ACCOUNT, ACCOUNT, MutationOperation.INSERT))

        writer.writes.shouldBeEmpty()
    }

    "a delete writes a tombstone patch stamped with the enqueue time" {
        database.accountDao().insert(accountEntity(updatedAt = 100L))
        plugin.push(mutationOf(SyncEntityTypes.ACCOUNT, ACCOUNT, MutationOperation.INSERT))

        plugin.push(mutationOf(SyncEntityTypes.ACCOUNT, ACCOUNT, MutationOperation.DELETE))

        val patch = writer.tombstones.single()
        patch.path shouldBe ACCOUNT_PATH
        patch.value shouldBe TombstonePatch(
            deletedAt = PLUGIN_ENQUEUED_AT.toEpochMilliseconds(),
            updatedAt = PLUGIN_ENQUEUED_AT.toEpochMilliseconds(),
            clientVersionCode = PLUGIN_VERSION_CODE,
        )
    }

    "a tombstone for a document that never reached the server is skipped" {
        plugin.push(mutationOf(SyncEntityTypes.ACCOUNT, ACCOUNT, MutationOperation.DELETE))

        writer.tombstones.shouldBeEmpty()
    }

    // ── pull ──────────────────────────────────────────────────────────────────

    "an account this device has never seen is inserted from the document" {
        val result = plugin.applyDoc(StubRemoteDocument(ACCOUNT, accountDoc()), PLUGIN_WORKSPACE_ID)

        result shouldBe EntityApplyResult(applied = true, wasConflict = false)
        database.accountDao().getById(ACCOUNT)?.name shouldBe "Everyday"
    }

    "a newer remote row wins over the local one" {
        database.accountDao().insert(accountEntity(updatedAt = 100L))

        plugin.applyDoc(
            StubRemoteDocument(ACCOUNT, accountDoc(updatedAt = 200L)),
            PLUGIN_WORKSPACE_ID,
        )

        database.accountDao().getById(ACCOUNT)?.name shouldBe "Everyday"
    }

    "a local row edited more recently is kept, and the clash is reported" {
        database.accountDao().insert(accountEntity(updatedAt = 300L))

        val result = plugin.applyDoc(
            StubRemoteDocument(ACCOUNT, accountDoc(updatedAt = 200L)),
            PLUGIN_WORKSPACE_ID,
        )

        result shouldBe EntityApplyResult(applied = false, wasConflict = true)
        database.accountDao().getById(ACCOUNT)?.name shouldBe "Local name"
    }

    // Firestore has no cascade, so the plugin drops the account's transactions itself — leaving
    // them would strand rows pointing at an account that no longer exists.
    "a tombstone deletes the account and its transactions" {
        database.accountDao().insert(accountEntity(updatedAt = 100L))
        database.transactionDao().insertAll(
            listOf(
                TransactionEntity(
                    id = "t-1",
                    workspaceId = PLUGIN_WORKSPACE_ID,
                    accountId = ACCOUNT,
                    amount = -1_00L,
                    currencyCode = "EUR",
                    categoryId = PLUGIN_CATEGORY_ID,
                    note = "",
                    merchant = "",
                    operationAt = 1L,
                    operationDate = "2025-01-01",
                    type = "EXPENSE",
                    status = "ACTUAL",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )

        val result = plugin.applyDoc(
            StubRemoteDocument(ACCOUNT, accountDoc(deletedAt = 400L)),
            PLUGIN_WORKSPACE_ID,
        )

        result shouldBe EntityApplyResult(applied = true, wasConflict = false)
        database.accountDao().getById(ACCOUNT).shouldBeNull()
        database.transactionDao().getById("t-1").shouldBeNull()
    }

    "a tombstone for an account this device never had is applied without failing" {
        val result = plugin.applyDoc(
            StubRemoteDocument(ACCOUNT, accountDoc(deletedAt = 400L)),
            PLUGIN_WORKSPACE_ID,
        )

        result shouldBe EntityApplyResult(applied = true, wasConflict = false)
    }

    // Issue #156: one undecodable document must not abort the batch before the cursor advances.
    "an undecodable document is skipped rather than taking the pull down" {
        val poison = StubRemoteDocument(ACCOUNT) { throw SerializationException("balance: \"abc\"") }

        val result = plugin.applyDoc(poison, PLUGIN_WORKSPACE_ID)

        result shouldBe EntityApplyResult(applied = false, wasConflict = false)
        database.accountDao().getById(ACCOUNT).shouldBeNull()
    }
})
