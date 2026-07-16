package com.georgeci.moneysurfer.sync.db

import androidx.room.Room
import androidx.room.RoomDatabase
import com.georgeci.moneysurfer.sync.storage.iosSyncStorageFilePath

fun getSyncDatabaseBuilder(): RoomDatabase.Builder<SyncDatabase> {
    val dbPath = iosSyncStorageFilePath(SYNC_DB_NAME)
    return Room.databaseBuilder<SyncDatabase>(dbPath)
}
