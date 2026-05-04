package com.georgeci.moneysurfer.sync.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Per-(workspace, collection) cursor + last-sync timestamps.
 *
 * No FK on [workspaceId] — sync_meta may exist for a workspace that has been
 * deleted locally (the cursor will be reused once the workspace is re-pulled).
 *
 * See sync.md §4.3.
 */
@Entity(
    tableName = "sync_meta",
    primaryKeys = ["workspaceId", "collection"],
)
data class SyncMetaEntity(
    @ColumnInfo(name = "workspaceId") val workspaceId: String,
    @ColumnInfo(name = "collection") val collection: String,
    @ColumnInfo(name = "lastPulledAt") val lastPulledAt: Long?,
    @ColumnInfo(name = "lastSyncSuccessAt") val lastSyncSuccessAt: Long?,
    @ColumnInfo(name = "lastSyncAttemptAt") val lastSyncAttemptAt: Long?,
)
