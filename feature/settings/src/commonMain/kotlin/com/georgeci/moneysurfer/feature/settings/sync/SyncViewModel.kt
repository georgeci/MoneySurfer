package com.georgeci.moneysurfer.feature.settings.sync

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.SyncFeatureFlag
import com.georgeci.moneysurfer.domain.model.SyncStep
import com.georgeci.moneysurfer.sync.api.SyncError
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class SyncViewModel(
    private val syncCoordinator: SyncCoordinator,
    private val syncFeatureFlag: SyncFeatureFlag,
) : MviViewModel<SyncState, SyncEvent, SyncEffect>(
    initialState = SyncState(),
) {

    private val log = Logger.withTag(TAG)

    override fun onEvent(event: SyncEvent) {
        when (event) {
            SyncEvent.OnBackClick -> postSideEffect(SyncEffect.NavigateBack)
            SyncEvent.OnSyncClick -> sync()
        }
    }

    private fun sync() {
        // Defensive: SettingsScreen no longer surfaces a way here when the flag is off,
        // but the navigation entry stays registered — bail out so a deep link or stale
        // back stack can't trigger an actual sync against Firestore.
        if (!syncFeatureFlag.enabled) {
            log.i { "[sync] feature flag off — ignoring manual sync request" }
            updateState { copy(syncStatus = SyncStatus.Idle) }
            return
        }
        launch {
            log.i { "[sync] requesting MANUAL sync via coordinator" }
            updateState { copy(syncStatus = SyncStatus.InProgress(step = null)) }

            val handle = syncCoordinator.requestSync(reason = SyncReason.MANUAL)
            val result = handle.result.await()

            result.fold(
                ifLeft = { error: SyncError ->
                    val nextStatus = when (error) {
                        SyncError.Cancelled -> SyncStatus.Idle
                        else -> SyncStatus.Failed(error.toUiMessage())
                    }
                    log.w(throwable = (error as? SyncError.Unknown)?.cause) { "[sync] failed error" }
                    updateState { copy(syncStatus = nextStatus) }
                },
                ifRight = { summary ->
                    log.i {
                        "[sync] done uploaded=${summary.uploadedCount} " +
                            "downloaded=${summary.downloadedCount}"
                    }
                    updateState {
                        copy(
                            syncStatus = SyncStatus.Done(
                                pushed = summary.uploadedCount,
                                pulled = summary.downloadedCount,
                            ),
                        )
                    }
                },
            )
        }
    }

    private fun SyncError.toUiMessage(): String = when (this) {
        SyncError.Cancelled -> "Sync cancelled"
        SyncError.NetworkUnavailable -> "No internet connection"
        SyncError.AuthRequired -> "Please sign in again"
        SyncError.PermissionDenied -> "You don't have access to this workspace"
        is SyncError.UnsupportedAppVersion -> message
        is SyncError.StorageError -> "Local storage error"
        is SyncError.Unknown -> cause.message ?: "Sync failed"
    }

    private companion object {
        const val TAG = "SyncVM"
    }
}

data class SyncState(
    val syncStatus: SyncStatus = SyncStatus.Idle,
)

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data class InProgress(val step: SyncStep?) : SyncStatus
    data object NoWorkspace : SyncStatus
    data class Done(val pushed: Int, val pulled: Int) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

sealed interface SyncEvent {
    data object OnBackClick : SyncEvent
    data object OnSyncClick : SyncEvent
}

sealed interface SyncEffect {
    data object NavigateBack : SyncEffect
}
