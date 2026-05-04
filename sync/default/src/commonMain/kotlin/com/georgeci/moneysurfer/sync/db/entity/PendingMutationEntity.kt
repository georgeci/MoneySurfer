package com.georgeci.moneysurfer.sync.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Outbox row — one queued local mutation awaiting push.
 *
 * No FK constraints intentionally: the outbox is an independent ledger that
 * must outlive the row it references (e.g. for DELETE operations the entity
 * is already gone from its primary table).
 *
 * Schema mirrors `sync.md §4.3`.
 */
@Entity(
    tableName = "pending_mutations",
    indices = [
        Index("status"),
        Index("workspaceId"),
        Index("createdAt"),
    ],
)
data class PendingMutationEntity(
    @PrimaryKey val id: String,
    val entityType: String,
    val entityId: String,
    val operation: String,
    val workspaceId: String?,
    val createdAt: Long,
    val attempts: Int,
    val status: String,
    val lastError: String?,
) {
    companion object {
        const val STATUS_PENDING: String = "PENDING"
        const val STATUS_IN_FLIGHT: String = "IN_FLIGHT"
        const val STATUS_FAILED: String = "FAILED"
    }
}
