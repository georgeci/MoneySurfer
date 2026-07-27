package com.georgeci.moneysurfer.data.config

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.data.db.dao.ConfigEntryDao
import com.georgeci.moneysurfer.data.db.entity.ConfigEntryEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

/**
 * The `config_entry` half of the Local layer, shaped exactly like
 * [PreferencesMirror]: `ConfigSource.peek` is synchronous while Room is suspend-only, so the table
 * is kept in memory, warmed by [hydrate], refreshed by [changes] before that flow emits downstream,
 * and refreshed again by [write].
 *
 * Unlike the DataStore mirror this one is also written from *outside* — the pull applies remote
 * values straight through [ConfigEntryDao]. That is safe precisely because [changes] is fed by
 * Room's own invalidation: any write to the table, whoever made it, re-emits and republishes the
 * snapshot, which is what lets a theme changed on another device retheme this one.
 */
internal class ConfigEntryMirror(private val dao: ConfigEntryDao) {

    private val log = Logger.withTag(TAG)

    @Volatile
    private var snapshot: Map<String, String>? = null

    @Volatile
    private var readFailed: Boolean = false

    /** `true` while the table cannot be read; cleared by the next successful read. */
    val isDegraded: Boolean get() = readFailed

    /** `null` until warm, which resolution reads as "this layer holds nothing". */
    fun raw(key: String): String? = snapshot?.get(key)

    /**
     * Guards [snapshot] and [writes], held only for the assignments — never across a Room call, for
     * the same reason [PreferencesMirror] never holds its lock across DataStore: [hydrate] is
     * awaited on the startup path and must not queue behind someone else's write.
     */
    private val publish = Mutex()

    /**
     * Bumped by every completed [write]. A read that began before the bump must not publish its
     * result, or [hydrate] would overwrite a freshly written value with pre-write state and the
     * layer would serve the stale value for the rest of the session.
     */
    private var writes: Long = 0

    suspend fun hydrate() {
        val startedAt = publish.withLock { writes }
        val read = readSnapshot()
        publish.withLock {
            if (writes == startedAt) snapshot = read
        }
    }

    /** A local write: the value is published immediately, so `peek` never lags its own writer. */
    suspend fun write(key: String, value: String, updatedAt: Long) {
        dao.write(key = key, value = value, updatedAt = updatedAt)
        publish.withLock {
            writes++
            snapshot = snapshot.orEmpty() + (key to value)
            readFailed = false
        }
    }

    /**
     * The account wipe, published the same way a [write] is.
     *
     * Publishing here is the point, not tidiness: Room's invalidation only reaches this mirror
     * through [changes], which is a cold flow with no collector of its own — if nothing happens to
     * be observing a setting at the moment of the wipe, `peek` would keep serving the wiped
     * account's values for the life of the process, and the next user would inherit them the moment
     * the session overlay is cleared. Even with a collector, invalidation is asynchronous, so the
     * window exists regardless.
     */
    suspend fun deleteAll() {
        dao.deleteAll()
        publish.withLock {
            writes++
            snapshot = emptyMap()
            readFailed = false
        }
    }

    /**
     * Never fails. A Room failure here would otherwise reach the startup collector, which has no
     * route to fall back to, so it is logged, recorded in [isDegraded] and served as an empty table.
     *
     * Only the first emission needs the [writes] guard: it is the one that can carry a snapshot
     * read before an in-flight write, while every later emission follows the write that triggered it.
     */
    val changes: Flow<Unit> = flow {
        // Per-collection state, like `PreferencesMirror.changes`: hoisting it out of the builder
        // would share one `isFirst` between every collector.
        val startedAt = publish.withLock { writes }
        var isFirst = true
        emitAll(
            dao.observeAll().onEach { rows ->
                publish.withLock {
                    if (!isFirst || writes == startedAt) {
                        snapshot = rows.associate(ConfigEntryEntity::toPair)
                        readFailed = false
                    }
                    isFirst = false
                }
            },
        )
    }
        .catch { failure ->
            reportUnreadable(failure)
            emit(emptyList())
        }
        .map { }

    @Suppress("TooGenericExceptionCaught") // Any store failure must degrade, not propagate.
    private suspend fun readSnapshot(): Map<String, String> = try {
        dao.getAll().associate(ConfigEntryEntity::toPair).also { readFailed = false }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        reportUnreadable(failure)
        emptyMap()
    }

    private fun reportUnreadable(failure: Throwable) {
        readFailed = true
        // Error severity: `CrashReportingLogWriter` turns this into a Crashlytics non-fatal, the
        // only signal that an install is running on defaults rather than on its synced settings.
        log.e(failure) { "config_entry unreadable — resolving without this layer until it recovers" }
    }

    private companion object {
        const val TAG = "ConfigMirror"
    }
}

private fun ConfigEntryEntity.toPair(): Pair<String, String> = key to value
