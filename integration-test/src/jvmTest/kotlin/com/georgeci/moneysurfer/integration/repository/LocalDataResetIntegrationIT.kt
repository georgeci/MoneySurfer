package com.georgeci.moneysurfer.integration.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.georgeci.moneysurfer.appconfig.ConfigKey
import com.georgeci.moneysurfer.appconfig.ConfigKeyGroup
import com.georgeci.moneysurfer.appconfig.LayerValue
import com.georgeci.moneysurfer.appconfig.SessionConfigOverlayImpl
import com.georgeci.moneysurfer.appconfig.SettingKey
import com.georgeci.moneysurfer.data.config.LocalConfigSourceImpl
import com.georgeci.moneysurfer.data.datastore.createDataStore
import com.georgeci.moneysurfer.data.repository.LocalDataResetRepositoryImpl
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.integration.fixtures.IntegrationHarness
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.nio.file.Files

private val THEME: SettingKey<String> = SettingKey.string("ui.theme_mode", default = "System", sync = true)

/** Device-scoped: it must survive the wipe, which is why it is not in `config_entry` at all. */
private val ONBOARDING: SettingKey<Boolean> =
    SettingKey.bool("ui.onboarding_completed", default = false, sync = false)

/**
 * The account wipe, end to end against the real database and a real DataStore file.
 *
 * The three properties are inseparable and none of them is provable against a fake: synced settings
 * are deleted, device-scoped settings are not, and the deleted values survive *in memory* long
 * enough for the screen the user is looking at to keep its theme.
 */
class LocalDataResetIntegrationIT : StringSpec({

    lateinit var harness: IntegrationHarness

    // Stands in for the application scope the graph binds: the preferences mirror keeps one
    // collection of the store on it for as long as the source lives.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    afterSpec { scope.cancel() }

    beforeEach { harness = IntegrationHarness() }
    afterEach { harness.close() }

    fun store(): DataStore<Preferences> {
        val dir = Files.createTempDirectory("ms-reset-test")
        return createDataStore { dir.resolve("config.preferences_pb").toString() }
    }

    fun localConfig(preferences: DataStore<Preferences> = store()) = LocalConfigSourceImpl(
        preferences,
        scope,
        harness.database.configEntryDao(),
        ClockUseCase(),
        ConfigOnlyOutbox,
    )

    "synced settings are deleted, so the next user cannot inherit them or win LWW with them" {
        val local = localConfig()
        local.write(THEME, "Dark")
        val overlay = SessionConfigOverlayImpl()
        val reset = LocalDataResetRepositoryImpl(harness.database, local, overlay, listOf(TestKeys))

        reset.clearAll()

        harness.database.configEntryDao().getAll().shouldBeEmpty()
    }

    "the layer stops serving the wiped values immediately, not once Room gets around to it" {
        // `peek` is synchronous and answers from an in-memory snapshot that Room's invalidation
        // only reaches through a cold flow — with no collector, never. Deleting the rows underneath
        // it would leave this account's theme resolving from the Local layer for the life of the
        // process, and `SyncedSettingsSession.onSessionStart()` clears the overlay that was
        // legitimately masking it, so the next user would inherit it.
        val local = localConfig()
        local.write(THEME, "Dark")
        local.peek(THEME) shouldBe LayerValue.Present("Dark")
        val reset = LocalDataResetRepositoryImpl(
            harness.database,
            local,
            SessionConfigOverlayImpl(),
            listOf(TestKeys),
        )

        reset.clearAll()

        local.peek(THEME) shouldBe LayerValue.Absent
    }

    "the wiped values are held in memory, so the running UI keeps its theme" {
        val local = localConfig()
        local.write(THEME, "Dark")
        val overlay = SessionConfigOverlayImpl()
        val reset = LocalDataResetRepositoryImpl(harness.database, local, overlay, listOf(TestKeys))

        reset.clearAll()

        overlay.peek(THEME) shouldBe LayerValue.Present("Dark")
    }

    "a device-scoped setting is neither wiped nor held" {
        // Resetting `ui.onboarding_completed` would replay onboarding after every logout, and
        // holding it in the overlay would be pointless — nothing wiped it.
        val preferences = store()
        val local = localConfig(preferences)
        local.write(ONBOARDING, true)
        val overlay = SessionConfigOverlayImpl()
        val reset = LocalDataResetRepositoryImpl(harness.database, local, overlay, listOf(TestKeys))

        reset.clearAll()

        val reopened = localConfig(preferences)
        reopened.hydrate()
        reopened.peek(ONBOARDING) shouldBe LayerValue.Present(true)
        overlay.peek(ONBOARDING) shouldBe LayerValue.Absent
    }

    "a key the user never set is not materialised into the overlay" {
        // Holding a default would put it above the next user's pulled value — a read that writes,
        // by another name.
        val overlay = SessionConfigOverlayImpl()
        val local = localConfig()
        local.hydrate()
        val reset = LocalDataResetRepositoryImpl(harness.database, local, overlay, listOf(TestKeys))

        reset.clearAll()

        overlay.peek(THEME) shouldBe LayerValue.Absent
    }
})

private object TestKeys : ConfigKeyGroup {
    override val keys: List<ConfigKey<*>> = listOf(THEME, ONBOARDING)
}

/** The wipe path never pushes; a write here is only setup. */
private object ConfigOnlyOutbox : OutboxEnqueuer {
    override suspend fun enqueueUpsert(
        entityType: String,
        entityId: String,
        scopeKey: String?,
        operation: MutationOperation,
    ) {
        check(entityType == SyncEntityTypes.USER_CONFIG) { "unexpected enqueue of $entityType" }
    }

    override suspend fun enqueueDelete(entityType: String, entityId: String, scopeKey: String?) = Unit

    override suspend fun isEnabled(): Boolean = false
}
