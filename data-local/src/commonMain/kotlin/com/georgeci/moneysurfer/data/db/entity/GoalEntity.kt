package com.georgeci.moneysurfer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A savings goal. The amount saved is deliberately absent — it is derived from
 * `SUM(goal_contributions.amount)` (see [GoalContributionEntity]) so there is no
 * denormalized column to drift.
 */
@Entity(
    tableName = "goals",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
        ),
    ],
    indices = [Index("workspaceId"), Index("accountId")],
)
data class GoalEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "workspaceId") val workspaceId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "emoji") val emoji: String,
    @ColumnInfo(name = "hue") val hue: Int,
    @ColumnInfo(name = "target") val target: Long,
    @ColumnInfo(name = "currencyCode") val currencyCode: String,
    /** ISO-8601 date, matching the `BudgetEntity.startDate` convention. */
    @ColumnInfo(name = "startDate") val startDate: String,
    /** ISO-8601 date, or null when the goal has no deadline. */
    @ColumnInfo(name = "targetDate") val targetDate: String? = null,
    @ColumnInfo(name = "accountId") val accountId: String? = null,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "createdAt", defaultValue = "0") val createdAt: Long = 0L,
    @ColumnInfo(name = "updatedAt", defaultValue = "0") val updatedAt: Long = 0L,
)
