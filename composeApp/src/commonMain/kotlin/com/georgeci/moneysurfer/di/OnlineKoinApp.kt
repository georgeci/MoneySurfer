package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.appconfig.di.ConfigModule
import com.georgeci.moneysurfer.appconfig.remote.di.RemoteConfigModule
import com.georgeci.moneysurfer.data.di.RemoteDataModule
import com.georgeci.moneysurfer.data.di.SyncImplModule
import com.georgeci.moneysurfer.sync.internal.di.SyncModule
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

// koin-compiler generates a `.module` accessor only for `@KoinApplication` classes,
// not for plain `@Module`. We need ONE accessor that pulls in the three remote/sync
// modules so the host can layer them on top of the shared `AppModule` graph at
// runtime, without including them in shared (which must stay Firebase-free).
//
// `ConfigModule` is included here rather than in shared's `AppModule` for the same reason the
// remote modules are: `app-config/default` is a host dependency, and the Build layer it assembles
// is host-specific.
//
// `RemoteConfigModule` is online-only: it binds the RemoteGlobal layer to `appConfig/flags`, and the
// offline build binds `RemoteGlobalConfigSource.Empty` in its own wiring instead.
@KoinApplication
@Module(
    includes = [
        RemoteDataModule::class,
        SyncImplModule::class,
        SyncModule::class,
        ConfigModule::class,
        RemoteConfigModule::class,
        OnlineHostConfigModule::class,
        OnlineFirstRunModule::class,
    ],
)
class OnlineKoinApp
