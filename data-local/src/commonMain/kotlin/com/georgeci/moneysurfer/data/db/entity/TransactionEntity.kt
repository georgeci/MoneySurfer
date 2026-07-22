package com.georgeci.moneysurfer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
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
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
        ),
    ],
    // Composite indices mirror the ORDER BY of every list query in TransactionDao, so
    // SQLite walks the index instead of building a temp B-tree per Flow emission. Both
    // start with the FK column, which also satisfies Room's FK-index requirement — no
    // separate single-column index on workspaceId / accountId is needed.
    indices = [
        Index(
            value = ["workspaceId", "operationDate", "operationAt", "createdAt"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.DESC, Index.Order.DESC],
        ),
        Index(
            value = ["accountId", "operationDate", "operationAt", "createdAt"],
            orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.DESC, Index.Order.DESC],
        ),
        Index("categoryId"),
    ],
)
data class TransactionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "workspaceId") val workspaceId: String,
    @ColumnInfo(name = "accountId") val accountId: String,
    @ColumnInfo(name = "amount") val amount: Long,
    @ColumnInfo(name = "currencyCode") val currencyCode: String,
    @ColumnInfo(name = "categoryId") val categoryId: String?,
    @ColumnInfo(name = "note") val note: String,
    @ColumnInfo(name = "operationAt") val operationAt: Long,
    @ColumnInfo(name = "operationDate", defaultValue = "") val operationDate: String = "",
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "status", defaultValue = "ACTUAL") val status: String = "ACTUAL",
    @ColumnInfo(name = "createdAt", defaultValue = "0") val createdAt: Long = 0L,
    @ColumnInfo(name = "updatedAt", defaultValue = "0") val updatedAt: Long = 0L,
    @ColumnInfo(name = "transferId") val transferId: String? = null,
)
