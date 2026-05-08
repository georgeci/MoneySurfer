package com.georgeci.moneysurfer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "accounts",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
        ),
    ],
    indices = [Index("workspaceId")],
)
data class AccountEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "workspaceId") val workspaceId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "balance") val balance: Long,
    @ColumnInfo(name = "archived", defaultValue = "0") val archived: Boolean = false,
    @ColumnInfo(name = "updatedAt", defaultValue = "0") val updatedAt: Long = 0L,
)
