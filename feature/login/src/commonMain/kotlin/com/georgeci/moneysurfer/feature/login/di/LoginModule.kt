package com.georgeci.moneysurfer.feature.login.di

import com.georgeci.moneysurfer.feature.login.SignInFeatureConfig
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("com.georgeci.moneysurfer.feature.login")
class LoginModule {

    @Single
    fun signInFeatureConfig(): SignInFeatureConfig = SignInFeatureConfig()
}

