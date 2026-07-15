package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.domain.logging.configureLogging
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
 *
 * [isDebug] gates logging: release hosts pass `false` so PII-bearing Info/Debug
 * logs are muted (see [configureLogging]). Callers must pass their build-type
 * signal explicitly (Android `BuildConfig.DEBUG`, iOS `Platform.isDebugBinary`).
 */
fun initKoin(
    isDebug: Boolean,
    extraModules: List<Module> = emptyList(),
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication {
    configureLogging(isDebug)
    return startKoin<AppModule> {
        appDeclaration()
        modules(sharedPlatformModule)
        if (extraModules.isNotEmpty()) {
            modules(extraModules)
        }
    }
}
