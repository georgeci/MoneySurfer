package com.georgeci.moneysurfer.sync.internal.network

import com.georgeci.moneysurfer.sync.api.SyncReason
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Policy for how long the coordinator should wait for the network to come back
 * before failing with [com.georgeci.moneysurfer.sync.api.SyncError.NetworkUnavailable].
 *
 * - [Indefinite] — wait forever; the user can cancel via [com.georgeci.moneysurfer.sync.coordinator.SyncHandle.cancel].
 * - [Bounded] — fail after `timeout` elapses.
 *
 * See SyncCoordinatorFAQ.md №9.
 */
sealed interface NetworkWaitMode {
    data object Indefinite : NetworkWaitMode
    data class Bounded(val timeout: Duration) : NetworkWaitMode
}

fun SyncReason.toNetworkWaitMode(): NetworkWaitMode = when (this) {
    SyncReason.BACKGROUND -> NetworkWaitMode.Bounded(5.minutes)
    SyncReason.APP_START -> NetworkWaitMode.Bounded(30.seconds)
    SyncReason.FOREGROUND,
    SyncReason.MANUAL,
    SyncReason.SWIPE_REFRESH,
    SyncReason.LOCAL_CHANGE,
    -> NetworkWaitMode.Indefinite
}

/**
 * Merges wait modes from a set of reasons (when multiple sync requests are merged):
 * picks the strictest (smallest) bounded timeout, otherwise [Indefinite].
 */
fun Set<SyncReason>.toNetworkWaitMode(): NetworkWaitMode {
    val boundedMin = this
        .map { it.toNetworkWaitMode() }
        .filterIsInstance<NetworkWaitMode.Bounded>()
        .minByOrNull { it.timeout.inWholeMilliseconds }
    return boundedMin ?: NetworkWaitMode.Indefinite
}
