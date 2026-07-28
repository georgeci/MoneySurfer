package com.georgeci.moneysurfer.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.georgeci.moneysurfer.appconfig.ConfigKey
import com.georgeci.moneysurfer.appconfig.LayerValue
import com.georgeci.moneysurfer.appconfig.LocalConfigSource
import com.georgeci.moneysurfer.appconfig.SettingKey
import com.georgeci.moneysurfer.appconfig.layerValueOf
import com.georgeci.moneysurfer.data.db.dao.ConfigEntryDao
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

/**
 * Device-local user settings, split across two stores by [SettingKey.sync].
 *
 * | | `sync = true` | `sync = false` |
 * | --- | --- | --- |
 * | Store | Room `config_entry` | the app's DataStore file |
 * | Scope | the account — wiped by `LocalDataResetRepositoryImpl.clearAll()` | the device — never wiped |
 * | Replication | pushed through the outbox, pulled per user | none |
 *
 * The split is what makes both halves correct at once: the push plugin needs `value + updatedAt`
 * readable by key and LWW needs a real `updatedAt` column, while `ui.onboarding_completed` must
 * survive the logout that resets the account's settings. Callers see none of it — they hold a
 * `Pref<T>` and the routing lives here.
 *
 * A plain (non-`SettingKey`) key is host- or server-owned and can never be written locally, so it
 * reads from DataStore, which is where the Local layer has always looked for it.
 *
 * DataStore values are stored under a `config.` prefix, which is what keeps this layer clear of the
 * legacy typed preferences in the same file: `ui.onboarding_completed` used to be a
 * `booleanPreferencesKey`, and `Preferences.Key` equality is by name only, so reading it back
 * through a `stringPreferencesKey` of the same name would throw on any existing install. The key
 * *name* stays unprefixed everywhere it is user- or wire-visible — the debug panel, the
 * `config_entry` primary key, and `users/{uid}/config/{keyName}`; the prefix belongs to that one
 * store.
 */
@Single(binds = [LocalConfigSource::class])
class LocalConfigSourceImpl(
    dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
    configEntryDao: ConfigEntryDao,
    private val clock: ClockUseCase,
    private val outbox: OutboxEnqueuer,
) : LocalConfigSource {

    private val preferences = PreferencesMirror(dataStore, scope)
    private val synced = ConfigEntryMirror(configEntryDao)

    override fun <T : Any> peek(key: ConfigKey<T>): LayerValue<T> = when {
        key.isSynced -> key.layerValueOf(synced.raw(key.name))
        else -> key.layerValueOf(preferences.raw(key.preferenceName))
    }

    /**
     * Either store changing re-resolves the layer. `combine` waits for both to emit, which they both
     * do immediately with their current state.
     */
    override val changes: Flow<Unit> = combine(preferences.changes, synced.changes) { }

    /**
     * The two mirrors warm concurrently because they read different stores — a DataStore file and
     * Room — and this call blocks the splash: `AppLaunchViewModel` awaits it before a start route
     * exists. Serialising them would put the database open (and, on the first launch after an
     * upgrade, its migration) end-to-end behind the preferences read for no reason.
     */
    override suspend fun hydrate() = coroutineScope {
        val preferencesWarm = launch { preferences.hydrate() }
        val syncedWarm = launch { synced.hydrate() }
        preferencesWarm.join()
        syncedWarm.join()
    }

    override val isDegraded: Boolean get() = preferences.isDegraded || synced.isDegraded

    /**
     * Writes the value, then queues the push — a dual-write, not a transaction: the outbox lives in
     * a different database (`moneysurfer_sync.db`), so nothing here can be atomic with it. A crash
     * in between leaves a row whose `updatedAt` is newer than its `lastPushedAt`, which is what the
     * sign-in reconciliation looks for.
     *
     * The enqueue is unconditional on purpose. `OutboxEnqueuerImpl` already no-ops without a
     * Firebase uid (demo and signed-out sessions) and the offline host binds `NoOpOutboxEnqueuer`,
     * so gating it here would only duplicate that decision somewhere that cannot see it.
     */
    override suspend fun <T : Any> write(key: SettingKey<T>, value: T) {
        val raw = key.codec.encode(value)
        if (key.sync) {
            synced.write(key = key.name, value = raw, updatedAt = clock.now().toEpochMilliseconds())
            outbox.enqueueUpsert(
                entityType = SyncEntityTypes.USER_CONFIG,
                // One key is one entity: the key name *is* the document id, and settings are
                // user-scoped, so there is no workspace to scope them to.
                entityId = key.name,
                scopeKey = null,
                operation = MutationOperation.UPDATE,
            )
        } else {
            preferences.edit { it[stringPreferencesKey(key.preferenceName)] = raw }
        }
    }

    /**
     * Drops `config_entry` and retires the in-memory snapshot in the same call, so `peek` stops
     * serving the wiped account's settings immediately. The DataStore half is untouched — that is
     * the whole point of the split: `ui.onboarding_completed` must survive the wipe.
     */
    override suspend fun clearSynced() = synced.deleteAll()

    private val ConfigKey<*>.isSynced: Boolean get() = this is SettingKey && sync

    private val ConfigKey<*>.preferenceName: String get() = PREFIX + name

    private companion object {
        const val PREFIX = "config."
    }
}
