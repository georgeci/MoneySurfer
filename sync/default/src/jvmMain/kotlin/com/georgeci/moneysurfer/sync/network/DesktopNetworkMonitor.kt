package com.georgeci.moneysurfer.sync.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single

/**
 * Desktop (JVM) [NetworkMonitor] — always reports online.
 *
 * Plain JVM has no portable network-change notification API. The desktop
 * target is primarily used for development, so assuming connectivity is
 * online is a reasonable default. A real implementation would poll
 * `java.net.InetAddress.isReachable` or watch interface events via JNA,
 * but that complexity is not justified for the current use-case.
 */
@Single(binds = [NetworkMonitor::class])
class DesktopNetworkMonitor : NetworkMonitor {
    override val online: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
}
