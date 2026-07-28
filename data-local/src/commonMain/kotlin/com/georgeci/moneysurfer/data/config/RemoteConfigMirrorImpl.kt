package com.georgeci.moneysurfer.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.georgeci.moneysurfer.appconfig.RemoteConfigMirror
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

/** File name of the server-flag mirror. Deliberately not the app's settings file. */
internal const val REMOTE_FLAGS_FILE_NAME = "moneysurfer_remote_flags.preferences_pb"

/**
 * [RemoteConfigMirror] on its own DataStore file, alongside the other DataStore-backed layers.
 *
 * Its own file for two reasons. The contents are server-owned and disposable, so they must not ride
 * along in the user's settings file through backup and export; and being disposable is what lets the
 * store replace itself on corruption, which the settings file deliberately does not do because it
 * also holds the session pointers.
 *
 * The store is **not** a Koin binding: a second unqualified `DataStore<Preferences>` would collide
 * with the app's own one in `sharedPlatformModule` and both would end up reading the same file. Each
 * platform's `createRemoteConfigMirror(...)` builds it and hands it straight to this constructor —
 * the same shape `DebugConfigSourceImpl` already uses.
 *
 * Values are stored under the bare key name; the file is dedicated, so there is nothing to namespace
 * against.
 */
class RemoteConfigMirrorImpl(
    dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : RemoteConfigMirror {

    private val mirror = PreferencesMirror(dataStore, scope)

    override fun raw(name: String): String? = mirror.raw(name)

    override val changes: Flow<Unit> = mirror.changes

    override suspend fun hydrate() = mirror.hydrate()

    override val isDegraded: Boolean get() = mirror.isDegraded

    override suspend fun replaceAll(values: Map<String, String>) {
        mirror.edit { preferences ->
            // Clear first: this is a replace, not a merge, so a flag the owner removed from the
            // document stops being served instead of being pinned at its last value forever.
            preferences.clear()
            values.forEach { (name, raw) -> preferences[stringPreferencesKey(name)] = raw }
        }
    }
}
