package com.georgeci.moneysurfer.integration.plugin

import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.data.db.entity.AccountEntity
import com.georgeci.moneysurfer.data.db.entity.CategoryEntity
import com.georgeci.moneysurfer.data.db.entity.TransactionEntity
import com.georgeci.moneysurfer.data.remote.CategoryDoc
import com.georgeci.moneysurfer.data.sync.plugin.CategorySyncPlugin
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

private const val CATEGORY = "cat-1"
private const val ACCOUNT = "acc-1"

private fun categoryDoc(updatedAt: Long = 200L, deletedAt: Long? = null) = CategoryDoc(
    name = "Remote name",
    type = "EXPENSE",
    createdAt = 1L,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    iconKey = "basket",
    hue = 120,
)

private fun categoryEntity(updatedAt: Long) = CategoryEntity(
    id = CATEGORY,
    workspaceId = PLUGIN_WORKSPACE_ID,
    name = "Local name",
    type = "EXPENSE",
    parentId = null,
    createdAt = 1L,
    updatedAt = updatedAt,
)

/**
 * The category plugin, both halves. The tombstone path is the interesting one: Firestore has no
 * cascade and Room's FK would refuse the delete, so the plugin has to unlink the transactions that
 * point at the category first — losing the row, not the spending it classified.
 */
class CategorySyncPluginIT : StringSpec({

    lateinit var database: MoneySurferDatabase
    lateinit var writer: RecordingDocumentWriter
    lateinit var plugin: CategorySyncPlugin

    suspend fun seedTransaction() {
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
        database.transactionDao().insertAll(
            listOf(
                TransactionEntity(
                    id = "t-1",
                    workspaceId = PLUGIN_WORKSPACE_ID,
                    accountId = ACCOUNT,
                    amount = -1_00L,
                    currencyCode = "USD",
                    categoryId = CATEGORY,
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
    }

    beforeEach {
        database = inMemoryRoomDatabase()
        writer = RecordingDocumentWriter()
        plugin = CategorySyncPlugin(
            writer = writer,
            appInfo = pluginAppInfo(),
            conflictResolver = LwwConflictResolver(),
            categoryDao = database.categoryDao(),
            transactionDao = database.transactionDao(),
        )
        database.seedPluginWorkspace()
    }

    afterEach { database.close() }

    "an insert writes the row under the workspace's categories collection" {
        database.categoryDao().insert(categoryEntity(updatedAt = 100L))

        plugin.push(mutationOf(SyncEntityTypes.CATEGORY, CATEGORY, MutationOperation.INSERT))

        writer.writes.single().path shouldBe "workspaces/$PLUGIN_WORKSPACE_ID/categories/$CATEGORY"
        writer.onlyWrite<CategoryDoc>().clientVersionCode shouldBe PLUGIN_VERSION_CODE
    }

    "an upsert for a category that is already gone writes nothing" {
        plugin.push(mutationOf(SyncEntityTypes.CATEGORY, CATEGORY, MutationOperation.UPDATE))

        writer.writes.shouldBeEmpty()
    }

    "a pulled category this device has never seen is inserted" {
        val result = plugin.applyDoc(
            StubRemoteDocument(CATEGORY, categoryDoc()),
            PLUGIN_WORKSPACE_ID,
        )

        result shouldBe EntityApplyResult(applied = true, wasConflict = false)
        database.categoryDao().getById(CATEGORY)?.name shouldBe "Remote name"
    }

    "a local edit that is newer than the remote one is kept" {
        database.categoryDao().insert(categoryEntity(updatedAt = 300L))

        val result = plugin.applyDoc(
            StubRemoteDocument(CATEGORY, categoryDoc(updatedAt = 200L)),
            PLUGIN_WORKSPACE_ID,
        )

        result shouldBe EntityApplyResult(applied = false, wasConflict = true)
        database.categoryDao().getById(CATEGORY)?.name shouldBe "Local name"
    }

    "a tombstone unlinks the category's transactions before dropping the row" {
        database.categoryDao().insert(categoryEntity(updatedAt = 100L))
        seedTransaction()

        val result = plugin.applyDoc(
            StubRemoteDocument(CATEGORY, categoryDoc(deletedAt = 400L)),
            PLUGIN_WORKSPACE_ID,
        )

        result shouldBe EntityApplyResult(applied = true, wasConflict = false)
        database.categoryDao().getById(CATEGORY).shouldBeNull()
        database.transactionDao().getById("t-1")?.categoryId.shouldBeNull()
    }

    "an undecodable document is skipped rather than taking the pull down" {
        val poison = StubRemoteDocument(CATEGORY) { throw SerializationException("hue: \"blue\"") }

        plugin.applyDoc(poison, PLUGIN_WORKSPACE_ID) shouldBe
            EntityApplyResult(applied = false, wasConflict = false)
    }
})
