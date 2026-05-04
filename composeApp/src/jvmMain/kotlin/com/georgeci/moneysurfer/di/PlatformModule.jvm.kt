package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.data.repository.NoOpCrashReporter
import com.georgeci.moneysurfer.domain.telemetry.CrashReporter
import com.georgeci.moneysurfer.sync.db.SyncDatabase
import com.georgeci.moneysurfer.sync.db.getSyncDatabaseBuilder
import com.georgeci.moneysurfer.sync.db.getSyncRoomDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

private val onlinePlatformModule: Module = module {
    single<SyncDatabase> { getSyncRoomDatabase(getSyncDatabaseBuilder()) }
    single<CrashReporter> { NoOpCrashReporter() }
}

val onlineWiring: List<Module> = listOf(
    onlinePlatformModule,
    OnlineKoinApp().module(),
)
