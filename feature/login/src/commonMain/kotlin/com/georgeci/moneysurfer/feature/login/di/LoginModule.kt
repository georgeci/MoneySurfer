package com.georgeci.moneysurfer.feature.login.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

/**
 * Note: `SignInFeatureConfig` is intentionally NOT registered here. Each host
 * (`composeApp` -> online, `composeAppOffline` -> demo-only) provides its own
 * binding so the offline override doesn't depend on Koin module load order.
 */
@Module
@ComponentScan("com.georgeci.moneysurfer.feature.login")
class LoginModule
