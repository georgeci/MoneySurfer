package com.georgeci.moneysurfer.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import com.georgeci.moneysurfer.domain.storage.appDataDir
import java.io.File

fun getDatabaseBuilder(): RoomDatabase.Builder<MoneySurferDatabase> {
    val dbFile = File(appDataDir(), DB_NAME)
    return Room.databaseBuilder<MoneySurferDatabase>(dbFile.absolutePath)
}
