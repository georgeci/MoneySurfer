package com.georgeci.moneysurfer.sync.repository

import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/**
 * Per-(scopeKey, collection) sync metadata.
 *
 * - [cursor] — `lastPulledAt` value used as `WHERE updatedAt > cursor` filter
 *   in the next pull. Updated only when remote rows are successfully written
 *   to local Room within the same transaction.
 * - [lastSyncSuccessAt] / [lastSyncAttemptAt] — timestamps for the UI badge.
 *
 * [scopeKey] is typically a workspace id string. [collection] is an opaque
 * Firestore collection name string (e.g. "accounts", "transactions").
 *
 * See SyncCoordinatorFAQ.md №14, sync.md §4.3.
 */
interface SyncMetaRepository {
    suspend fun cursor(scopeKey: String, collection: String): Instant?
    suspend fun setCursor(scopeKey: String, collection: String, cursor: Instant)
    suspend fun markAttempt(scopeKey: String, collection: String, at: Instant)
    suspend fun markSuccess(scopeKey: String, collection: String, at: Instant)
    suspend fun clearScope(scopeKey: String)
    fun observe(scopeKey: String): Flow<List<SyncMeta>>
}

data class SyncMeta(
    val scopeKey: String,
    val collection: String,
    val lastPulledAt: Instant?,
    val lastSyncSuccessAt: Instant?,
    val lastSyncAttemptAt: Instant?,
)
