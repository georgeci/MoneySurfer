package com.georgeci.moneysurfer.sync.repository

import com.georgeci.moneysurfer.sync.api.SyncScope
import kotlinx.coroutines.flow.Flow

/**
 * Outbox of local mutations waiting to be pushed to the remote backend.
 *
 * Lifecycle of a row:
 * 1. [enqueue] — produced by a repository's dual-write inside the same Room transaction
 *    that wrote the entity. Status = `PENDING`.
 * 2. [pending] — read by [com.georgeci.moneysurfer.sync.usecase.UploadPendingChangesUseCase]
 *    when sync runs. Caller then flips status via [markInFlight].
 * 3. [markCompleted] — successful push removes the row.
 * 4. [markFailed] — push exception bumps `attempts` and resets status to `PENDING`
 *    (next sync cycle picks it up again, possibly with backoff in a later phase).
 *
 * See SyncCoordinatorFAQ.md №13.
 */
interface PendingMutationQueue {
    suspend fun enqueue(mutation: PendingMutation)

    suspend fun pending(scope: SyncScope, limit: Int = DEFAULT_BATCH_LIMIT): List<PendingMutation>

    suspend fun markInFlight(ids: List<String>)

    suspend fun markCompleted(ids: List<String>)

    suspend fun markFailed(id: String, error: String)

    val pendingCount: Flow<Int>

    /**
     * Live view of the outbox rows themselves, oldest first — the Settings → Sync screen's
     * outbox section. Unlike [pendingCount] this includes rows currently `IN_FLIGHT`: a push
     * that never completes leaves the row in that status, and hiding it would make a stuck
     * outbox look like an empty one.
     *
     * Diagnostics only — the push path reads [pending] instead, because it needs the scope
     * filter and the `markInFlight` handshake that goes with it.
     */
    fun observeOutbox(limit: Int = DEFAULT_OUTBOX_LIMIT): Flow<List<PendingMutation>>

    companion object {
        const val DEFAULT_BATCH_LIMIT: Int = 100

        /** Enough rows to diagnose a stuck outbox without rendering thousands of them. */
        const val DEFAULT_OUTBOX_LIMIT: Int = 50
    }
}
