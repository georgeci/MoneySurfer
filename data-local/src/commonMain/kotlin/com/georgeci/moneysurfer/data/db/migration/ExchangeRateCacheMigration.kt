package com.georgeci.moneysurfer.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v31 → v32: add `exchange_rates`, the local FX cache behind the multi-currency dashboard total.
 *
 * Pure addition — nothing existing is touched, and an upgraded install simply starts with an
 * empty cache that the first refresh fills. The DDL is the one Room generates for
 * `ExchangeRateEntity`; keep the two in step or Room's schema validation fails at open time.
 */
val MIGRATION_31_32: Migration = object : Migration(31, 32) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `exchange_rates` (
                `baseCurrency` TEXT NOT NULL,
                `currency` TEXT NOT NULL,
                `rate` REAL NOT NULL,
                `asOf` INTEGER NOT NULL,
                `fetchedAt` INTEGER NOT NULL,
                PRIMARY KEY(`baseCurrency`, `currency`)
            )
            """.trimIndent(),
        )
    }
}
