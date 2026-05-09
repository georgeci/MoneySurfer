package com.georgeci.moneysurfer.sync.noop.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

/**
 * Koin module that registers the offline-only no-op replacements for the
 * sync layer. Mirrors `:sync:default`'s `SyncModule` so an `@KoinApplication`
 * aggregator can include it via `includes = [SyncNoOpModule::class]`.
 */
@Module
@ComponentScan("com.georgeci.moneysurfer.sync.noop")
class SyncNoOpModule
