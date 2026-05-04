package com.georgeci.moneysurfer.data.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

// https://developer.android.com/kotlin/multiplatform/room#set-coroutine-context
fun getRoomDatabase(builder: RoomDatabase.Builder<MoneySurferDatabase>): MoneySurferDatabase {
    lateinit var database: MoneySurferDatabase
    database = builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        // Schema bumped from Long PKs to UUID Strings — no migration path is feasible.
        // Local data is wiped on app upgrade; remote data lives in Firestore.
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
    return database
}

internal const val DB_NAME = "moneysurfer.db"
