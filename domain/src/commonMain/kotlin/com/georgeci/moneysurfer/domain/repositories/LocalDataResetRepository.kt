package com.georgeci.moneysurfer.domain.repositories

/**
 * Wipes every local table — used by the logout flow to reset the device to a fresh state.
 * Implementation lives in `:data` since it knows the FK-safe deletion order.
 */
interface LocalDataResetRepository {
    suspend fun clearAll()
}
