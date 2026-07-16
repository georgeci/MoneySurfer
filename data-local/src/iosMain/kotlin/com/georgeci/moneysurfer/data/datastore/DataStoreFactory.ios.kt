package com.georgeci.moneysurfer.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.georgeci.moneysurfer.data.storage.iosAppStorageFilePath

fun createDataStore(): DataStore<Preferences> = createDataStore(
    producePath = { iosAppStorageFilePath(DATASTORE_FILE_NAME, isDatabase = false) },
)
