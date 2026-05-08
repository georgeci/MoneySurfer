package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.domain.firstrun.FirstRunSeeder
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Online build has nothing to seed at first run — Firestore-backed onboarding (sign-in,
 * workspace selector / creation) handles the empty-state path. Bind a no-op so
 * `AppLaunchViewModel` can depend on `FirstRunSeeder` unconditionally; mirrors the offline
 * override in `OfflineWiring.offlineFirstRunModule`.
 */
@Module
class OnlineFirstRunModule {

    @Single
    fun firstRunSeeder(): FirstRunSeeder = FirstRunSeeder { /* no-op */ }
}
