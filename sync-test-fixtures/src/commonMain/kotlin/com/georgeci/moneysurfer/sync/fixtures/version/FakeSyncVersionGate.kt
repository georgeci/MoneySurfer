package com.georgeci.moneysurfer.sync.fixtures.version

import com.georgeci.moneysurfer.sync.version.SyncVersionGate
import com.georgeci.moneysurfer.sync.version.SyncVersionStatus

/**
 * In-memory [SyncVersionGate] for tests.
 *
 * Defaults to [SyncVersionStatus.Allowed]. Use [setStatus] to flip to
 * [SyncVersionStatus.Blocked] and verify sync-blocking behaviour.
 * [refreshCount] records every [refresh] call.
 */
class FakeSyncVersionGate(
    initial: SyncVersionStatus = SyncVersionStatus.Allowed,
) : SyncVersionGate {

    var refreshCount: Int = 0
        private set

    private var current: SyncVersionStatus = initial

    override suspend fun refresh(): SyncVersionStatus {
        refreshCount++
        return current
    }

    override fun isSyncAllowed(): Boolean = current !is SyncVersionStatus.Blocked

    fun setStatus(status: SyncVersionStatus) {
        current = status
    }
}
