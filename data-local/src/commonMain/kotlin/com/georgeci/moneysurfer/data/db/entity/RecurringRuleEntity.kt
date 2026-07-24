package com.georgeci.moneysurfer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
        ),
    ],
    indices = [Index("workspaceId"), Index("categoryId")],
)
data class RecurringRuleEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "workspaceId", defaultValue = "") val workspaceId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "amount") val amount: Long,
    @ColumnInfo(name = "categoryId") val categoryId: String,
    @ColumnInfo(name = "scheduleFrequency") val scheduleFrequency: String,
    @ColumnInfo(name = "scheduleInterval") val scheduleInterval: Int,
    @ColumnInfo(name = "scheduleDaysOfWeek") val scheduleDaysOfWeek: String,
    @ColumnInfo(name = "scheduleDaysOfMonth") val scheduleDaysOfMonth: String,
    @ColumnInfo(name = "scheduleMissingDayPolicy") val scheduleMissingDayPolicy: String,
    @ColumnInfo(name = "startDate") val startDate: String,
    @ColumnInfo(name = "nextRunAt") val nextRunAt: Long?,
    @ColumnInfo(name = "autoCreate") val autoCreate: Boolean,
    @ColumnInfo(name = "isActive") val isActive: Boolean,
    @ColumnInfo(name = "createdAt", defaultValue = "0") val createdAt: Long = 0L,
    @ColumnInfo(name = "updatedAt", defaultValue = "0") val updatedAt: Long = 0L,
)
