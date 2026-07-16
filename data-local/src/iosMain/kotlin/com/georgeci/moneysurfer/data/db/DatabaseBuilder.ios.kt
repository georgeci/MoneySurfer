package com.georgeci.moneysurfer.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import com.georgeci.moneysurfer.domain.storage.iosAppStorageFilePath

fun getDatabaseBuilder(): RoomDatabase.Builder<MoneySurferDatabase> {
    val dbPath = iosAppStorageFilePath(DB_NAME, isDatabase = true)
    return Room.databaseBuilder<MoneySurferDatabase>(dbPath)
}
