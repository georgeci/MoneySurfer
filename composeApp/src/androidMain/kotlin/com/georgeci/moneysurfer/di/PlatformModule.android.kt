package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.appconfig.RemoteConfigMirror
import com.georgeci.moneysurfer.data.config.createRemoteConfigMirror
import com.georgeci.moneysurfer.data.remote.androidRemoteDataModule
import com.georgeci.moneysurfer.data.repository.FirebaseCrashReporter
import com.georgeci.moneysurfer.domain.telemetry.CrashReporter
import com.georgeci.moneysurfer.sync.db.SyncDatabase
import com.georgeci.moneysurfer.sync.db.getSyncDatabaseBuilder
import com.georgeci.moneysurfer.sync.db.getSyncRoomDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

private val onlinePlatformModule: Module = module {
    includes(androidRemoteDataModule)
    single<SyncDatabase> { getSyncRoomDatabase(getSyncDatabaseBuilder(context = get())) }
    single<CrashReporter> { FirebaseCrashReporter() }
    // Server-flag mirror, on its own DataStore file created inside the factory rather than bound —
    // a second unqualified `DataStore<Preferences>` would collide with the app's own one. Bound per
    // host rather than in `sharedPlatformModule`, because only the online build has a remote layer.
    single<RemoteConfigMirror> { createRemoteConfigMirror(context = get(), scope = get()) }
}

val onlineWiring: List<Module> = listOf(
    onlinePlatformModule,
    OnlineKoinApp().module(),
)
