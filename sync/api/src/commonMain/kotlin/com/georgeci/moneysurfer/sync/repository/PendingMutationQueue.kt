package com.georgeci.moneysurfer.sync.repository

import com.georgeci.moneysurfer.sync.api.SyncScope
import kotlinx.coroutines.flow.Flow

/**
 * Outbox of local mutations waiting to be pushed to the remote backend.
 *
 * Lifecycle of a row:
 * 1. [enqueue] — produced by a repository's dual-write, right after the local write. **Not** in the
 *    same transaction, and no transaction can make it so: the outbox lives in `SyncDatabase`
 *    (`moneysurfer_sync.db`), a different database from `MoneySurferDatabase`, so the two writes are
 *    always two sequential calls and a crash in between leaves a local change with nothing queued
 *    for it. What closes that window is reconciliation at session start, not atomicity.
 *    Enqueueing is insert-if-absent among `PENDING` rows with the same type, id, operation **and
 *    scope**, so repeated edits of one entity queue one push rather than N identical ones while the
 *    same id under two workspaces still queues two. Status = `PENDING`.
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

    companion object {
        const val DEFAULT_BATCH_LIMIT: Int = 100
    }
}
