package com.georgeci.moneysurfer.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v30 → v31: give `accounts` the `extraDetails` column the creation screen has been collecting
 * and throwing away (issue #304).
 *
 * Purely additive, and with no backfill to do: the values never reached storage, so every existing
 * row genuinely has no extra details and the empty-array default is the truthful state rather than
 * a placeholder.
 */
val MIGRATION_30_31: Migration = object : Migration(30, 31) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE `accounts` ADD COLUMN `extraDetails` TEXT NOT NULL DEFAULT '[]'")
    }
}
