package com.georgeci.moneysurfer.di

import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.plugin.module.dsl.startKoin

expect val platformModule: Module

fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication {
    return startKoin<AppModule> {
        appDeclaration()
        modules(platformModule)
    }
}
