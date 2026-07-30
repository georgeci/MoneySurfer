package com.georgeci.moneysurfer.data.db.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v33 → v34: add `config_entry`, the account-scoped home of every `sync = true` setting.
 *
 * Pure addition. Nothing is backfilled from the DataStore values the settings live in today: the
 * app is not released, so losing current dev/test settings is the cheaper side of the trade — a
 * decision ADR-004 marks as expiring at the first production release.
 *
 * The migration is not optional busywork. On debuggable hosts `getRoomDatabase` still ends in
 * `fallbackToDestructiveMigration(dropAllTables = true)`, so a missing 33 → 34 path does not fail
 * loudly there — it silently drops every table on the first launch after the upgrade. Release
 * builds pass `allowDestructiveMigration = false` and crash on open instead; either way a hole in
 * the chain costs the local database. See `docs/architecture/persistence.md` → "Room schema
 * versioning".
 *
 * The DDL is the one Room generates for `ConfigEntryEntity`; keep the two in step or Room's schema
 * validation fails at open time.
 */
val MIGRATION_33_34: Migration = object : Migration(33, 34) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `config_entry` (
                `key` TEXT NOT NULL,
                `value` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                `lastPushedAt` INTEGER,
                PRIMARY KEY(`key`)
            )
            """.trimIndent(),
        )
    }
}
