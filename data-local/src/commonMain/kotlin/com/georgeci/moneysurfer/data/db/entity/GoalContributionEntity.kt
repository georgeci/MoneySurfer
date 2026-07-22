package com.georgeci.moneysurfer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One signed movement against a [GoalEntity] — negative is a withdrawal.
 *
 * The goal FK cascades: deleting a goal drops its contributions locally. That
 * happens silently, so the repository enqueues the matching outbox deletes
 * itself; Firestore has no cascade.
 */
@Entity(
    tableName = "goal_contributions",
    foreignKeys = [
        ForeignKey(
            entity = WorkspaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["workspaceId"],
        ),
        ForeignKey(
            entity = GoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("workspaceId"), Index("goalId")],
)
data class GoalContributionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "workspaceId") val workspaceId: String,
    @ColumnInfo(name = "goalId") val goalId: String,
    /** Signed minor units — negative is a withdrawal. */
    @ColumnInfo(name = "amount") val amount: Long,
    /** ISO-8601 date. */
    @ColumnInfo(name = "occurredOn") val occurredOn: String,
    @ColumnInfo(name = "note") val note: String,
    /** Always null in v1 — reserved for the money-backed model. */
    @ColumnInfo(name = "transferId") val transferId: String? = null,
    @ColumnInfo(name = "createdAt", defaultValue = "0") val createdAt: Long = 0L,
    @ColumnInfo(name = "updatedAt", defaultValue = "0") val updatedAt: Long = 0L,
)
