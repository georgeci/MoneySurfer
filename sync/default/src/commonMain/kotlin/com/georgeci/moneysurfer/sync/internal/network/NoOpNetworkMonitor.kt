package com.georgeci.moneysurfer.sync.internal.network

import com.georgeci.moneysurfer.sync.network.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Always-online stub. Used by tests and environments where real network
 * monitoring is not needed (e.g. unit tests, embedded scenarios).
 *
 * Not registered in Koin — platform-specific implementations in `:sync-impl`
 * provide the `@Single` binding for each target.
 */
internal class NoOpNetworkMonitor : NetworkMonitor {
    override val online: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
}
