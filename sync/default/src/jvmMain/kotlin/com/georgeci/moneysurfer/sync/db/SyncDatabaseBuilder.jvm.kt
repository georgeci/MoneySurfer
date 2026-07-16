package com.georgeci.moneysurfer.sync.db

import androidx.room.Room
import androidx.room.RoomDatabase
import com.georgeci.moneysurfer.sync.storage.appDataDir
import java.io.File

fun getSyncDatabaseBuilder(): RoomDatabase.Builder<SyncDatabase> {
    val dbFile = File(appDataDir(), SYNC_DB_NAME)
    return Room.databaseBuilder<SyncDatabase>(dbFile.absolutePath)
}
