package com.georgeci.moneysurfer.sync.network

import com.georgeci.moneysurfer.sync.runtime.ApplicationScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_get_status
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create

/**
 * iOS [NetworkMonitor] backed by Network.framework path monitoring.
 *
 * Uses a dedicated serial `DispatchQueue` for callbacks so path updates never
 * block the main thread. The Kotlin `MutableStateFlow` is thread-safe — updating
 * it from the dispatch queue is safe.
 *
 * [AppScope] is accepted for API symmetry with [AndroidNetworkMonitor] and
 * future use (e.g. launching a cleanup coroutine on app-termination signal).
 */
@OptIn(ExperimentalForeignApi::class)
@Single(binds = [NetworkMonitor::class])
class IosNetworkMonitor(
    @Suppress("UNUSED_PARAMETER") appScope: ApplicationScope,
) : NetworkMonitor {

    private val _online = MutableStateFlow(false)
    override val online: StateFlow<Boolean> = _online.asStateFlow()

    private val monitor = nw_path_monitor_create()
    private val queue = dispatch_queue_create("sync.network.monitor", null)

    init {
        nw_path_monitor_set_update_handler(monitor) { path ->
            _online.value = nw_path_get_status(path) == nw_path_status_satisfied
        }
        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_start(monitor)
    }
}
