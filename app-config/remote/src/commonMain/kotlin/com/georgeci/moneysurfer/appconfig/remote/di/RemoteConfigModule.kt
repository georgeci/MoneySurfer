package com.georgeci.moneysurfer.appconfig.remote.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

/**
 * The RemoteGlobal layer's assembly, included by the online host's `@KoinApplication` aggregator
 * next to the other Firestore-bound modules.
 *
 * Separate from `app-config/default`'s `ConfigModule` because that one is included by **both** hosts:
 * the offline build assembles the same engine and binds `RemoteGlobalConfigSource.Empty` itself.
 */
@Module
@ComponentScan("com.georgeci.moneysurfer.appconfig.remote")
class RemoteConfigModule
