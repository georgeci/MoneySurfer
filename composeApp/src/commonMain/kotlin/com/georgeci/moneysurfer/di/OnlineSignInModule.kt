package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.feature.login.SignInFeatureConfig
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Online build registers the full sign-in flow (email/password, anonymous,
 * demo). Mirrors `composeAppOffline`'s demo-only override; keeping the
 * binding host-owned (instead of defaulted in `feature/login`) means the
 * offline build can't silently regress to the full UI through module load
 * order changes.
 */
@Module
class OnlineSignInModule {

    @Single
    fun signInFeatureConfig(): SignInFeatureConfig = SignInFeatureConfig(
        emailPassword = true,
        anonymous = true,
        demo = true,
    )
}
