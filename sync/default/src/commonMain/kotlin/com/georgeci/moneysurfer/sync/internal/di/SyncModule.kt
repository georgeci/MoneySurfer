package com.georgeci.moneysurfer.sync.internal.di

import com.georgeci.moneysurfer.sync.db.SyncDatabase
import com.georgeci.moneysurfer.sync.db.dao.PendingMutationDao
import com.georgeci.moneysurfer.sync.db.dao.SyncMetaDao
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("com.georgeci.moneysurfer.sync")
class SyncModule {

    @Single
    fun syncMetaDao(db: SyncDatabase): SyncMetaDao = db.syncMetaDao()

    @Single
    fun pendingMutationDao(db: SyncDatabase): PendingMutationDao = db.pendingMutationDao()
}
