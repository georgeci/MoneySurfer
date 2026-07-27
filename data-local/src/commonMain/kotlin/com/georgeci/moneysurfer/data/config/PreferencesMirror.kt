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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * Shared plumbing for the DataStore-backed configuration layers.
 *
 * `ConfigSource.peek` is synchronous while DataStore is suspend-only, so the last known
 * `Preferences` snapshot is kept in memory. It is kept current by exactly one collection of
 * `dataStore.data` — started eagerly on the application scope and shared by [changes] — so every
 * observer of the layer, and every `Config.observe` collector above it, rides that one subscription
 * instead of opening its own.
 *
 * [snapshot] has two writers, that collection and [edit], and [writes] is what orders them: a value
 * the store handed the collection before a write committed is dropped rather than published over
 * that write. Ordering them explicitly, instead of routing both through the collection, is
 * deliberate — see [edit].
 */
internal class PreferencesMirror(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) {

    private val log = Logger.withTag(TAG)

    @Volatile
    private var snapshot: Preferences? = null

    @Volatile
    private var readFailed: Boolean = false

    /**
     * `true` while the file cannot be read. Cleared by the next successful read or write, so a store
     * that recovers stops being reported as degraded without any retry logic.
     */
    val isDegraded: Boolean get() = readFailed

    /** `null` until warm, which resolution reads as "this layer holds nothing". */
    fun raw(name: String): String? = snapshot?.get(stringPreferencesKey(name))

    /**
     * Guards the [snapshot] and [writes] assignments. Held only for the assignments themselves —
     * **never** across a store call.
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
     * Bumped by every completed [edit]. It dates a write, so a store value read before the bump can
     * be recognised as older than that write and dropped instead of published over it.
     */
    @Volatile
    private var writes: Long = 0

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
     *
     * [writes] is sampled *before* each value is awaited, never after it arrives, and that ordering
     * is the correctness property here. Sampling afterwards would leave a window in which a write
     * commits and bumps between the store handing over a value and this code reading the counter —
     * the stale value would then pass the check and be published over the write. Sampled first, any
     * write that bumps later is by construction newer than the value in hand, so dropping it is
     * right. Dropping costs nothing: the dropped value is only ever one this layer already has (its
     * own write) or one belonging to another writer on the same file, whose keys this layer does
     * not read.
     */
    val changes: Flow<Unit> = flow {
        var sampledAt = writes
        emitAll(
            dataStore.data.onEach { preferences ->
                publish.withLock {
                    if (sampledAt == writes) {
                        snapshot = preferences
                        readFailed = false
                    }
                }
                sampledAt = writes
            },
        )
    }
        .catch { failure ->
            reportUnreadable(failure)
            emit(emptyPreferences())
        }
        .map { }
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    /** Warms the mirror by awaiting the shared collection's first emission. Idempotent. */
    suspend fun hydrate() {
        changes.first()
    }

    /**
     * Serialises writes to this mirror, so their publications keep commit order.
     *
     * Held across the store call, unlike [publish]: [hydrate] and `peek` never touch this one, so
     * the startup path still cannot end up behind someone else's `dataStore.edit`. DataStore
     * serialises the writes themselves anyway, so the only thing this adds is that the two
     * assignments below cannot be reordered against another write's.
     */
    private val writing = Mutex()

    /**
     * Publishes the committed value itself rather than waiting for the collection to publish it.
     *
     * Waiting would read better — one writer of [snapshot], no counter — but it cannot be made to
     * terminate here. The Local layer shares its DataStore file with the session pointers
     * (`SessionPointersImpl`), which write it without going through this class; `dataStore.data` is
     * backed by a conflating `StateFlow`, so a session write landing in the dispatch window makes
     * the collection skip this write's value entirely and hand over the newer one. A wait for "the
     * collection published exactly what I committed" would then never end, and would hold [writing]
     * while it did not — wedging every later write to the layer. Publishing here and ordering the
     * two writers through [writes] has no such failure mode.
     */
    suspend fun edit(transform: (MutablePreferences) -> Unit) {
        writing.withLock {
            val written = dataStore.edit(transform)
            publish.withLock {
                writes++
                snapshot = written
                readFailed = false
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
