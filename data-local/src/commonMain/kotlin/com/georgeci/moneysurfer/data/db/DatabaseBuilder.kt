package com.georgeci.moneysurfer.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_25_26
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_27_28
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_29_30
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_30_31
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_31_32
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_32_33
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_33_34
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_34_35
import com.georgeci.moneysurfer.data.db.migration.MIGRATION_35_36
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/**
 * Builds the app database.
 *
 * [allowDestructiveMigration] must stay `false` in release builds: dropping the user's
 * ledger on upgrade is only ever acceptable on a developer machine, where the schema is
 * still being iterated on and the local database is disposable. Every release-to-release
 * upgrade from [MONEY_SURFER_DB_RELEASE_BASELINE_VERSION] onwards is carried by a
 * hand-written [androidx.room.migration.Migration] instead — see
 * `docs/architecture/persistence.md` → "Room schema versioning".
 *
 * The parameter defaults to the release-safe value so a caller that forgets it cannot
 * accidentally ship destructive upgrades.
 */
// https://developer.android.com/kotlin/multiplatform/room#set-coroutine-context
fun getRoomDatabase(
    builder: RoomDatabase.Builder<MoneySurferDatabase>,
    allowDestructiveMigration: Boolean = false,
): MoneySurferDatabase {
    lateinit var database: MoneySurferDatabase
    database = builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        // Pre-baseline versions (< 25, plus the 26→27 and 28→29 gaps) never shipped to a
        // user, so they have no migration path and are only reachable on a dev machine.
        .addMigrations(
            MIGRATION_25_26,
            MIGRATION_27_28,
            MIGRATION_29_30,
            MIGRATION_30_31,
            MIGRATION_31_32,
            MIGRATION_32_33,
            MIGRATION_33_34,
            MIGRATION_34_35,
            MIGRATION_35_36,
        )
        .apply {
            if (allowDestructiveMigration) {
                fallbackToDestructiveMigration(dropAllTables = true)
            }
        }
        .build()
    return database
}

internal const val DB_NAME = "moneysurfer.db"
