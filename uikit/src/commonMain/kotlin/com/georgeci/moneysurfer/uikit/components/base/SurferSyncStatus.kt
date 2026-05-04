package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Decoupled UI status fed into [SurferToolbar] / [SurferDashboardToolbar] via
 * [LocalSurferSyncStatus]. The uikit module knows nothing about the sync layer —
 * a higher-level provider (in `:shared`) maps `SyncCoordinator` state to this enum
 * and pushes the value through composition once for the whole app, so screens never
 * have to forward sync state into the toolbar.
 */
enum class SurferSyncStatus {
    Hidden,
    Syncing,
    Synced,
    Failed,
}

val LocalSurferSyncStatus = compositionLocalOf { SurferSyncStatus.Hidden }

@Composable
internal fun SurferSyncStatusBadge(
    modifier: Modifier = Modifier,
    status: SurferSyncStatus = LocalSurferSyncStatus.current,
) {
    if (status == SurferSyncStatus.Hidden) return

    val color = when (status) {
        SurferSyncStatus.Failed -> AppTheme.materialColors.error
        else -> AppTheme.materialColors.primary
    }

    Box(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .size(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            SurferSyncStatus.Syncing -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = color,
                strokeWidth = 2.dp,
            )
            SurferSyncStatus.Synced,
            SurferSyncStatus.Failed,
            -> Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            SurferSyncStatus.Hidden -> Unit
        }
    }
}
