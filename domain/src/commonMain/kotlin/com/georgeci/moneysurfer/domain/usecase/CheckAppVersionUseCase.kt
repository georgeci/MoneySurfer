package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.model.AppVersionStatus
import com.georgeci.moneysurfer.domain.repositories.AppVersionGate
import org.koin.core.annotation.Single

/**
 * Refreshes the remote app-version gate. Invoke at app start, before
 * `BootstrapSessionUseCase` and `BackgroundSyncScheduler.schedulePeriodic` —
 * see block.md "Порядок запуска приложения".
 */
@Single
class CheckAppVersionUseCase(
    private val gate: AppVersionGate,
) {
    suspend operator fun invoke(): AppVersionStatus = gate.refresh()
}
