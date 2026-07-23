package com.georgeci.moneysurfer.data.db.entity

import androidx.room.ColumnInfo

/**
 * One aggregation row of `TransactionDao.getTotals`: the summed magnitude of every transaction
 * sharing a raw type, currency and amount sign inside the queried window.
 *
 * [type] is the raw column text, not a parsed enum — legacy spellings are resolved in
 * `TransactionRepositoryImpl`, which is why [isNegative] travels alongside it.
 */
data class TransactionTotalEntity(
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "currencyCode") val currencyCode: String,
    @ColumnInfo(name = "isNegative") val isNegative: Boolean,
    @ColumnInfo(name = "total") val total: Long,
)
