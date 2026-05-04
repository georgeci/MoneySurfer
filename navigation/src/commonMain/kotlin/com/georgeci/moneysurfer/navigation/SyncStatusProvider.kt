package com.georgeci.moneysurfer.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.sync.api.LastSyncOutcome
import com.georgeci.moneysurfer.sync.api.SyncState
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import com.georgeci.moneysurfer.uikit.components.base.LocalSurferSyncStatus
import com.georgeci.moneysurfer.uikit.components.base.SurferSyncStatus
import kotlinx.coroutines.flow.combine
import org.koin.compose.koinInject

/**
 * Single ambient bridge that maps [SyncCoordinator] live state into
 * [LocalSurferSyncStatus]. Wrap once near the root of the UI tree — every
 * `SurferToolbar` underneath then renders the indicator without any per-screen
 * plumbing.
 *
 * Lives next to [AppNavGraph] so `:uikit` stays free of sync-layer types.
 */
@Composable
fun SyncStatusProvider(
    coordinator: SyncCoordinator = koinInject(),
    content: @Composable () -> Unit,
) {
    val statusFlow = remember(coordinator) {
        combine(coordinator.state, coordinator.lastOutcome) { state, outcome ->
            toSurferStatus(state, outcome)
        }
    }
    val status by statusFlow.collectAsStateWithLifecycle(initialValue = SurferSyncStatus.Hidden)

    CompositionLocalProvider(LocalSurferSyncStatus provides status) {
        content()
    }
}

private fun toSurferStatus(state: SyncState, outcome: LastSyncOutcome): SurferSyncStatus =
    when (state) {
        is SyncState.Running, is SyncState.Queued -> SurferSyncStatus.Syncing
        SyncState.Idle -> when (outcome) {
            is LastSyncOutcome.Success -> SurferSyncStatus.Synced
            is LastSyncOutcome.Failed -> SurferSyncStatus.Failed
            LastSyncOutcome.None, is LastSyncOutcome.Cancelled -> SurferSyncStatus.Hidden
        }
    }
