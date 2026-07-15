package com.georgeci.moneysurfer.data.sync.plugin
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.sync.plugin.EntityApplyResult
import com.georgeci.moneysurfer.sync.plugin.RemoteDocument
import com.georgeci.moneysurfer.sync.repository.ConflictResolution
import kotlinx.serialization.DeserializationStrategy
import kotlin.coroutines.cancellation.CancellationException

private val decodeLog = Logger.withTag("SyncDecode")

/** Result for a document a plugin intentionally did not apply (skipped, not a conflict). */
internal val SKIPPED_APPLY_RESULT = EntityApplyResult(applied = false, wasConflict = false)

/**
 * Decodes [deserializer] from this document, returning `null` instead of throwing when the
 * document is malformed — a field whose wire type does not match the DTO (e.g. `amount: "abc"`
 * where a `Long` is expected).
 *
 * Poison-document guard (issue #156). Entity docs are pulled and decoded on every co-member's
 * device. Any member could historically write a wrong-typed field because the Firestore rules
 * validated only membership, not field shape. Such a doc threw inside [RemoteDocument.decode]
 * mid-pull and aborted the whole batch BEFORE the cursor advanced past it, so every later
 * incremental pull re-fetched it and failed again — a workspace-wide sync outage that retried
 * forever. Returning `null` lets the caller skip the row (returning [SKIPPED_APPLY_RESULT], which
 * still advances the cursor), containing the blast radius to that one document. The rules now also
 * reject such writes at the source; this keeps already-poisoned docs and older clients — which the
 * rules cannot retroactively fix — from taking sync down.
 *
 * Only the in-memory decode is guarded: the document is already fetched, so a throw here is always
 * a malformed payload, never a transient I/O error (those still surface and abort so they retry).
 * [CancellationException] is rethrown so coroutine cancellation is never swallowed.
 */
internal fun <T> RemoteDocument.decodeOrNull(deserializer: DeserializationStrategy<T>): T? = try {
    decode(deserializer)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (@Suppress("TooGenericExceptionCaught") failure: Throwable) {
    decodeLog.e(throwable = failure) {
        "skipping undecodable ${deserializer.descriptor.serialName} doc id=$id"
    }
    null
}

internal suspend inline fun <T : Any> applyResolution(
    resolution: ConflictResolution<T>,
    crossinline write: suspend (T) -> Unit,
): EntityApplyResult = when (resolution) {
    is ConflictResolution.TakeRemote -> {
        write(resolution.value)
        EntityApplyResult(applied = true, wasConflict = false)
    }
    is ConflictResolution.Merged -> {
        write(resolution.value)
        EntityApplyResult(applied = true, wasConflict = true)
    }
    is ConflictResolution.TakeLocal -> EntityApplyResult(applied = false, wasConflict = true)
    ConflictResolution.Skip -> EntityApplyResult(applied = false, wasConflict = true)
}
