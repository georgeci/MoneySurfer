package com.georgeci.moneysurfer.data.datastore.core

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PreferenceStore(
    private val dataStore: DataStore<Preferences>,
) {

    fun boolean(name: String, default: Boolean = false): PreferenceProperty<Boolean> =
        PreferenceProperty(dataStore, booleanPreferencesKey(name), default)

    fun int(name: String, default: Int = 0): PreferenceProperty<Int> =
        PreferenceProperty(dataStore, intPreferencesKey(name), default)

    fun long(name: String, default: Long = 0L): PreferenceProperty<Long> =
        PreferenceProperty(dataStore, longPreferencesKey(name), default)

    fun float(name: String, default: Float = 0f): PreferenceProperty<Float> =
        PreferenceProperty(dataStore, floatPreferencesKey(name), default)

    fun double(name: String, default: Double = 0.0): PreferenceProperty<Double> =
        PreferenceProperty(dataStore, doublePreferencesKey(name), default)

    fun string(name: String, default: String = ""): PreferenceProperty<String> =
        PreferenceProperty(dataStore, stringPreferencesKey(name), default)

    fun <T> custom(
        key: Preferences.Key<T>,
        default: T,
    ): PreferenceProperty<T> =
        PreferenceProperty(dataStore, key, default)

    /**
     * Writes several keys in one DataStore edit. A group whose members must move together — the
     * session pointers on logout — cannot be written as N separate `set` calls, or a crash in
     * between leaves the group half-updated.
     */
    suspend fun edit(transform: (MutablePreferences) -> Unit) {
        dataStore.edit(transform)
    }
}

class PreferenceProperty<T>(
    private val dataStore: DataStore<Preferences>,
    /** Exposed so a caller can include this property in a multi-key [PreferenceStore.edit]. */
    val key: Preferences.Key<T>,
    private val default: T,
) {
    val flow: Flow<T> =
        dataStore.data
            .map { preferences -> preferences[key] ?: default }
            .distinctUntilChanged()

    suspend fun get(): T =
        dataStore.data.map { it[key] ?: default }.first()

    suspend fun set(value: T) {
        dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    suspend fun update(transform: (T) -> T) {
        dataStore.edit { preferences ->
            val oldValue = preferences[key] ?: default
            preferences[key] = transform(oldValue)
        }
    }

    suspend fun remove() {
        dataStore.edit { preferences ->
            preferences.remove(key)
        }
    }
}
