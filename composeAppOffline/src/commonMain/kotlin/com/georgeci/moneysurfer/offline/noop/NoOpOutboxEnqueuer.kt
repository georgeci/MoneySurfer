package com.georgeci.moneysurfer.offline.noop

import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer

/**
 * Offline build has no remote sync, so every outbox call is a silent no-op.
 * `isEnabled()` returns false so any call sites that gate behaviour on it (e.g.
 * showing sync UI affordances) treat the session as local-only.
 */
class NoOpOutboxEnqueuer : OutboxEnqueuer {
    override suspend fun enqueueUpsert(
        entityType: String,
        entityId: String,
        scopeKey: String?,
        operation: MutationOperation,
    ) = Unit

    override suspend fun enqueueDelete(
        entityType: String,
        entityId: String,
        scopeKey: String?,
    ) = Unit

    override suspend fun isEnabled(): Boolean = false
}
