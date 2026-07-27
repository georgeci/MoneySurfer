package com.georgeci.moneysurfer.data.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.georgeci.moneysurfer.appconfig.ConfigKey
import com.georgeci.moneysurfer.appconfig.LayerValue
import com.georgeci.moneysurfer.appconfig.SettingKey
import com.georgeci.moneysurfer.data.datastore.createDataStore
import com.georgeci.moneysurfer.data.db.dao.ConfigEntryDao
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.sync.SyncEntityTypes
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import java.nio.file.Files

/** Device-scoped: DataStore, never wiped, never pushed. */
private val DEVICE_KEY: SettingKey<Boolean> =
    SettingKey.bool("ui.onboarding_completed", default = false, sync = false)

/** Account-scoped: Room `config_entry`, wiped with the account, pushed through the outbox. */
private val SYNCED_KEY: SettingKey<String> =
    SettingKey.string("ui.theme_mode", default = "System", sync = true)

/** Host-owned. Not a `SettingKey`, so nothing local can write it — and nothing may serve it. */
private val HOST_KEY: ConfigKey<Boolean> = ConfigKey.bool("host.is_offline", default = false)

/** Server-owned kill switch, served only by the RemoteGlobal layer. */
private val SERVER_KEY: ConfigKey<Boolean> =
    ConfigKey.bool("sync.remote_enabled", default = true, remoteOverridable = true)

/**
 * Runs against a real on-disk DataStore and an in-memory stand-in for the `config_entry` DAO,
 * because what matters here is exactly what a fake store would paper over: which of the two stores
 * a key lands in, the `config.` namespace, and the warm/cold state of the mirror `peek` reads.
 */
