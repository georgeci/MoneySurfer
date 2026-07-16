package com.georgeci.moneysurfer.sync.db

import androidx.room.Room
import androidx.room.RoomDatabase
import com.georgeci.moneysurfer.domain.storage.iosAppStorageFilePath

fun getSyncDatabaseBuilder(): RoomDatabase.Builder<SyncDatabase> {
    val dbPath = iosAppStorageFilePath(SYNC_DB_NAME, isDatabase = true)
    return Room.databaseBuilder<SyncDatabase>(dbPath)
}
