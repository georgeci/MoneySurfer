package com.georgeci.moneysurfer.domain.fixtures

import com.georgeci.moneysurfer.domain.model.AppVersionStatus
import com.georgeci.moneysurfer.domain.repositories.AppVersionGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [AppVersionGate] for tests.
 *
 * Defaults to [AppVersionStatus.Supported]. Use [setStatus] to flip and verify
 * sync-blocking behaviour. [refreshCount] records every call to [refresh] —
 * used to assert the gate is re-checked on each sync cycle.
 */
class FakeAppVersionGate(
    initial: AppVersionStatus = AppVersionStatus.Supported,
) : AppVersionGate {

    private val _status = MutableStateFlow<AppVersionStatus?>(initial)
    override val status: StateFlow<AppVersionStatus?> = _status.asStateFlow()

    var refreshCount: Int = 0
        private set

    override suspend fun refresh(): AppVersionStatus {
        refreshCount++
        return _status.value ?: AppVersionStatus.Supported
    }

    override fun isSyncAllowed(): Boolean = when (_status.value) {
        null,
        AppVersionStatus.Supported,
        is AppVersionStatus.UpdateAvailable,
        -> true
        is AppVersionStatus.Unsupported -> false
    }

    fun setStatus(status: AppVersionStatus) {
        _status.value = status
    }
}
