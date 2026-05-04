package com.georgeci.moneysurfer.offline.di

import com.georgeci.moneysurfer.domain.telemetry.CrashReporter
import com.georgeci.moneysurfer.offline.noop.NoOpCrashReporter
import com.georgeci.moneysurfer.offline.noop.NoOpOutboxEnqueuer
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import org.koin.core.module.Module
import org.koin.dsl.module

private val offlineNoOpModule: Module = module {
    single<OutboxEnqueuer> { NoOpOutboxEnqueuer() }
    single<CrashReporter> { NoOpCrashReporter() }
}

/**
 * Modules layered on top of shared's `AppModule` graph for the offline build.
 * No data-remote, no sync-surfer, no sync/default — every remote-side
 * dependency is satisfied by an explicit no-op binding.
 */
val offlineWiring: List<Module> = listOf(
    offlineNoOpModule,
)
