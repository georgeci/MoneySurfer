package com.georgeci.moneysurfer.sync.version

/**
 * Version gate decoupled from domain. `:sync` checks this; `:sync-impl` provides the
 * adapter that reads from [com.georgeci.moneysurfer.domain.repositories.AppVersionGate].
 *
 * See block.md.
 */
interface SyncVersionGate {
    suspend fun refresh(): SyncVersionStatus
    fun isSyncAllowed(): Boolean
}

sealed interface SyncVersionStatus {
    data object Allowed : SyncVersionStatus
    data class Blocked(val message: String) : SyncVersionStatus
}
