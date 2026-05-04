package com.georgeci.moneysurfer.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

fun getDatabaseBuilder(): RoomDatabase.Builder<MoneySurferDatabase> {
    val dbFile = File(System.getProperty("java.io.tmpdir"), ".moneysurfer/$DB_NAME")
    dbFile.parentFile?.mkdirs()
    return Room.databaseBuilder<MoneySurferDatabase>(dbFile.absolutePath)
}
