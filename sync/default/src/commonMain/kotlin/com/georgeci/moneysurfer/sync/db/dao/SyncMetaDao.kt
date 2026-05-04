package com.georgeci.moneysurfer.sync.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.georgeci.moneysurfer.sync.db.entity.SyncMetaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncMetaDao {

    @Query(
        """
        SELECT * FROM sync_meta
        WHERE workspaceId = :workspaceId AND collection = :collection
        """,
    )
    suspend fun get(workspaceId: String, collection: String): SyncMetaEntity?

    @Upsert
    suspend fun upsert(entity: SyncMetaEntity)

    @Query("SELECT * FROM sync_meta WHERE workspaceId = :workspaceId")
    fun observeByWorkspace(workspaceId: String): Flow<List<SyncMetaEntity>>

    @Query("DELETE FROM sync_meta WHERE workspaceId = :workspaceId")
    suspend fun deleteByWorkspace(workspaceId: String)

    @Query("DELETE FROM sync_meta")
    suspend fun deleteAll()
}
