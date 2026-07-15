package com.georgeci.moneysurfer.data.sync.plugin

import com.georgeci.moneysurfer.sync.repository.PendingMutation
import dev.gitlive.firebase.firestore.DocumentReference
import kotlinx.serialization.Serializable

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
internal data class TombstonePatch(
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
 * Writes [patch] onto the remote doc, skipping docs that never reached
 * Firestore: an entity created and deleted between two drains leaves the
 * INSERT push a no-op (the Room row is already gone), and updating the
 * missing doc would raise NOT_FOUND on every retry, wedging the batch.
 * A doc that never existed remotely has nothing for peers to forget.
 */
internal suspend fun DocumentReference.pushTombstone(patch: TombstonePatch) {
    if (!get().exists) return
    update(patch)
}
