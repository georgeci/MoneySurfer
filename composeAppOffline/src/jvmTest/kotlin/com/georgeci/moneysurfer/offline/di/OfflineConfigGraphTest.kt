package com.georgeci.moneysurfer.offline.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.georgeci.moneysurfer.appconfig.BuildConfigSource
import com.georgeci.moneysurfer.appconfig.ConfigKeyGroup
import com.georgeci.moneysurfer.appconfig.DebugConfigSource
import com.georgeci.moneysurfer.appconfig.HostConfigKeys
import com.georgeci.moneysurfer.appconfig.LayerValue
import com.georgeci.moneysurfer.appconfig.RemoteGlobalConfigSource
import com.georgeci.moneysurfer.data.db.MoneySurferDatabase
import com.georgeci.moneysurfer.di.AppModule
import com.georgeci.moneysurfer.di.module
import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.backup.BackupStorageLocator
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/**
 * Mirrors composeApp's `OnlineConfigGraphTest` for the offline graph, and pins the offline surface
 * the four deleted mirror tests used to guard: demo-only sign-in, no transfers, sync off.
 *
 * Resolves the bindings for real rather than walking the graph shape, so a future module-load
 * reordering that let an online default win would fail here.
 */
class OfflineConfigGraphTest : StringSpec({

    "every registered configuration key name is unique across the whole graph" {
        withOfflineGraph { koin ->
            val names = koin.getAll<ConfigKeyGroup>().flatMap { group -> group.keys }.map { it.name }
            names.groupBy { it }.filterValues { it.size > 1 }.keys.shouldBeEmpty()
        }
    }

    "offline build layer declares every host key" {
        withOfflineGraph { koin ->
            val build = koin.get<BuildConfigSource>()
            HostConfigKeys.all.filter { build.peek(it) == LayerValue.Absent }.shouldBeEmpty()
        }
    }

    "offline build resolves isOffline true" {
        withOfflineGraph { koin ->
            koin.get<BuildConfigSource>().peek(HostConfigKeys.isOffline) shouldBe LayerValue.Present(true)
        }
    }

    "offline build offers only the demo sign-in entry point" {
        withOfflineGraph { koin ->
            val build = koin.get<BuildConfigSource>()
            build.peek(HostConfigKeys.signInEmailPassword) shouldBe LayerValue.Present(false)
            build.peek(HostConfigKeys.signInAnonymous) shouldBe LayerValue.Present(false)
            build.peek(HostConfigKeys.signInDemo) shouldBe LayerValue.Present(true)
        }
    }

    "offline build hides the Transfer segment" {
        withOfflineGraph { koin ->
            koin.get<BuildConfigSource>().peek(HostConfigKeys.transferEnabled) shouldBe LayerValue.Present(false)
        }
    }

    "offline build keeps sync off — there is no Firestore-backed implementation here" {
        withOfflineGraph { koin ->
            koin.get<BuildConfigSource>().peek(HostConfigKeys.syncEnabled) shouldBe LayerValue.Present(false)
        }
    }

    "offline build binds the empty RemoteGlobal layer" {
        withOfflineGraph { koin ->
            koin.get<RemoteGlobalConfigSource>() shouldBe RemoteGlobalConfigSource.Empty
        }
    }
})

private fun withOfflineGraph(block: (org.koin.core.Koin) -> Unit) {
    val app = koinApplication {
        modules(AppModule().module(), configTestPlatformModule)
        modules(offlineWiring)
    }
    try {
        block(app.koin)
    } finally {
        app.close()
    }
}

private val configTestPlatformModule = module {
    // Mirrors `applicationScopeModule` — see composeApp's `KoinModuleVerificationTest`.
    single<CoroutineScope> { error("this test must not instantiate the application scope") }
    single<MoneySurferDatabase> { error("this test must not instantiate the database") }
    single<DataStore<Preferences>> { error("this test must not instantiate DataStore") }
    single<DebugConfigSource> { DebugConfigSource.Empty }
    single { AppInfo(version = "test", versionCode = 1) }
    single<BackupStorageLocator> { error("this test must not instantiate the backup locator") }
}
