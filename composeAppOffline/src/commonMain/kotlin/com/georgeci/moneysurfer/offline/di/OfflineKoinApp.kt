package com.georgeci.moneysurfer.offline.di

import com.georgeci.moneysurfer.sync.noop.di.SyncNoOpModule
import org.koin.core.annotation.KoinApplication
import org.koin.core.annotation.Module

/**
 * Mirrors `:composeApp`'s `OnlineKoinApp`: koin-compiler generates a `.module`
 * accessor only for `@KoinApplication`-annotated classes, so we need one
 * aggregator that pulls in every offline-only Koin module before they can be
 * layered on top of shared's `AppModule` graph.
 */
@KoinApplication
@Module(
    includes = [
        SyncNoOpModule::class,
    ],
)
class OfflineKoinApp
