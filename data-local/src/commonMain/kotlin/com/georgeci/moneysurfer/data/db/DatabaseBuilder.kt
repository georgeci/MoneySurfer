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

// https://developer.android.com/kotlin/multiplatform/room#set-coroutine-context
fun getRoomDatabase(builder: RoomDatabase.Builder<MoneySurferDatabase>): MoneySurferDatabase {
    lateinit var database: MoneySurferDatabase
    database = builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        // Declared migrations run first; the destructive fallback below only catches the
        // older versions that have no path forward.
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
        // Schema bumped from Long PKs to UUID Strings — no migration path is feasible.
        // Local data is wiped on app upgrade; remote data lives in Firestore.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    return database
}

internal const val DB_NAME = "moneysurfer.db"
