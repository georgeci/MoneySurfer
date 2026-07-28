package com.georgeci.moneysurfer.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The process-lifetime [CoroutineScope], included by every `sharedPlatformModule` actual.
 *
 * It exists so a binding can keep a collection running for as long as the graph does. The
 * configuration layers are the first users: each `PreferencesMirror` collects its DataStore once
 * here and shares the result, instead of handing every `Config.observe` collector its own
 * subscription to all four layers.
 *
 * [SupervisorJob] so one failed collection does not cancel the others, and [Dispatchers.Default]
 * because the work is store reads and flow bookkeeping, never UI.
 *
 * Deliberately not `sync`'s `ApplicationScope`: that one is bound by the sync graph, which the
 * offline host does not have, and `:data-local` cannot depend on `:sync` anyway. This is the plain
 * `CoroutineScope` binding, and it is the only one in the graph.
 */
internal val applicationScopeModule: Module = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
}
