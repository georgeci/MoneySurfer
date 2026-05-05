package com.georgeci.moneysurfer.di

import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.plugin.module.dsl.startKoin

// Local-only platform bindings: MoneySurferDatabase + DataStore + AppInfo + default
// CrashReporter (no-op). Provided per platform via expect/actual in shared.
expect val sharedPlatformModule: Module

/**
 * Starts Koin with the [AppModule] graph + [sharedPlatformModule] + caller-provided
 * [extraModules]. Hosts pass remote/sync wiring (Firebase or no-op) via [extraModules].
 */
fun initKoin(
    extraModules: List<Module> = emptyList(),
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication {
    return startKoin<AppModule> {
        appDeclaration()
        modules(sharedPlatformModule)
        if (extraModules.isNotEmpty()) {
            modules(extraModules)
        }
    }
}
