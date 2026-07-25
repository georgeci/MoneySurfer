package com.georgeci.moneysurfer.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v32 → v33: add `accounts.sortOrder` so the manage screen's drag-to-reorder has somewhere to
 * put the order the user chose.
 *
 * Additive column plus a backfill. The backfill is what keeps the upgrade invisible: reads now
 * order by `sortOrder` then `name`, so leaving every existing row on the column default would
 * re-sort each workspace alphabetically the first time it is opened — a list the user never
 * asked for. Numbering the rows by insertion order (`rowid`) within their workspace instead
 * reproduces the order the unordered `SELECT` was already returning.
 */
val MIGRATION_32_33: Migration = object : Migration(32, 33) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `accounts` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
        connection.execSQL(
            """
            UPDATE `accounts` SET `sortOrder` = (
                SELECT COUNT(*) FROM `accounts` AS earlier
                WHERE earlier.`workspaceId` = `accounts`.`workspaceId`
                  AND earlier.`rowid` < `accounts`.`rowid`
            )
            """.trimIndent(),
        )
    }
}
