package com.georgeci.moneysurfer.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v27 → v28: fill in `transactions.operationDate` for rows written before the column existed.
 *
 * The column was added with a `''` default, and readers have been papering over that ever since —
 * `resolveLegacyOperationDate` derives the date from `operationAt` whenever the stored text is
 * unparseable. That works for a full-table read, but the transactions list now filters on the
 * column in SQL, and `'' >= '2026-07-01'` is false: every legacy row would silently vanish from
 * every date window.
 *
 * The derivation is UTC on purpose — `date(unixepoch)` matches `resolveLegacyOperationDate`'s
 * `TimeZone.UTC` exactly, so a backfilled row keeps the date the app has been showing for it
 * rather than shifting by a day.
 */
val MIGRATION_27_28: Migration = object : Migration(27, 28) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            UPDATE `transactions`
            SET `operationDate` = date(`operationAt` / 1000, 'unixepoch')
            WHERE `operationDate` = ''
            """.trimIndent(),
        )
    }
}