class LocalConfigSourceImplJvmTest : StringSpec({

    // Stands in for the application scope the graph binds: the preferences mirror keeps one
    // collection of the store on it for as long as the source lives.
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    afterSpec { scope.cancel() }

    fun newStore(): DataStore<Preferences> {
        val dir = Files.createTempDirectory("ms-config-test")
        return createDataStore { dir.resolve("config.preferences_pb").toString() }
    }

    fun source(
        store: DataStore<Preferences> = newStore(),
        dao: ConfigEntryDao = FakeConfigEntryDao(),
        outbox: OutboxEnqueuer = RecordingOutbox(),
    ) = LocalConfigSourceImpl(store, scope, dao, ClockUseCase(), outbox)

    "a write is readable through peek the moment it returns" {
        // The preferences mirror's collection is eager, so a second source over the same store warms
        // itself and "cold reads absent" is no longer something this layer can be asked. What a
        // write still owes is that its own value is visible synchronously the moment it returns —
        // the mirror orders `edit`'s publication against the collection's to keep that true.
        val written = source()

        written.write(DEVICE_KEY, true)

        written.peek(DEVICE_KEY) shouldBe LayerValue.Present(true)
    }

    "a written value round-trips after hydrate" {
        val store = newStore()
        source(store).write(DEVICE_KEY, true)

        val reopened = source(store)
        reopened.hydrate()

        reopened.peek(DEVICE_KEY) shouldBe LayerValue.Present(true)
    }

    "values are namespaced so they cannot clash with the legacy typed preferences" {
        val store = newStore()
        // The pre-engine store wrote `ui.onboarding_completed` as a *boolean* preference. DataStore
        // keys compare by name only, so reading that entry back through a string key would throw on
        // every existing install — the prefix is what keeps the two apart.
        store.edit { it[booleanPreferencesKey(DEVICE_KEY.name)] = true }

        val local = source(store)
        local.hydrate()

        local.peek(DEVICE_KEY) shouldBe LayerValue.Absent
        store.data.first()[booleanPreferencesKey(DEVICE_KEY.name)] shouldBe true
    }

    "changes emits the current state and again after a write" {
        val local = source()

        local.changes.first() shouldBe Unit
        local.write(DEVICE_KEY, true)
        local.changes.first() shouldBe Unit
        local.peek(DEVICE_KEY) shouldBe LayerValue.Present(true)
    }

    "reading a key nobody wrote stores nothing" {
        // A read that materialised the default would make a fresh device upload its own defaults
        // before the first remote pull — and win LWW against the user's real settings, because the
        // local write is newer. Nothing in the read path may touch the store.
        val store = newStore()
        val dao = FakeConfigEntryDao()
        val local = source(store, dao)

        local.hydrate()
        local.peek(DEVICE_KEY) shouldBe LayerValue.Absent
        local.peek(SYNCED_KEY) shouldBe LayerValue.Absent

        store.data.first().asMap().keys.map { it.name } shouldNotContain "config.${DEVICE_KEY.name}"
        dao.getAll().shouldBeEmpty()
    }

    "an undecodable value is left on disk rather than overwritten by the fallback" {
        // Absent-in-that-layer is a *resolution* rule, not a repair: silently rewriting the value
        // would destroy the evidence the debug panel exists to show.
        val store = newStore()
        store.edit { it[stringPreferencesKey("config.${DEVICE_KEY.name}")] = "maybe" }
        val local = source(store)
        local.hydrate()

        local.peek(DEVICE_KEY) shouldBe LayerValue.Undecodable("maybe")

        store.data.first()[stringPreferencesKey("config.${DEVICE_KEY.name}")] shouldBe "maybe"
    }

    "a corrupt file degrades the layer instead of throwing out of hydrate or of the change flow" {
        // The app's own settings store has no `ReplaceFileCorruptionHandler` on purpose (it also
        // holds the session pointers), so `dataStore.data` throws `CorruptionException` for the life
        // of the install. Both the startup `hydrate()` and every `Config.observe` collector run
        // through here, and the startup one has no route to fall back to — so neither may throw.
        val dir = Files.createTempDirectory("ms-config-corrupt-test")
        val file = dir.resolve("config.preferences_pb")
        Files.write(file, byteArrayOf(0x1, 0x2, 0x3, 0x4, 0x5))
        val local = source(createDataStore { file.toString() })

        local.hydrate()

        local.isDegraded shouldBe true
        local.peek(DEVICE_KEY) shouldBe LayerValue.Absent
        // The flow a `Config.observe` collector subscribes to still emits rather than failing.
        local.changes.first() shouldBe Unit
    }

    // ── Routing by `sync` ─────────────────────────────────────────────────────

    "a synced key is stored in config_entry, not in the preferences file" {
        // The split is what makes the account wipe possible at all: `clearAll()` drops this table
        // and must not touch the device-scoped preferences beside it.
        val store = newStore()
        val dao = FakeConfigEntryDao()
        val local = source(store, dao)

        local.write(SYNCED_KEY, "Dark")

        local.peek(SYNCED_KEY) shouldBe LayerValue.Present("Dark")
        dao.getByKey(SYNCED_KEY.name)?.value shouldBe "Dark"
        // The key *name* is stored unprefixed — it is the Firestore document id as well.
        store.data.first().asMap().keys.map { it.name }.shouldBeEmpty()
    }

    "a device-scoped key never reaches config_entry" {
        val dao = FakeConfigEntryDao()
        val local = source(dao = dao)

        local.write(DEVICE_KEY, true)

        dao.getAll().shouldBeEmpty()
    }

    "a synced write stamps updatedAt and queues exactly one push" {
        val dao = FakeConfigEntryDao()
        val outbox = RecordingOutbox()
        val local = source(dao = dao, outbox = outbox)

        local.write(SYNCED_KEY, "Dark")

        // One key is one entity: the id is the key name and there is no workspace to scope it to.
        outbox.upserts shouldContainExactly listOf(
            Triple(SyncEntityTypes.USER_CONFIG, SYNCED_KEY.name, null),
        )
        (dao.getByKey(SYNCED_KEY.name)?.updatedAt ?: 0L) shouldBe dao.lastWriteAt
    }

    "a device-scoped write queues nothing" {
        val outbox = RecordingOutbox()

        source(outbox = outbox).write(DEVICE_KEY, true)

        outbox.upserts.shouldBeEmpty()
    }

    "a config_entry row named after a host or server key is never served" {
        // `users/{uid}/config` is self-writable by design, so a user *can* create a document called
        // `host.is_offline` or `sync.remote_enabled` and it *will* be pulled into this table. It
        // must not become a way to grant yourself the offline start route or to unkill a switch:
        // only a `SettingKey` with `sync = true` is read from here, and neither of those is one.
        val dao = FakeConfigEntryDao()
        val local = source(dao = dao)
        dao.applyRemote(key = HOST_KEY.name, value = "true", updatedAt = 1)
        dao.applyRemote(key = SERVER_KEY.name, value = "false", updatedAt = 1)
        local.hydrate()

        local.peek(HOST_KEY) shouldBe LayerValue.Absent
        local.peek(SERVER_KEY) shouldBe LayerValue.Absent
    }

    "a value written into config_entry by the pull becomes visible to peek" {
        // The pull writes through the DAO directly; Room's own invalidation is what republishes the
        // mirror, and without it a setting changed on another device would not retheme this one.
        val dao = FakeConfigEntryDao()
        val local = source(dao = dao)
        local.hydrate()

        dao.applyRemote(key = SYNCED_KEY.name, value = "Dark", updatedAt = 1)
        local.changes.first()

        local.peek(SYNCED_KEY) shouldBe LayerValue.Present("Dark")
    }

    "clearSynced empties config_entry and the snapshot behind peek, but not the preferences" {
        // Both halves matter: the account's settings have to be gone from the layer *now* (peek is
        // synchronous and Room's invalidation is not), and the device-scoped ones beside them have
        // to survive — resetting `ui.onboarding_completed` would replay onboarding after a logout.
        val store = newStore()
        val dao = FakeConfigEntryDao()
        val local = source(store, dao)
        local.write(SYNCED_KEY, "Dark")
        local.write(DEVICE_KEY, true)

        local.clearSynced()

        local.peek(SYNCED_KEY) shouldBe LayerValue.Absent
        dao.getAll().shouldBeEmpty()
        local.peek(DEVICE_KEY) shouldBe LayerValue.Present(true)
    }

    "an unreadable config_entry degrades the layer instead of throwing" {
        val dao = FakeConfigEntryDao(failReads = true)
        val local = source(dao = dao)

        local.hydrate()

        local.isDegraded shouldBe true
        local.peek(SYNCED_KEY) shouldBe LayerValue.Absent
        local.changes.first() shouldBe Unit
    }
})
