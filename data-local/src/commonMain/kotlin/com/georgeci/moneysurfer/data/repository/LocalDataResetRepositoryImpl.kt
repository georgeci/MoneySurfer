package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.appconfig.ConfigKey
import com.georgeci.moneysurfer.appconfig.ConfigKeyGroup
import com.georgeci.moneysurfer.appconfig.LayerValue
import com.georgeci.moneysurfer.appconfig.LocalConfigSource
import com.georgeci.moneysurfer.appconfig.SessionConfigOverlay
import com.georgeci.moneysurfer.appconfig.SettingKey
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.domain.repositories.LocalDataResetRepository
import org.koin.core.annotation.Single

/**
 * Takes the database rather than one DAO per table — the parameter list grew
 * with every new table and bought nothing, since this class only ever fans out
 * `deleteAll()`.
 *
 * The configuration arguments are the exception: synced settings are account data and are wiped
 * here with everything else, but the *running UI* must not notice, so their current values are
 * lifted into the in-memory overlay on the way out.
 */
@Single(binds = [LocalDataResetRepository::class])
class LocalDataResetRepositoryImpl(
    private val database: MoneySurferDatabase,
    private val localConfig: LocalConfigSource,
    private val overlay: SessionConfigOverlay,
    private val keyGroups: List<ConfigKeyGroup>,
) : LocalDataResetRepository {

    // FK-safe order: leaf tables first, then parents.
    override suspend fun clearAll() {
        database.goalContributionDao().deleteAll()
        database.goalDao().deleteAll()
        database.recurringRuleDao().deleteAll()
        database.budgetDao().deleteAll()
        database.transactionDao().deleteAll()
        database.accountDao().deleteAll()
        database.categoryDao().deleteAll()
        database.workspaceInviteDao().deleteAll()
        database.workspaceMemberDao().deleteAll()
        database.workspaceDao().deleteAll()
        database.userDao().deleteAll()
        // Not user data — public FX quotes — but "reset local data" should leave no rows behind,
        // and the cost of forgetting them is a single refetch on the next dashboard open.
        database.exchangeRateDao().deleteAll()
        // Synced settings are account data. Without this the next user on the device inherits the
        // previous one's theme *and* wins every LWW comparison against their own remote documents
        // (the local row is newer), so their real settings would never be pulled and never pushed.
        //
        // Through the config layer rather than `database.configEntryDao()`, unlike every line
        // above: the layer keeps an in-memory snapshot behind its synchronous `peek`, refreshed
        // only by Room's invalidation through a cold flow with no collector of its own. Deleting
        // the rows underneath it would leave `peek` serving this account's values until something
        // else happened to observe a setting — and the session overlay is cleared at the next
        // sign-in, so the new user would then see them.
        holdSyncedSettingsInMemory()
        localConfig.clearSynced()
    }

    /**
     * Copies the values about to be deleted into the overlay, so the screen the user is looking at
     * keeps its theme, palette and container style instead of flashing back to defaults the moment
     * they tap "log out". Nothing persists them, and `SyncedSettingsSession.onSessionStart()` drops
     * them before the next session's pull.
     *
     * Reads through `peek`, so it holds exactly what the Local layer holds: a key the user never set
     * is absent and stays absent, which keeps the overlay from materialising defaults that would
     * then outrank the next user's pulled values.
     */
    private fun holdSyncedSettingsInMemory() {
        val held = keyGroups
            .flatMap { it.keys }
            .filter { it is SettingKey && it.sync }
            .mapNotNull { key -> key.encodedLocalValue()?.let { key.name to it } }
        overlay.hold(held.toMap())
    }

    private fun <T : Any> ConfigKey<T>.encodedLocalValue(): String? =
        (localConfig.peek(this) as? LayerValue.Present)?.let { codec.encode(it.value) }
}
