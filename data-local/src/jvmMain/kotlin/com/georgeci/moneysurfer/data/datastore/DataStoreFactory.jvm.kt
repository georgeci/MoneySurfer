package com.georgeci.moneysurfer.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import java.io.File

fun createDataStore(): DataStore<Preferences> = createDataStore(
    producePath = {
        val dir = File(System.getProperty("java.io.tmpdir"), ".moneysurfer")
        dir.mkdirs()
        File(dir, DATASTORE_FILE_NAME).absolutePath
    },
)
