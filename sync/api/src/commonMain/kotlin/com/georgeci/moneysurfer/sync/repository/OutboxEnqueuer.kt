package com.georgeci.moneysurfer.sync.repository

/**
 * Outbox writer used by data-layer repositories to enqueue local mutations
 * for deferred push to the remote backend.
 *
 * The implementation guards against demo mode and non-Firebase sessions —
 * callers do not need to check [isEnabled] before calling [enqueueUpsert] /
 * [enqueueDelete]; the impl silently no-ops when sync is not allowed.
 *
 * [entityType] — constant from [com.georgeci.moneysurfer.domain.sync.SyncEntityTypes].
 * [scopeKey]   — workspace id for subcollection entities, null for root entities.
 *
 * See sync.md §4.3.
 */
interface OutboxEnqueuer {

    suspend fun enqueueUpsert(
        entityType: String,
        entityId: String,
        scopeKey: String?,
        operation: MutationOperation,
    )

    suspend fun enqueueDelete(
        entityType: String,
        entityId: String,
        scopeKey: String?,
    )

    /** Returns false for demo / signed-out sessions — mutations are local-only. */
    suspend fun isEnabled(): Boolean
}
