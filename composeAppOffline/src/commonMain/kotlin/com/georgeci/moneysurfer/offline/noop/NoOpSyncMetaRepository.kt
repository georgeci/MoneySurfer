package com.georgeci.moneysurfer.offline.noop

import com.georgeci.moneysurfer.sync.repository.SyncMeta
import com.georgeci.moneysurfer.sync.repository.SyncMetaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Instant

/**
 * Offline build never pulls, so there is nothing to keep a cursor for: reads answer "no cursor",
 * writes are dropped, and [observe] emits a single empty list so the Sync screen's cursor section
 * renders its empty state instead of waiting on a flow that never emits.
 */
class NoOpSyncMetaRepository : SyncMetaRepository {
    override suspend fun cursor(scopeKey: String, collection: String): Instant? = null
    override suspend fun setCursor(scopeKey: String, collection: String, cursor: Instant) = Unit
    override suspend fun markAttempt(scopeKey: String, collection: String, at: Instant) = Unit
    override suspend fun markSuccess(scopeKey: String, collection: String, at: Instant) = Unit
    override suspend fun clearScope(scopeKey: String) = Unit
    override fun observe(scopeKey: String): Flow<List<SyncMeta>> = flowOf(emptyList())
}
