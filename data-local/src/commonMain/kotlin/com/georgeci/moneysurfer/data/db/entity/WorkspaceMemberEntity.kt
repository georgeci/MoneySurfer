package com.georgeci.moneysurfer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

// No FK on workspaceId — accepting an incoming invite inserts a member row BEFORE the
// recipient has synced the workspace locally (the workspace pull is triggered after the
// member row exists, since `users.workspaceIds` doesn't include the new workspace until
// then). The previous FK triggered SQLite error 787 on accept. Same trade-off as
// `workspace_invites`: referential integrity loss is fine because workspaces are never
// hard-deleted; member rows for purged workspaces are cleaned up by the sync layer
// (CLEAR_MEMBERS step in WorkspaceSyncRepositoryImpl) rather than by FK cascade.
@Entity(
    tableName = "workspace_members",
    primaryKeys = ["userId", "workspaceId"],
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
        ),
    ],
    indices = [Index("userId"), Index("workspaceId"), Index("status")],
)
data class WorkspaceMemberEntity(
    @ColumnInfo(name = "userId") val userId: String,
    @ColumnInfo(name = "workspaceId") val workspaceId: String,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "status", defaultValue = "ACTIVE") val status: String = "ACTIVE",
    @ColumnInfo(name = "displayName", defaultValue = "") val displayName: String = "",
    @ColumnInfo(name = "email") val email: String? = null,
    @ColumnInfo(name = "addedByUserId") val addedByUserId: String? = null,
    @ColumnInfo(name = "createdAt", defaultValue = "0") val createdAt: Long,
    @ColumnInfo(name = "updatedAt", defaultValue = "0") val updatedAt: Long = 0L,
    @ColumnInfo(name = "leftAt") val leftAt: Long? = null,
    @ColumnInfo(name = "removedAt") val removedAt: Long? = null,
)
