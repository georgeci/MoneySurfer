package com.georgeci.moneysurfer.offline.noop

import com.georgeci.moneysurfer.domain.model.AppVersionStatus
import com.georgeci.moneysurfer.domain.repositories.AppVersionGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Offline build skips the remote version gate; status stays `Supported`
 * forever and `isSyncAllowed()` is always true (sync itself is a no-op).
 */
class NoOpAppVersionGate : AppVersionGate {
    private val _status = MutableStateFlow<AppVersionStatus?>(AppVersionStatus.Supported)

    override val status: StateFlow<AppVersionStatus?> = _status.asStateFlow()

    override suspend fun refresh(): AppVersionStatus = AppVersionStatus.Supported

    override fun isSyncAllowed(): Boolean = true
}
