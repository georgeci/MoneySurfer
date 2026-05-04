package com.georgeci.moneysurfer.sync.repository

import kotlin.time.Instant

/**
 * Strategy for combining a local and a remote version of the same entity.
 *
 * Phase 2 ships only LWW (last-write-wins) by `updatedAt`. Future strategies
 * (`Skip` for blocking inbox, `Merged` for field-level merges) plug in here.
 *
 * See SyncCoordinatorFAQ.md №5, sync.md §4.2.
 */
interface ConflictResolver {
    fun <T : Any> resolve(
        local: T?,
        remote: T,
        metadata: ConflictMetadata,
    ): ConflictResolution<T>
}

data class ConflictMetadata(
    val entityType: String,
    val entityId: String,
    val localUpdatedAt: Instant?,
    val remoteUpdatedAt: Instant,
)

sealed interface ConflictResolution<out T> {
    data class TakeRemote<T>(val value: T) : ConflictResolution<T>
    data class TakeLocal<T>(val value: T) : ConflictResolution<T>
    data class Merged<T>(val value: T) : ConflictResolution<T>

    /** Defer decision to a manual-resolution inbox (not implemented in Phase 2). */
    data object Skip : ConflictResolution<Nothing>
}
