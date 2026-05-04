package com.georgeci.moneysurfer.sync.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.georgeci.moneysurfer.sync.db.dao.PendingMutationDao
import com.georgeci.moneysurfer.sync.db.dao.SyncMetaDao
import com.georgeci.moneysurfer.sync.db.entity.PendingMutationEntity
import com.georgeci.moneysurfer.sync.db.entity.SyncMetaEntity

@Database(
    entities = [
        SyncMetaEntity::class,
        PendingMutationEntity::class,
    ],
    version = 1,
)
@ConstructedBy(SyncDatabaseConstructor::class)
abstract class SyncDatabase : RoomDatabase() {
    abstract fun syncMetaDao(): SyncMetaDao
    abstract fun pendingMutationDao(): PendingMutationDao
}

@Suppress("KotlinNoActualForExpect")
expect object SyncDatabaseConstructor : RoomDatabaseConstructor<SyncDatabase> {
    override fun initialize(): SyncDatabase
}
