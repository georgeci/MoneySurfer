package com.georgeci.moneysurfer.data.sync.plugin

import com.georgeci.moneysurfer.data.sync.WorkspaceDocRef
import com.georgeci.moneysurfer.data.sync.WorkspaceDocumentWriter
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy

/**
 * Wire-format patch pushed for [com.georgeci.moneysurfer.sync.repository.MutationOperation.DELETE].
 *
 * Firestore rules deny hard deletes on every entity collection
 * (`allow delete: if false`, firestore.rules v2.0.0), so a local delete
 * replicates as a soft-delete `update`. Field names must match the entity DTOs
 * in RemoteDtos.kt: peers decode `deletedAt != null` and hard-delete the local
 * row, and their `updatedAt > cursor` pull picks the tombstone up
 * (see sync-pull-lww.md → Tombstones).
 */
@Serializable
data class TombstonePatch(
    val deletedAt: Long,
    val updatedAt: Long,
    val clientVersionCode: Int,
)

/**
 * [TombstonePatch.deletedAt] and [TombstonePatch.updatedAt] both take the
 * mutation's enqueue time, so a retried push writes an identical patch —
 * matching the idempotent re-push contract the rest of the outbox relies on.
 */
internal fun tombstonePatchFor(
    mutation: PendingMutation,
    clientVersionCode: Int,
): TombstonePatch {
    val deletedAtMillis = mutation.createdAt.toEpochMilliseconds()
    return TombstonePatch(
        deletedAt = deletedAtMillis,
        updatedAt = deletedAtMillis,
        clientVersionCode = clientVersionCode,
    )
}

/**
 * The push half every workspace sub-collection plugin shares: an upsert writes whatever [doc]
 * resolves to now, and a delete writes a tombstone patch instead — Firestore rules deny hard
 * deletes.
 *
 * [doc] returning null means the row is no longer there to push. That is the same "created and
 * deleted between two drains" case the tombstone skip covers from the other side, and it must
 * return normally: `UploadPendingChangesUseCaseImpl` treats a push that throws as undelivered and
 * requeues it, which for a row that will never come back is a queue entry that never drains.
 */
internal suspend fun <T : Any> WorkspaceDocumentWriter.pushToCollection(
    mutation: PendingMutation,
    collectionName: String,
    clientVersionCode: Int,
    strategy: SerializationStrategy<T>,
    doc: suspend (WorkspaceDocRef) -> T?,
) {
    val ref = WorkspaceDocRef.inCollection(
        workspaceId = requireNotNull(mutation.scopeKey) {
            "$collectionName mutation ${mutation.entityId} has no workspace scope"
        },
        collectionName = collectionName,
        documentId = mutation.entityId,
    )
    when (mutation.operation) {
        MutationOperation.INSERT,
        MutationOperation.UPDATE,
        -> doc(ref)?.let { set(ref, strategy, it) }
        MutationOperation.DELETE ->
            tombstone(ref, tombstonePatchFor(mutation, clientVersionCode))
    }
}
