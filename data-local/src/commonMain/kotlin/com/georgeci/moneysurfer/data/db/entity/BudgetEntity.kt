package com.georgeci.moneysurfer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
        ),
    ],
    indices = [Index("workspaceId")],
)
data class BudgetEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "workspaceId", defaultValue = "") val workspaceId: String,
    @ColumnInfo(name = "name") val name: String,
    /** CSV of category ids. Empty = every expense category. */
    @ColumnInfo(name = "categoryIds") val categoryIds: String,
    @ColumnInfo(name = "amount") val amount: Long,
    @ColumnInfo(name = "period") val period: String,
    @ColumnInfo(name = "startDate") val startDate: String,
    @ColumnInfo(name = "alertPercent") val alertPercent: Int,
    @ColumnInfo(name = "isActive") val isActive: Boolean,
    @ColumnInfo(name = "rollover", defaultValue = "0") val rollover: Boolean = false,
    @ColumnInfo(name = "createdAt", defaultValue = "0") val createdAt: Long = 0L,
    @ColumnInfo(name = "updatedAt", defaultValue = "0") val updatedAt: Long = 0L,
)
