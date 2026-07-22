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
    // v2: pending_mutations dropped the separate status / createdAt indices for one
    // composite. Destructive fallback is fine here — the outbox is transient.
    version = 2,
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
