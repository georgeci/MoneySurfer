package com.georgeci.moneysurfer.offline.noop

import com.georgeci.moneysurfer.domain.repositories.RemoteDataResetRepository

/**
 * Offline build has no sync database, so there are no pending mutations or
 * cursor metadata to clear on logout.
 */
class NoOpRemoteDataResetRepository : RemoteDataResetRepository {
    override suspend fun clearAll() = Unit
}
