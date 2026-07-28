package com.georgeci.moneysurfer.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v34 → v35: add `transactions.splitId`, the shared id of the sibling rows one receipt was split
 * across (issue #399).
 *
 * Pure addition, and nothing is backfilled: every existing row is a single-category transaction,
 * which is exactly what `NULL` means here.
 *
 * The index comes with the column rather than later. `getCategorizedWindow` correlates on
 * `splitId` per row to learn a group's size, and — more immediately — Room validates the schema at
 * open time, so a migrated database missing the index would fail to open where a freshly created
 * one succeeds.
 */
val MIGRATION_34_35: Migration = object : Migration(34, 35) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `splitId` TEXT DEFAULT NULL")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_transactions_splitId` ON `transactions` (`splitId`)",
        )
    }
}
