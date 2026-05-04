package com.georgeci.moneysurfer.data.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

fun getDatabaseBuilder(context: Context): RoomDatabase.Builder<MoneySurferDatabase> {
    val dbPath = context.getDatabasePath(DB_NAME).absolutePath
    return Room.databaseBuilder<MoneySurferDatabase>(context, dbPath)
}
