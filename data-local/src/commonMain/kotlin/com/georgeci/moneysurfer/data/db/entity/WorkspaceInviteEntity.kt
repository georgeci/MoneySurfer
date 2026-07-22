package com.georgeci.moneysurfer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// No FK on workspaceId — incoming invites address workspaces the recipient is NOT a
// member of (the whole point of an invite), so the parent workspace row may not exist
// in local Room when the invite is upserted from a collection-group pull. The FK
// previously triggered SQLite error 787 (FOREIGN KEY constraint failed) on the
// recipient device. Referential integrity for member-side invite cleanup is enforced
// at the workspace-pull layer (CLEAR_INVITES step) rather than via cascade.
@Entity(
    tableName = "workspace_invites",
    // Single-column workspaceId / email indices are omitted: they are leftmost
    // prefixes of the composites below, which SQLite uses for prefix-only lookups
    // too. A standalone `status` index is pointless at three distinct values.
    indices = [
        Index("targetUserId"),
        Index(value = ["workspaceId", "status"]),
        Index(value = ["email", "status"]),
    ],
)
data class WorkspaceInviteEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "workspaceId") val workspaceId: String,
    @ColumnInfo(name = "email") val email: String,
    @ColumnInfo(name = "targetUserId") val targetUserId: String? = null,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "invitedByUserId") val invitedByUserId: String,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "updatedAt", defaultValue = "0") val updatedAt: Long = 0L,
    @ColumnInfo(name = "expiresAt") val expiresAt: Long,
    @ColumnInfo(name = "respondedAt") val respondedAt: Long? = null,
)
