package com.georgeci.moneysurfer.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.georgeci.moneysurfer.data.storage.appDataDir
import java.io.File

fun createDataStore(): DataStore<Preferences> = createDataStore(
    producePath = { File(appDataDir(), DATASTORE_FILE_NAME).absolutePath },
)
