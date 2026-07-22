package com.georgeci.moneysurfer.data.db.entity

import androidx.room.ColumnInfo

data class CategorizedTransactionEntity(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "workspaceId") val workspaceId: String,
    @ColumnInfo(name = "accountId") val accountId: String,
    @ColumnInfo(name = "amount") val amount: Long,
    @ColumnInfo(name = "currencyCode") val currencyCode: String,
    @ColumnInfo(name = "categoryId") val categoryId: String?,
    @ColumnInfo(name = "categoryName") val categoryName: String?,
    @ColumnInfo(name = "note") val note: String,
    @ColumnInfo(name = "merchant") val merchant: String,
    @ColumnInfo(name = "tags") val tags: String,
    @ColumnInfo(name = "operationAt") val operationAt: Long,
    @ColumnInfo(name = "operationDate") val operationDate: String,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
    @ColumnInfo(name = "transferId") val transferId: String? = null,
    @ColumnInfo(name = "recurringRuleId") val recurringRuleId: String? = null,
)
