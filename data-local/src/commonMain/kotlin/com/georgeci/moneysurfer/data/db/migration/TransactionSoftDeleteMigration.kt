package com.georgeci.moneysurfer.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v35 → v36: add `transactions.deletedAt` so a delete becomes a tombstone instead of dropping the
 * row (issue #346).
 *
 * Every existing row is live, and `NULL` is exactly that — so this is a pure addition with no
 * backfill. Rows deleted before this column existed are simply gone; there is nothing to recover
 * for them, which is the bug the column fixes going forward.
 *
 * `transactions_fts` needs no attention: it is an external-content FTS4 table over `note` and
 * `merchant` only, so a new column on the content table changes neither its schema nor the
 * triggers Room generates for it. Soft-deleted rows stay in the index and are filtered out by the
 * `deletedAt IS NULL` term on the join in `TransactionDao.searchByText`.
 */
val MIGRATION_35_36: Migration = object : Migration(35, 36) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `transactions` ADD COLUMN `deletedAt` INTEGER DEFAULT NULL")
    }
}
