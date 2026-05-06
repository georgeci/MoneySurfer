package com.georgeci.moneysurfer.sync.repository

import com.georgeci.moneysurfer.domain.repositories.RemoteDataResetRepository
import com.georgeci.moneysurfer.sync.db.dao.PendingMutationDao
import com.georgeci.moneysurfer.sync.db.dao.SyncMetaDao
import org.koin.core.annotation.Single

@Single(binds = [RemoteDataResetRepository::class])
class RemoteDataResetRepositoryImpl(
    private val pendingMutationDao: PendingMutationDao,
    private val syncMetaDao: SyncMetaDao,
) : RemoteDataResetRepository {
    override suspend fun clearAll() {
        pendingMutationDao.deleteAll()
        syncMetaDao.deleteAll()
    }
}
