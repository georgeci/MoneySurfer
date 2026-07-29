package com.georgeci.moneysurfer.integration.plugin

import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.data.db.entity.CategoryEntity
import com.georgeci.moneysurfer.data.db.entity.UserEntity
import com.georgeci.moneysurfer.data.db.entity.WorkspaceEntity
import com.georgeci.moneysurfer.data.sync.WorkspaceDocRef
import com.georgeci.moneysurfer.data.sync.WorkspaceDocumentWriter
import com.georgeci.moneysurfer.data.sync.plugin.TombstonePatch
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.sync.plugin.RemoteDocument
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlin.time.Instant

internal const val PLUGIN_WORKSPACE_ID = "ws-1"
internal const val PLUGIN_OWNER_ID = "u-1"
internal const val PLUGIN_CATEGORY_ID = "c-1"
internal const val PLUGIN_VERSION_CODE = 42
internal val PLUGIN_ENQUEUED_AT: Instant = Instant.fromEpochMilliseconds(1_700_000_000_000L)

internal fun pluginAppInfo(versionCode: Int = PLUGIN_VERSION_CODE): AppInfo =
    AppInfo(version = "1.0.0", versionCode = versionCode)

/** One document a plugin handed the writer. */
internal data class RemoteWrite(val path: String, val value: Any)

/**
 * Records what a push addressed and what it put there, instead of reaching Firestore.
 *
 * [existingDocs] is what a tombstone consults: the real writer skips a document that never reached
 * the server, because updating a missing doc raises NOT_FOUND on every retry and wedges the drain.
 */
internal class RecordingDocumentWriter : WorkspaceDocumentWriter {

    val writes: MutableList<RemoteWrite> = mutableListOf()
    val tombstones: MutableList<RemoteWrite> = mutableListOf()
    val existingDocs: MutableSet<String> = mutableSetOf()

    override suspend fun <T : Any> set(
        ref: WorkspaceDocRef,
        strategy: SerializationStrategy<T>,
        value: T,
    ) {
        writes += RemoteWrite(ref.path, value)
        existingDocs += ref.path
    }

    override suspend fun tombstone(ref: WorkspaceDocRef, patch: TombstonePatch) {
        if (ref.path !in existingDocs) return
        tombstones += RemoteWrite(ref.path, patch)
    }

    /** The single document written, for the common one-write assertion. */
    inline fun <reified T> onlyWrite(): T = writes.single().value as T
}

/** An outbox row as the drain hands it to a plugin. */
internal fun mutationOf(
    entityType: String,
    entityId: String,
    operation: MutationOperation,
    scopeKey: String? = PLUGIN_WORKSPACE_ID,
) = PendingMutation(
    id = "m-$entityId-${operation.name}",
    entityType = entityType,
    entityId = entityId,
    operation = operation,
    scopeKey = scopeKey,
    createdAt = PLUGIN_ENQUEUED_AT,
    attempts = 0,
    lastError = null,
)

/** Foreign keys are enforced, so the workspace and its parents have to exist before a pull lands. */
internal suspend fun MoneySurferDatabase.seedPluginWorkspace() {
    userDao().insert(UserEntity(id = PLUGIN_OWNER_ID, displayName = "Owner", isAnon = false))
    workspaceDao().insert(
        WorkspaceEntity(
            id = PLUGIN_WORKSPACE_ID,
            name = "WS",
            description = "",
            baseCurrency = "USD",
            ownerId = PLUGIN_OWNER_ID,
            createdAt = 1L,
            archived = false,
            updatedAt = 1L,
        ),
    )
    categoryDao().insert(
        CategoryEntity(
            id = PLUGIN_CATEGORY_ID,
            workspaceId = PLUGIN_WORKSPACE_ID,
            name = "Groceries",
            type = "EXPENSE",
            parentId = null,
            createdAt = 1L,
        ),
    )
}

/**
 * A pulled document that hands back [payload] as-is. Decoding from the wire is gitlive's job and
 * is covered by `DecodeOrNullSpec` in `:sync-surfer`; what these specs vary is the DTO — or the
 * failure — the plugin is given.
 */
internal class StubRemoteDocument(
    override val id: String,
    private val payload: () -> Any?,
) : RemoteDocument {

    constructor(id: String, payload: Any) : this(id, { payload })

    @Suppress("UNCHECKED_CAST")
    override fun <T> decode(deserializer: DeserializationStrategy<T>): T = payload() as T

    override fun getLong(field: String): Long? = null
}
