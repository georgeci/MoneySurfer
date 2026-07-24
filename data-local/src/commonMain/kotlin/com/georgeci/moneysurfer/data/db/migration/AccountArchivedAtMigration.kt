package com.georgeci.moneysurfer.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v29 → v30: add `accounts.archivedAt` so the manage list can say *when* an account was archived.
 *
 * Rows archived before this column existed have no recorded date, and `updatedAt` is not a stand-in
 * — balance recalculation bumps it long after the archive. They stay `NULL`, and the archived row
 * falls back to the plain type label rather than inventing a month.
 */
val MIGRATION_29_30: Migration = object : Migration(29, 30) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `accounts` ADD COLUMN `archivedAt` INTEGER DEFAULT NULL")
    }
}
