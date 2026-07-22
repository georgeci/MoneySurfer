package com.georgeci.moneysurfer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
        ),
    ],
    indices = [Index("workspaceId"), Index("parentId")],
)
data class CategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "workspaceId") val workspaceId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "parentId") val parentId: String?,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "updatedAt", defaultValue = "0") val updatedAt: Long = 0L,
    @ColumnInfo(name = "systemKind") val systemKind: String? = null,
    /**
     * Semantic icon key. Blank means "never stored" — a row added by the additive v26 backfill
     * always carries a value, but a row pushed by an older client can still arrive empty, and
     * the domain mapper resolves that back to the deterministic default.
     */
    @ColumnInfo(name = "iconKey", defaultValue = "''") val iconKey: String = "",
    /** Hue in degrees, 0 until 360. `-1` is the same "never stored" sentinel as a blank [iconKey]. */
    @ColumnInfo(name = "hue", defaultValue = "-1") val hue: Int = -1,
)
