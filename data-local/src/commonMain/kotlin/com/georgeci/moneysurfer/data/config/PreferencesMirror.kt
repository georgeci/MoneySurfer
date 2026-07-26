package com.georgeci.moneysurfer.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
     * Guards [snapshot] and [writes]. Held only for the assignments themselves — **never** across a
     * store call.
     *
     * That distinction is the whole point. Holding it across the I/O would serialize correctly, but
     * it would also put the startup path behind a write: `AppLaunchViewModel` awaits [hydrate]
     * before a start route exists, the RemoteGlobal layer's mirror is written by a fire-and-forget
     * refresh in the same frame, and the splash would then wait out someone else's `dataStore.edit`
     * — including its fsync. DataStore's own reads take no such lock (`tryLock`, falling back to an
     * unlocked read), so that stall would be new.
     */
    private val publish = Mutex()

    /**
     * Bumped by every completed [edit]. A read that began before the bump must not publish its
     * result: [hydrate] would otherwise overwrite freshly written values with pre-write state, and
     * the layer would serve the previous flags for the rest of the session even though the store on
     * disk is current. The RemoteGlobal layer is what makes that reachable — it writes on every
     * foreground return, while startup hydration may still be in flight.
     */
    private var writes: Long = 0

    suspend fun hydrate() {
        val startedAt = publish.withLock { writes }
        val read = readSnapshot()
        publish.withLock {
            // A write landed while this read was in flight, so the read is already stale. Drop it —
            // `edit` published something strictly newer.
            if (writes == startedAt) snapshot = read
        }
    }

    suspend fun edit(transform: (MutablePreferences) -> Unit) {
        val written = dataStore.edit(transform)
        publish.withLock {
            writes++
            snapshot = written
            readFailed = false
        }
    }

    /**
     * Never fails. A `CorruptionException` from a truncated file would otherwise propagate to every
     * collector — including the startup read in `AppLaunchViewModel`, which has no route to fall
     * back to — so the failure is logged, recorded in [isDegraded] and served as an empty snapshot.
     * DataStore recreates the file on the next successful write, at which point the flag clears.
     *
     * The first emission of each collection is guarded by [writes] for the same reason [hydrate] is:
     * `dataStore.data` opens with an *unlocked* read, so while an `edit` holds DataStore's own write
     * lock a new collector can be handed pre-write bytes. Publishing those would undo the write. Every
     * later emission is version-monotonic and cannot go backwards, so only the first needs the check.
     */
    val changes: Flow<Unit> = flow {
        val startedAt = publish.withLock { writes }
        var isFirst = true
        emitAll(
            dataStore.data.onEach { preferences ->
                publish.withLock {
                    if (!isFirst || writes == startedAt) {
                        snapshot = preferences
                        readFailed = false
                    }
                    isFirst = false
                }
            },
        )
    }
        .catch { failure ->
            reportUnreadable(failure)
            emit(emptyPreferences())
        }
        .map { }

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
