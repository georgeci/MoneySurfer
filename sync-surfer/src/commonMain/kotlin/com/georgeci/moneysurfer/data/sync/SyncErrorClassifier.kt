package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.sync.api.SyncError
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Maps SDK / runtime exceptions into a typed [SyncError].
 *
 * Lives in `:data` because only here do we see vendor-specific classes
 * (Firestore, Room). The coordinator and use case interfaces stay
 * vendor-neutral. See sync.md §4.2 «Конвенция error handling».
 */
internal fun Throwable.toSyncError(): SyncError = when (this) {
    is CancellationException -> SyncError.Cancelled
    is FirebaseFirestoreException -> classifyFirestore(this)
    is SerializationException -> SyncError.Unknown(this)
    else -> SyncError.Unknown(this)
}

/**
 * A denial, as opposed to "not here *yet*". The distinction decides whether a caller may skip what
 * it was reading: a denied read will never succeed for this user, while a network drop or an
 * expired token would turn skipping into a sync that reports success and downloaded nothing.
 *
 * The gitlive wrapper does not surface a typed `FirebaseFirestoreException` uniformly across
 * platforms, so this falls back to the message the same way [toSyncError] does.
 */
internal fun Throwable.isPermissionDenied(): Boolean =
    toSyncError() == SyncError.PermissionDenied ||
        PERMISSION_DENIED_MARKER in message?.uppercase().orEmpty()

private const val PERMISSION_DENIED_MARKER: String = "PERMISSION_DENIED"

private fun classifyFirestore(error: FirebaseFirestoreException): SyncError {
    // gitlive does not surface a typed Code enum across all platforms uniformly,
    // so fall back to message inspection until we wire a dedicated mapper per
    // platform. Expand this when Phase 2 adds richer Firestore exception
    // handling (quota, server timestamps).
    val message = error.message?.lowercase() ?: return SyncError.Unknown(error)
    return when {
        "unauthenticated" in message || "auth" in message -> SyncError.AuthRequired
        "permission" in message -> SyncError.PermissionDenied
        "unavailable" in message ||
            "network" in message ||
            "deadline" in message ||
            "timeout" in message -> SyncError.NetworkUnavailable
        else -> SyncError.Unknown(error)
    }
}
