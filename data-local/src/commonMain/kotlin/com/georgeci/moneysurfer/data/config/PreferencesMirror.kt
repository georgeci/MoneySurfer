package com.georgeci.moneysurfer.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.appconfig.ConfigKey
import com.georgeci.moneysurfer.appconfig.LayerValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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

    private val log = Logger.withTag(TAG)

    @Volatile
    private var snapshot: Preferences? = null

    @Volatile
    private var readFailed: Boolean = false

    /**
     * `true` while the file cannot be read. Cleared by the next successful read, so a store that
     * recovers stops being reported as degraded without any retry logic.
     */
    val isDegraded: Boolean get() = readFailed

    /** `null` until warm, which resolution reads as "this layer holds nothing". */
    fun raw(name: String): String? = snapshot?.get(stringPreferencesKey(name))

    /**
     * Never fails. A `CorruptionException` from a truncated file would otherwise propagate to every
     * collector — including the startup read in `AppLaunchViewModel`, which has no route to fall
     * back to — so the failure is logged, recorded in [isDegraded] and served as an empty snapshot.
     * DataStore recreates the file on the next successful write, at which point the flag clears.
     */
    val changes: Flow<Unit> = dataStore.data
        .onEach { preferences ->
            snapshot = preferences
            readFailed = false
        }
        .catch { failure ->
            reportUnreadable(failure)
            emit(emptyPreferences())
        }
        .map { }

    suspend fun hydrate() {
        snapshot = readSnapshot()
    }

    suspend fun edit(transform: (MutablePreferences) -> Unit) {
        snapshot = dataStore.edit(transform)
        readFailed = false
    }

    @Suppress("TooGenericExceptionCaught") // Any store failure must degrade, not propagate.
    private suspend fun readSnapshot(): Preferences = try {
        dataStore.data.first().also { readFailed = false }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        reportUnreadable(failure)
        emptyPreferences()
    }

    private fun reportUnreadable(failure: Throwable) {
        readFailed = true
        // Error severity: `CrashReportingLogWriter` turns this into a Crashlytics non-fatal, which
        // is the only signal that an install is running on defaults rather than stored values.
        log.e(failure) { "preferences unreadable — resolving without this layer until it recovers" }
    }

    private companion object {
        const val TAG = "ConfigMirror"
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
