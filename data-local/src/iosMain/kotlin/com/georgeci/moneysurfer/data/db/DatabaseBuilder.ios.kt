package com.georgeci.moneysurfer.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

fun getDatabaseBuilder(): RoomDatabase.Builder<MoneySurferDatabase> {
    val docsDir = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
        .first() as String
    val dbPath = "$docsDir/$DB_NAME"
    return Room.databaseBuilder<MoneySurferDatabase>(dbPath)
}
