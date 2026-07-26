package com.georgeci.moneysurfer.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.georgeci.moneysurfer.appconfig.BuildConfigSource
import com.georgeci.moneysurfer.appconfig.ConfigKeyGroup
import com.georgeci.moneysurfer.appconfig.ConfigRegistry
import com.georgeci.moneysurfer.appconfig.DebugConfigSource
import com.georgeci.moneysurfer.appconfig.HostConfigKeys
import com.georgeci.moneysurfer.appconfig.LayerValue
import com.georgeci.moneysurfer.appconfig.RemoteConfigMirror
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.backup.AppRestarter
import com.georgeci.moneysurfer.domain.backup.BackupStorageLocator
import com.georgeci.moneysurfer.sync.db.SyncDatabase
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Configuration invariants that only hold at the host level.
 *
 * Key objects are `internal` to the modules that own them, so no single module can see them all —
 * the assembled Koin graph is the only place where every registered group is visible at once. That
 * is also why this test resolves bindings for real instead of walking the graph shape like
 * `KoinModuleVerificationTest` does.
 */
class OnlineConfigGraphTest : StringSpec({

    "every registered configuration key name is unique across the whole graph" {
        withOnlineGraph { koin ->
            val names = koin.getAll<ConfigKeyGroup>().flatMap { group -> group.keys }.map { it.name }
            // A duplicate name silently collapses in `ConfigRegistry.byName`, hiding one of the two
            // keys from the debug panel and from the remote-config pull.
            names.groupBy { it }.filterValues { it.size > 1 }.keys.shouldBeEmpty()
        }
    }

    "key groups bind as distinct classes so getAll sees all of them" {
        withOnlineGraph { koin ->
            val groups = koin.getAll<ConfigKeyGroup>()
            // Identical primary types would overwrite one another in Koin's definition index and
            // `getAll()` would return a single surviving group.
            groups.map { it::class }.distinct().size shouldBe groups.size
        }
    }

    "the registry receives every registered group" {
        withOnlineGraph { koin ->
            // Also proves Koin's `List<ConfigKeyGroup>` injection works end to end: an empty or
            // truncated list here is how the debug panel and the remote pull would silently lose
            // most keys.
            val registry = koin.get<ConfigRegistry>()

            registry.keys.size shouldBe koin.getAll<ConfigKeyGroup>().sumOf { it.keys.size }
            registry.find(HostConfigKeys.isOffline.name) shouldBe HostConfigKeys.isOffline
            registry.find("ui.theme_mode").shouldNotBeNull()
            registry.find("sync.user_enabled").shouldNotBeNull()
        }
    }

    "the server kill switch is registered and remote-overridable" {
        withOnlineGraph { koin ->
            // The one key the RemoteGlobal layer exists to carry, pinned against the *real* graph.
            // `RemoteGlobalConfigSourceImpl` drops a name the registry does not know with only a
            // log line, so renaming this key, dropping its `remoteOverridable`, or unbinding
            // `SyncConfigKeyGroup` would silently kill the switch — setting `sync.remote_enabled`
            // in `appConfig/flags` would do nothing in production while every suite stayed green.
            val killSwitch = koin.get<ConfigRegistry>().find("sync.remote_enabled")

            killSwitch.shouldNotBeNull()
            killSwitch.remoteOverridable shouldBe true
        }
    }

    "no host key is remote-overridable" {
        withOnlineGraph { koin ->
            // A server able to flip `host.is_offline` would put the online build on the offline
            // start route while its DI graph stays fully online.
            val registry = koin.get<ConfigRegistry>()

            HostConfigKeys.all.filter { registry.find(it.name)?.remoteOverridable == true }.shouldBeEmpty()
        }
    }

    "online build layer declares every host key" {
        withOnlineGraph { koin ->
            val build = koin.get<BuildConfigSource>()
            HostConfigKeys.all.filter { build.peek(it) == LayerValue.Absent }.shouldBeEmpty()
        }
    }

    "online build layer keeps the full surface, sync included" {
        withOnlineGraph { koin ->
            val build = koin.get<BuildConfigSource>()
            listOf(
                build.peek(HostConfigKeys.isOffline),
                build.peek(HostConfigKeys.signInEmailPassword),
                build.peek(HostConfigKeys.signInAnonymous),
                build.peek(HostConfigKeys.signInDemo),
                build.peek(HostConfigKeys.transferEnabled),
                build.peek(HostConfigKeys.syncEnabled),
            ) shouldContainExactly listOf(
                LayerValue.Present(false),
                LayerValue.Present(true),
                LayerValue.Present(true),
                LayerValue.Present(true),
                LayerValue.Present(true),
                // Sync is live in the online build since issue #342 — turning it off again is a
                // release decision, not a refactor, and belongs in AGENTS.md's dark-flag table.
                LayerValue.Present(true),
            )
        }
    }
})

private fun withOnlineGraph(block: (org.koin.core.Koin) -> Unit) {
    val app = koinApplication {
        modules(AppModule().module(), OnlineKoinApp().module(), configTestPlatformModule)
    }
    try {
        block(app.koin)
    } finally {
        app.close()
    }
}

/**
 * Same placeholders as `KoinModuleVerificationTest`: resolving the key groups and the build layer
 * never touches Room or DataStore, so these factories must not be called.
 */
private val configTestPlatformModule = module {
    single<MoneySurferDatabase> { error("this test must not instantiate the database") }
    single<SyncDatabase> { error("this test must not instantiate the sync database") }
    single<DataStore<Preferences>> { error("this test must not instantiate DataStore") }
    single<DebugConfigSource> { DebugConfigSource.Empty }
    single<RemoteConfigMirror> { error("this test must not instantiate the flag mirror") }
    single { AppInfo(version = "test", versionCode = 1) }
    single<BackupStorageLocator> { error("this test must not instantiate the backup locator") }
    single<AppRestarter> { error("this test must not instantiate the restarter") }
}
