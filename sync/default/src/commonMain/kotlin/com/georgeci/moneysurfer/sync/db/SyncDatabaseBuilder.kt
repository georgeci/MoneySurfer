package com.georgeci.moneysurfer.sync.db

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

fun getSyncRoomDatabase(builder: RoomDatabase.Builder<SyncDatabase>): SyncDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()

internal const val SYNC_DB_NAME = "moneysurfer_sync.db"
