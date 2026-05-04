package com.georgeci.moneysurfer.sync.fixtures.network

import com.georgeci.moneysurfer.sync.network.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [NetworkMonitor] backed by a [MutableStateFlow]. Tests flip
 * connectivity via [setOnline] to drive `awaitNetwork` / bounded-timeout paths.
 */
class FakeNetworkMonitor(initial: Boolean = true) : NetworkMonitor {
    private val _online = MutableStateFlow(initial)
    override val online: StateFlow<Boolean> = _online.asStateFlow()
    fun setOnline(value: Boolean) { _online.value = value }
}
