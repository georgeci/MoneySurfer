package com.georgeci.moneysurfer.offline.di

import com.georgeci.moneysurfer.appconfig.BuildConfigSource
import com.georgeci.moneysurfer.appconfig.HostConfigKeys
import com.georgeci.moneysurfer.appconfig.RemoteGlobalConfigSource
import com.georgeci.moneysurfer.domain.firstrun.FirstRunSeeder
import com.georgeci.moneysurfer.domain.repositories.AppConfigRepository
import com.georgeci.moneysurfer.domain.repositories.AppVersionGate
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.ExchangeRateRemoteSource
import com.georgeci.moneysurfer.domain.repositories.RemoteDataResetRepository
import com.georgeci.moneysurfer.domain.repositories.SessionShutdownGate
import com.georgeci.moneysurfer.domain.repositories.UserAccountDeletionRepository
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import com.georgeci.moneysurfer.domain.telemetry.CrashReporter
import com.georgeci.moneysurfer.domain.usecase.DemoLoginUseCase
import com.georgeci.moneysurfer.domain.usecase.SeedDefaultsUseCase
import com.georgeci.moneysurfer.offline.firstrun.OfflineFirstRunSeeder
import com.georgeci.moneysurfer.offline.noop.NoOpAppConfigRepository
import com.georgeci.moneysurfer.offline.noop.NoOpAppVersionGate
import com.georgeci.moneysurfer.offline.noop.NoOpAuthRemoteRepository
import com.georgeci.moneysurfer.offline.noop.NoOpCrashReporter
import com.georgeci.moneysurfer.offline.noop.NoOpExchangeRateRemoteSource
import com.georgeci.moneysurfer.offline.noop.NoOpOutboxEnqueuer
import com.georgeci.moneysurfer.offline.noop.NoOpPendingMutationQueue
import com.georgeci.moneysurfer.offline.noop.NoOpRemoteDataResetRepository
import com.georgeci.moneysurfer.offline.noop.NoOpSessionShutdownGate
import com.georgeci.moneysurfer.offline.noop.NoOpUserAccountDeletionRepository
import com.georgeci.moneysurfer.offline.noop.NoOpUserRemoteRepository
import com.georgeci.moneysurfer.offline.noop.NoOpWorkspaceSyncer
import com.georgeci.moneysurfer.sync.repository.OutboxEnqueuer
import com.georgeci.moneysurfer.sync.repository.PendingMutationQueue
import org.koin.core.module.Module
import org.koin.dsl.module

private val offlineNoOpModule: Module = module {
    single<OutboxEnqueuer> { NoOpOutboxEnqueuer() }
    single<CrashReporter> { NoOpCrashReporter() }
    single<AuthRemoteRepository> { NoOpAuthRemoteRepository() }
    single<UserRemoteRepository> { NoOpUserRemoteRepository() }
    single<AppConfigRepository> { NoOpAppConfigRepository() }
    single<AppVersionGate> { NoOpAppVersionGate() }
    single<WorkspaceSyncer> { NoOpWorkspaceSyncer() }
    single<RemoteDataResetRepository> { NoOpRemoteDataResetRepository() }
    single<UserAccountDeletionRepository> { NoOpUserAccountDeletionRepository() }
    single<SessionShutdownGate> { NoOpSessionShutdownGate() }
    single<PendingMutationQueue> { NoOpPendingMutationQueue() }
    single<ExchangeRateRemoteSource> { NoOpExchangeRateRemoteSource() }
}

/**
 * Offline-only first-run seed: pre-creates demo user + default workspace + Cash account on a
 * clean install so the app lands on a populated Dashboard. Online builds bind their own no-op
 * (Firestore-backed onboarding handles empty workspaces).
 */
private val offlineFirstRunModule: Module = module {
    single<FirstRunSeeder> {
        OfflineFirstRunSeeder(
            session = get(),
            demoLoginUseCase = get<DemoLoginUseCase>(),
            seedDefaultsUseCase = get<SeedDefaultsUseCase>(),
        )
    }
}

/**
 * The offline host's Build layer, replacing the four flag data classes it used to override
 * individually.
 *
 * Sign-in is demo-only (no remote auth backend), the Transfer segment is out of the offline MVP
 * scope, and sync has no Firestore-backed implementation here at all. Keeping these host-owned is
 * what stops the offline build regressing to the online surface through Koin module load order.
 *
 * The RemoteGlobal layer binds `Empty`: the offline build has no Firestore, and `Empty` lives in
 * `app-config/api` precisely so binding it needs no dependency on a remote implementation.
 */
private val offlineConfigModule: Module = module {
    single<BuildConfigSource> {
        BuildConfigSource {
            put(HostConfigKeys.isOffline, true)
            put(HostConfigKeys.signInEmailPassword, false)
            put(HostConfigKeys.signInAnonymous, false)
            put(HostConfigKeys.signInDemo, true)
            put(HostConfigKeys.transferEnabled, false)
            put(HostConfigKeys.syncEnabled, false)
        }
    }
    single<RemoteGlobalConfigSource> { RemoteGlobalConfigSource.Empty }
}

/**
 * Modules layered on top of shared's `AppModule` graph for the offline build.
 * No data-remote, no sync-surfer, no sync/default — every remote-side
 * dependency is satisfied by an explicit no-op binding.
 */
val offlineWiring: List<Module> = listOf(
    offlineNoOpModule,
    offlineConfigModule,
    offlineFirstRunModule,
    OfflineKoinApp().module(),
)
