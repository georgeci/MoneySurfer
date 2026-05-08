package com.georgeci.moneysurfer.offline.di

import com.georgeci.moneysurfer.domain.OfflineBuildFlags
import com.georgeci.moneysurfer.domain.repositories.AppConfigRepository
import com.georgeci.moneysurfer.domain.repositories.AppVersionGate
import com.georgeci.moneysurfer.domain.repositories.AuthRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.RemoteDataResetRepository
import com.georgeci.moneysurfer.domain.repositories.SessionShutdownGate
import com.georgeci.moneysurfer.domain.repositories.UserRemoteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceSyncer
import com.georgeci.moneysurfer.domain.telemetry.CrashReporter
import com.georgeci.moneysurfer.feature.login.SignInFeatureConfig
import com.georgeci.moneysurfer.feature.transaction.creation.TransactionCreationFeatureConfig
import com.georgeci.moneysurfer.offline.noop.NoOpAppConfigRepository
import com.georgeci.moneysurfer.offline.noop.NoOpAppVersionGate
import com.georgeci.moneysurfer.offline.noop.NoOpAuthRemoteRepository
import com.georgeci.moneysurfer.offline.noop.NoOpCrashReporter
import com.georgeci.moneysurfer.offline.noop.NoOpOutboxEnqueuer
import com.georgeci.moneysurfer.offline.noop.NoOpPendingMutationQueue
import com.georgeci.moneysurfer.offline.noop.NoOpRemoteDataResetRepository
import com.georgeci.moneysurfer.offline.noop.NoOpSessionShutdownGate
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
    single<SessionShutdownGate> { NoOpSessionShutdownGate() }
    single<PendingMutationQueue> { NoOpPendingMutationQueue() }
}

/**
 * Offline build shows a single "demo" entry on the sign-in screen — no
 * email/password or anonymous flows, since the offline build has no remote
 * auth backend wired up. This overrides the default registered by
 * `feature/login`'s `LoginModule`.
 */
private val offlineSignInModule: Module = module {
    single<SignInFeatureConfig> {
        SignInFeatureConfig(
            emailPassword = false,
            anonymous = false,
            demo = true,
        )
    }
    single<OfflineBuildFlags> { OfflineBuildFlags(isOffline = true) }
}

/**
 * Offline build hides the Transfer segment in transaction creation — multi-account
 * transfers are out of the offline MVP scope. Same host-owned binding pattern as
 * [offlineSignInModule] so the offline override can't regress through Koin module
 * load order.
 */
private val offlineTransactionCreationModule: Module = module {
    single<TransactionCreationFeatureConfig> {
        TransactionCreationFeatureConfig(transferEnabled = false)
    }
}

/**
 * Modules layered on top of shared's `AppModule` graph for the offline build.
 * No data-remote, no sync-surfer, no sync/default — every remote-side
 * dependency is satisfied by an explicit no-op binding.
 */
val offlineWiring: List<Module> = listOf(
    offlineNoOpModule,
    offlineSignInModule,
    offlineTransactionCreationModule,
    OfflineKoinApp().module(),
)
