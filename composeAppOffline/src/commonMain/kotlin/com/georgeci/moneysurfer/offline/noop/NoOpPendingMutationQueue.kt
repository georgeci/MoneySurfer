package com.georgeci.moneysurfer.offline.noop

import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import com.georgeci.moneysurfer.sync.repository.PendingMutationQueue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Offline build never enqueues outbox mutations — every queue read is empty
 * and every write is dropped. `pendingCount` emits a constant zero so any
 * UI badge stays hidden.
 */
class NoOpPendingMutationQueue : PendingMutationQueue {
    override suspend fun enqueue(mutation: PendingMutation) = Unit
    override suspend fun pending(scope: SyncScope, limit: Int): List<PendingMutation> = emptyList()
    override suspend fun markInFlight(ids: List<String>) = Unit
    override suspend fun markCompleted(ids: List<String>) = Unit
    override suspend fun markFailed(id: String, error: String) = Unit
    override val pendingCount: Flow<Int> = flowOf(0)
    override fun observeOutbox(limit: Int): Flow<List<PendingMutation>> = flowOf(emptyList())
}
