package com.georgeci.moneysurfer.sync.network

import kotlinx.coroutines.flow.StateFlow

/**
 * Reports whether the device currently has network connectivity.
 *
 * Platform implementations live in `:data` (Android `ConnectivityManager`,
 * iOS `NWPathMonitor`, desktop stub). Phase 0 ships only the interface;
 * tests use an in-memory fake.
 *
 * See SyncCoordinatorFAQ.md №9.
 */
interface NetworkMonitor {
    val online: StateFlow<Boolean>
}
