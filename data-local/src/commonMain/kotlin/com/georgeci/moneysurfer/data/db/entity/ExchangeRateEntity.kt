package com.georgeci.moneysurfer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * One cached FX quote: `1 baseCurrency = rate × currency`.
 *
 * A row per quote rather than one JSON blob per base — the table is written whole and read whole,
 * but rows let SQLite do the "newest fetch for this base" bookkeeping without decoding a blob, and
 * keep the schema inspectable. No foreign key: currencies here are provider codes, not workspace
 * rows, and the cache must survive a workspace wipe.
 */
@Entity(tableName = "exchange_rates", primaryKeys = ["baseCurrency", "currency"])
data class ExchangeRateEntity(
    @ColumnInfo(name = "baseCurrency") val baseCurrency: String,
    @ColumnInfo(name = "currency") val currency: String,
    @ColumnInfo(name = "rate") val rate: Double,
    /** Epoch millis the *provider* published this table — what the "as of" label shows. */
    @ColumnInfo(name = "asOf") val asOf: Long,
    /** Epoch millis this row was stored — what the refresh policy measures staleness against. */
    @ColumnInfo(name = "fetchedAt") val fetchedAt: Long,
)
