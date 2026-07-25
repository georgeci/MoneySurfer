package com.georgeci.moneysurfer.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.georgeci.moneysurfer.appconfig.ConfigKey
import com.georgeci.moneysurfer.appconfig.LayerValue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.concurrent.Volatile

/**
 * Shared plumbing for the two DataStore-backed configuration layers.
 *
 * `ConfigSource.peek` is synchronous while DataStore is suspend-only, so the last known
 * `Preferences` snapshot is kept in memory. It is warmed by [hydrate], refreshed by [changes]
 * before that flow emits downstream, and refreshed again by [edit] — so a layer that owns its own
 * writes is never stale, and a layer being observed is current for as long as it is observed.
 */
internal class PreferencesMirror(private val dataStore: DataStore<Preferences>) {

    @Volatile
    private var snapshot: Preferences? = null

    /** `null` until warm, which resolution reads as "this layer holds nothing". */
    fun raw(name: String): String? = snapshot?.get(stringPreferencesKey(name))

    val changes: Flow<Unit> = dataStore.data
        .onEach { snapshot = it }
        .map { }

    suspend fun hydrate() {
        snapshot = dataStore.data.first()
    }

    suspend fun edit(transform: (MutablePreferences) -> Unit) {
        snapshot = dataStore.edit(transform)
    }
}

/**
 * Turns a stored string into a layer value. A codec rejection is [LayerValue.Undecodable], not the
 * key default: resolution then continues to the layer below instead of stopping on a value nobody
 * wrote.
 */
internal fun <T : Any> ConfigKey<T>.layerValueOf(raw: String?): LayerValue<T> = when (raw) {
    null -> LayerValue.Absent
    else -> codec.decode(raw)?.let { LayerValue.Present(it) } ?: LayerValue.Undecodable(raw)
}
