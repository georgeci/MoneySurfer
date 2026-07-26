package com.georgeci.moneysurfer.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import okio.Path.Companion.toPath

internal const val DATASTORE_FILE_NAME = "moneysurfer_settings.preferences_pb"

fun createDataStore(producePath: () -> String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        produceFile = { producePath().toPath() },
    )

/**
 * Same store, but a file DataStore cannot parse is replaced with an empty one instead of throwing
 * `CorruptionException` on every read for the rest of the install's life.
 *
 * Only for stores whose contents are disposable — the debug-override layer. The app's own settings
 * file deliberately does **not** use this: it also holds the session pointers, so silently replacing
 * it would sign the user out, and that trade-off deserves its own decision rather than riding along
 * with a QA feature.
 */
fun createReplaceOnCorruptionDataStore(producePath: () -> String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        produceFile = { producePath().toPath() },
    )
