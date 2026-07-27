package com.georgeci.moneysurfer.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.georgeci.moneysurfer.appconfig.ConfigKey
import com.georgeci.moneysurfer.appconfig.LayerValue
import com.georgeci.moneysurfer.appconfig.LocalConfigSource
import com.georgeci.moneysurfer.appconfig.SettingKey
import com.georgeci.moneysurfer.appconfig.layerValueOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single

/**
 * Device-local user settings, on the app's existing DataStore file.
 *
 * Every key is here for now, `sync` flag or not. The follow-up per-user-sync issue splits the
 * storage — `sync = true` keys move to a Room `config_entry` table so the push plugin can read
 * `value + updatedAt` by key and the account-scoped wipe can drop them — and this class owns that
 * routing so no caller sees it.
 *
 * Every value is stored as a string under a `config.` prefix, which is what keeps this layer clear
 * of the legacy typed preferences in the same file: `ui.onboarding_completed` used to be a
 * `booleanPreferencesKey`, and `Preferences.Key` equality is by name only — reading it back through
 * a `stringPreferencesKey` of the same name would throw on any existing install. Prefixed names
 * lose current dev/test values instead, which the ADR explicitly accepts pre-release.
 *
 * The key *name* stays unprefixed everywhere it is user-visible or wire-visible (debug panel today,
 * `users/{uid}/config/{keyName}` later) — the prefix belongs to this store, not to the key.
 */
@Single(binds = [LocalConfigSource::class])
class LocalConfigSourceImpl(
    dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : LocalConfigSource {

    private val mirror = PreferencesMirror(dataStore, scope)

    override fun <T : Any> peek(key: ConfigKey<T>): LayerValue<T> =
        key.layerValueOf(mirror.raw(key.preferenceName))

    override val changes: Flow<Unit> = mirror.changes

    override suspend fun hydrate() = mirror.hydrate()

    override val isDegraded: Boolean get() = mirror.isDegraded

    override suspend fun <T : Any> write(key: SettingKey<T>, value: T) {
        mirror.edit { preferences ->
            preferences[stringPreferencesKey(key.preferenceName)] = key.codec.encode(value)
        }
    }

    private val ConfigKey<*>.preferenceName: String get() = PREFIX + name

    private companion object {
        const val PREFIX = "config."
    }
}
