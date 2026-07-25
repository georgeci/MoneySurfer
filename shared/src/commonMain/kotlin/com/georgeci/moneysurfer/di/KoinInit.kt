package com.georgeci.moneysurfer.di

import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.logging.configureLogging
import com.georgeci.moneysurfer.domain.telemetry.CrashReporter
import com.georgeci.moneysurfer.domain.telemetry.installCrashReporting
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.plugin.module.dsl.startKoin

// Local-only platform bindings: MoneySurferDatabase + DataStore + AppInfo + the debug-overrides
// configuration layer. Provided per platform via expect/actual in shared. The `CrashReporter`
// binding comes from the host's wiring (`onlineWiring` / `offlineWiring`), not from here.
expect val sharedPlatformModule: Module

/**
 * Starts Koin with the [AppModule] graph + [sharedPlatformModule] + caller-provided
 * [extraModules]. Hosts pass remote/sync wiring (Firebase or no-op) via [extraModules].
 *
 * [isDebug] gates logging: release hosts pass `false` so PII-bearing Info/Debug
 * logs are muted (see [configureLogging]). Callers must pass their build-type
 * signal explicitly (Android `BuildConfig.DEBUG`, iOS `Platform.isDebugBinary`).
 * It also gates crash collection: only release builds report to Crashlytics.
 */
fun initKoin(
    isDebug: Boolean,
    extraModules: List<Module> = emptyList(),
    appDeclaration: KoinAppDeclaration = {},
): KoinApplication {
    configureLogging(isDebug)
    val koinApplication = startKoin<AppModule> {
        appDeclaration()
        modules(sharedPlatformModule)
        if (extraModules.isNotEmpty()) {
            modules(extraModules)
        }
    }

    // Hosts that wire a real reporter (Android/iOS online builds) get Kermit forwarded to
    // Crashlytics; offline/desktop/test graphs bind a no-op or nothing at all, hence
    // getOrNull.
    koinApplication.koin.getOrNull<CrashReporter>()?.let { crashReporter ->
        installCrashReporting(
            crashReporter = crashReporter,
            isDebug = isDebug,
            userIds = koinApplication.koin.getOrNull<SessionPointers>()?.currentFirebaseUid,
        )
    }

    return koinApplication
}
