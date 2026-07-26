package com.georgeci.moneysurfer.appconfig.di

import com.georgeci.moneysurfer.appconfig.BuildConfigSource
import com.georgeci.moneysurfer.appconfig.Config
import com.georgeci.moneysurfer.appconfig.DebugConfigSource
import com.georgeci.moneysurfer.appconfig.LayeredConfig
import com.georgeci.moneysurfer.appconfig.LocalConfigSource
import com.georgeci.moneysurfer.appconfig.RemoteGlobalConfigSource
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Assembly of the configuration engine. Included by each host's `@KoinApplication` aggregator, so
 * `shared` stays free of `app-config`.
 *
 * No qualifiers: the four layers are distinct types declared in `app-config/api`, so Koin resolves
 * them positionally. Keys are not bound at all, so the graph does not grow with the number of
 * flags.
 *
 * The `@Single` lives inside a `@Module` class because the KSP processor does not accept `@Single`
 * on a top-level function.
 */
@Module
@ComponentScan("com.georgeci.moneysurfer.appconfig")
class ConfigModule {

    /**
     * Layer order is a literal list, not `getAll()` — precedence is a correctness property and
     * must not depend on module load order.
     *
     * `debug.isActive` doubles as the debug-build signal: only a debug build binds a real
     * [DebugConfigSource], so it is also when an early `snapshot()` should fail loudly.
     */
    @Single
    fun config(
        debug: DebugConfigSource,
        local: LocalConfigSource,
        remoteGlobal: RemoteGlobalConfigSource,
        build: BuildConfigSource,
    ): Config = LayeredConfig(
        layers = listOf(debug, local, remoteGlobal, build),
        local = local,
        failFastOnEarlySnapshot = debug.isActive,
    )
}
