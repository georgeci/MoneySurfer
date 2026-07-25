package com.georgeci.moneysurfer.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.georgeci.moneysurfer.appconfig.ConfigKey
import com.georgeci.moneysurfer.appconfig.DebugConfigSource
import com.georgeci.moneysurfer.appconfig.LayerValue
import kotlinx.coroutines.flow.Flow

/** File name of the debug-overrides store. Deliberately not the app's settings file. */
internal const val DEBUG_OVERRIDES_FILE_NAME = "moneysurfer_debug_overrides.preferences_pb"

/**
 * QA overrides on their own DataStore file, so resetting them never touches user settings and they
 * stay out of backup/export.
 *
 * The store is **not** a Koin binding: a second unqualified `DataStore<Preferences>` would collide
 * with the app's own one in `sharedPlatformModule` and both layers would end up reading the same
 * file. Each platform's `createDebugConfigSource(...)` builds it and hands it straight to this
 * constructor.
 *
 * Values are stored under the bare key name — the file is dedicated, so there is nothing to
 * namespace against.
 */
class DebugConfigSourceImpl(dataStore: DataStore<Preferences>) : DebugConfigSource {

    private val mirror = PreferencesMirror(dataStore)

    override val isActive: Boolean = true

    override fun <T : Any> peek(key: ConfigKey<T>): LayerValue<T> = key.layerValueOf(mirror.raw(key.name))

    override val changes: Flow<Unit> = mirror.changes

    override suspend fun hydrate() = mirror.hydrate()

    override suspend fun override(key: ConfigKey<*>, raw: String) {
        mirror.edit { preferences -> preferences[stringPreferencesKey(key.name)] = raw }
    }

    override suspend fun clear(key: ConfigKey<*>) {
        mirror.edit { preferences -> preferences.remove(stringPreferencesKey(key.name)) }
    }

    override suspend fun clearAll() {
        mirror.edit { preferences -> preferences.clear() }
    }
}
