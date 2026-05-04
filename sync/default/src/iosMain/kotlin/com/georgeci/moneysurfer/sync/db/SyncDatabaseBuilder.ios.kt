package com.georgeci.moneysurfer.sync.db

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

fun getSyncDatabaseBuilder(): RoomDatabase.Builder<SyncDatabase> {
    val docsDir = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .first() as String
    val dbPath = "$docsDir/$SYNC_DB_NAME"
    return Room.databaseBuilder<SyncDatabase>(dbPath)
}
