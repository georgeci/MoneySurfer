package com.georgeci.moneysurfer.sync.db

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

fun getSyncDatabaseBuilder(): RoomDatabase.Builder<SyncDatabase> {
    val dbFile = File(System.getProperty("java.io.tmpdir"), ".moneysurfer/$SYNC_DB_NAME")
    dbFile.parentFile?.mkdirs()
    return Room.databaseBuilder<SyncDatabase>(dbFile.absolutePath)
}
