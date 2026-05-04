package com.georgeci.moneysurfer.data.sync

import com.georgeci.moneysurfer.domain.model.AppVersionStatus
import com.georgeci.moneysurfer.domain.repositories.AppVersionGate
import com.georgeci.moneysurfer.sync.version.SyncVersionGate
import com.georgeci.moneysurfer.sync.version.SyncVersionStatus
import org.koin.core.annotation.Single

/**
 * Adapter: maps [AppVersionGate] (domain) → [SyncVersionGate] (sync engine).
 * Lives in `:sync-impl` so the engine module stays domain-free.
 */
@Single(binds = [SyncVersionGate::class])
class SyncVersionGateImpl(
    private val appVersionGate: AppVersionGate,
) : SyncVersionGate {

    override suspend fun refresh(): SyncVersionStatus = when (val s = appVersionGate.refresh()) {
        AppVersionStatus.Supported,
        is AppVersionStatus.UpdateAvailable,
        -> SyncVersionStatus.Allowed
        is AppVersionStatus.Unsupported -> SyncVersionStatus.Blocked(s.message)
    }

    override fun isSyncAllowed(): Boolean = appVersionGate.isSyncAllowed()
}
