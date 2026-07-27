package com.georgeci.moneysurfer.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * Shared plumbing for the DataStore-backed configuration layers.
 *
 * `ConfigSource.peek` is synchronous while DataStore is suspend-only, so the last known
 * `Preferences` snapshot is kept in memory. It is filled by exactly one collection of
 * `dataStore.data` — started eagerly on the application scope and shared by [changes] — so every
 * observer of the layer, and every `Config.observe` collector above it, rides that one
 * subscription instead of opening its own.
 *
 * That single collection is also the only writer of [published]. Snapshots therefore land in
 * DataStore's own emission order, and nothing can put a pre-write value back after a write: [edit]
 * waits for its own value to be published rather than assigning it, and [hydrate] is just "wait for
 * the collection's first emission".
 */
internal class PreferencesMirror(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {

    private val log = Logger.withTag(TAG)

    /**
     * The mirror `peek` reads. `null` until warm, which resolution reads as "this layer holds
     * nothing".
     *
     * A `StateFlow` rather than a plain field because [edit] has to wait on it: it is the handshake
     * that keeps a write visible to the synchronous read path the moment `edit` returns, without
     * making the write path a second writer of the value.
     */
    private val published = MutableStateFlow<Preferences?>(null)

    /**
     * `false` once [changes] has stopped collecting — its store failed a read, or the application
     * scope was cancelled at shutdown. The collection cannot restart, so from then on nothing will
     * publish, and [edit] must not wait for a publication that will never come.
     *
     * Declared before [changes]: the eager collection can reach `onCompletion` before the
     * constructor finishes.
     */
    private val collecting = MutableStateFlow(true)

    @Volatile
    private var readFailed: Boolean = false

    /**
     * `true` once the file could not be read.
     *
     * Latched in practice: a read failure ends the one collection, and there is no second one to
     * clear the flag on a later successful read. That is the honest reading of the state it
     * describes — the layer has lost its only view of the store and will not see a change made
     * anywhere else, whatever the file does next.
     */
    val isDegraded: Boolean get() = readFailed

    fun raw(name: String): String? = published.value?.get(stringPreferencesKey(name))

    /**
     * One collection of `dataStore.data` per mirror, for the lifetime of the graph.
     *
     * `Eagerly` rather than `WhileSubscribed`: the collection is what keeps the snapshot current,
     * so it must not stop when the last screen observing a flag goes away — the next synchronous
     * `peek` would then read whatever was true when it stopped. `replay = 1` is what keeps the
     * `ConfigSource.changes` contract: a collector that arrives late still gets the current state
     * before any change of its own.
     *
     * Never fails. A `CorruptionException` from a truncated file would otherwise propagate to every
     * collector — including the startup read in `AppLaunchViewModel`, which has no route to fall
     * back to — so the failure is logged, recorded in [isDegraded] and served as an empty emission.
     * DataStore recreates the file on the next successful write, at which point the flag clears.
     * The `catch` sits *below* the publish step on purpose: a failed read must not overwrite a good
     * snapshot with an empty one.
     */
    val changes: Flow<Unit> = dataStore.data
        .onEach { preferences ->
            published.value = preferences
            readFailed = false
        }
        .catch { failure ->
            reportUnreadable(failure)
            emit(emptyPreferences())
        }
        .onCompletion { collecting.value = false }
        .map { }
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    /** Warms the mirror by awaiting the shared collection's first emission. Idempotent. */
    suspend fun hydrate() {
        changes.first()
    }

    /**
     * Serialises writes to this mirror.
     *
     * Held across the store call, which the read paths deliberately are not: [hydrate] and `peek`
     * never touch this lock, so the startup path can never end up waiting out someone else's
     * `dataStore.edit` — the reason the previous publish lock was kept off the I/O. What it buys is
     * a single in-flight write per mirror, which is what makes the wait below terminate: with one
     * writer the store's current value *is* `written`, so the collection is guaranteed to publish
     * it (or to have published it already).
     */
    private val writing = Mutex()

    suspend fun edit(transform: (MutablePreferences) -> Unit) {
        writing.withLock {
            val written = dataStore.edit(transform)
            // Not `published.value = written`. Two writers of the snapshot have no ordering between
            // them, so a collection suspended mid-emission could put the pre-write value back and
            // leave a synchronous `peek` stale until the next emission corrected it. Waiting for
            // the collection to publish this write keeps one writer and the same guarantee: when
            // `edit` returns, `raw` reads what was just committed.
            //
            // [collecting] is the second term because the wait must be able to end. A store whose
            // read side failed once — a transient I/O error the next write no longer hits — leaves
            // a dead collection behind, and waiting on it would wedge this write and, through
            // [writing], every write to the layer after it.
            combine(published, collecting) { value, live -> value == written || !live }.first { it }
            if (!collecting.value) {
                // Nothing publishes any more, so the write path is the only writer left and there
                // is nothing to race with. `readFailed` is deliberately not cleared: the store is
                // writable, but its read side is gone, so the layer really is degraded — it will
                // not see a change made anywhere else.
                published.value = written
            }
        }
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
