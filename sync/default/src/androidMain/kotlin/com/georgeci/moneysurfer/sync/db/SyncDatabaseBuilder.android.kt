package com.georgeci.moneysurfer.sync.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun getSyncDatabaseBuilder(context: Context): RoomDatabase.Builder<SyncDatabase> {
    val dbPath = context.getDatabasePath(SYNC_DB_NAME).absolutePath
    return Room.databaseBuilder<SyncDatabase>(context, dbPath)
}
