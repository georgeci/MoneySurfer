package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.AppVersionStatus
import kotlinx.coroutines.flow.StateFlow

/**
 * Cached snapshot of the remote app-version gate. Refreshed on demand by
 * [refresh] (typically called from `AppStartupCoordinator`); subsequent
 * read paths (sync coordinator, write-usecases) consult [status] without
 * blocking on the network.
 *
 * Treats `null` from [com.georgeci.moneysurfer.domain.repositories.AppConfigRepository]
 * (missing doc / fetch failure) as [AppVersionStatus.Supported] — fail-open
 * so the gate doesn't lock users out on a transient Firestore hiccup.
 *
 * See block.md.
 */
interface AppVersionGate {
    /** `null` until the first successful [refresh]. */
    val status: StateFlow<AppVersionStatus?>

    suspend fun refresh(): AppVersionStatus

    /** Convenience read for write-paths — assumes [refresh] has run. */
    fun isSyncAllowed(): Boolean
}
