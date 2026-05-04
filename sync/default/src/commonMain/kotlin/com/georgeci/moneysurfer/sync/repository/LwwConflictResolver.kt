package com.georgeci.moneysurfer.sync.repository

import org.koin.core.annotation.Single
import kotlin.time.Instant

/**
 * Last-Write-Wins resolver — picks the side with the newer `updatedAt`.
 *
 * No local row → remote wins.
 * Equal timestamps → local wins (stable; remote re-pulls have no effect).
 *
 * See sync.md §2.2 / §4.7 «Сценарий 2».
 */
@Single(binds = [ConflictResolver::class])
class LwwConflictResolver : ConflictResolver {

    override fun <T : Any> resolve(
        local: T?,
        remote: T,
        metadata: ConflictMetadata,
    ): ConflictResolution<T> {
        if (local == null) return ConflictResolution.TakeRemote(remote)
        val localUpdatedAt = metadata.localUpdatedAt ?: Instant.DISTANT_PAST
        return if (metadata.remoteUpdatedAt > localUpdatedAt) {
            ConflictResolution.TakeRemote(remote)
        } else {
            ConflictResolution.TakeLocal(local)
        }
    }
}
