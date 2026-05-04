package com.georgeci.moneysurfer.sync.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.georgeci.moneysurfer.sync.runtime.ApplicationScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.stateIn
import org.koin.core.annotation.Single

/**
 * Android [NetworkMonitor] backed by [ConnectivityManager.registerNetworkCallback].
 *
 * Emits `true` when at least one network with [NetworkCapabilities.NET_CAPABILITY_INTERNET]
 * becomes available; `false` when the last such network is lost.
 *
 * Requires `ACCESS_NETWORK_STATE` permission (declared in this module's manifest).
 *
 * [SharingStarted.Eagerly] keeps the callback registered for the lifetime of [appScope]
 * so the first collector gets the current state without a cold-start gap.
 */
@Single(binds = [NetworkMonitor::class])
class AndroidNetworkMonitor(
    context: Context,
    appScope: ApplicationScope,
) : NetworkMonitor {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override val online: StateFlow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) {
                // Only go offline when no other capable network is available.
                trySend(isCurrentlyOnline())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.stateIn(
        scope = appScope,
        started = SharingStarted.Eagerly,
        initialValue = isCurrentlyOnline(),
    )

    private fun isCurrentlyOnline(): Boolean =
        connectivityManager.activeNetwork
            ?.let { connectivityManager.getNetworkCapabilities(it) }
            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ?: false
}
