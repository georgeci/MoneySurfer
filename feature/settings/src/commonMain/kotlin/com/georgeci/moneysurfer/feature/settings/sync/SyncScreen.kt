package com.georgeci.moneysurfer.feature.settings.sync

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.model.SyncStep
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsChevron
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRow
import com.georgeci.moneysurfer.uikit.components.settings.SurferStatusHeroCard
import com.georgeci.moneysurfer.uikit.components.settings.SurferStatusHeroTone
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.settings.generated.resources.Res
import moneysurfer.feature.settings.generated.resources.settings_sync_action_force
import moneysurfer.feature.settings.generated.resources.settings_sync_done
import moneysurfer.feature.settings.generated.resources.settings_sync_failed
import moneysurfer.feature.settings.generated.resources.settings_sync_hero_idle_supporting
import moneysurfer.feature.settings.generated.resources.settings_sync_hero_idle_title
import moneysurfer.feature.settings.generated.resources.settings_sync_hero_progress_title
import moneysurfer.feature.settings.generated.resources.settings_sync_in_progress
import moneysurfer.feature.settings.generated.resources.settings_sync_in_progress_with_step
import moneysurfer.feature.settings.generated.resources.settings_sync_no_workspace
import moneysurfer.feature.settings.generated.resources.settings_sync_step_pull_accounts
import moneysurfer.feature.settings.generated.resources.settings_sync_step_pull_categories
import moneysurfer.feature.settings.generated.resources.settings_sync_step_pull_invites
import moneysurfer.feature.settings.generated.resources.settings_sync_step_pull_members
import moneysurfer.feature.settings.generated.resources.settings_sync_step_pull_transactions
import moneysurfer.feature.settings.generated.resources.settings_sync_step_pull_workspace
import moneysurfer.feature.settings.generated.resources.settings_sync_step_push_accounts
import moneysurfer.feature.settings.generated.resources.settings_sync_step_push_categories
import moneysurfer.feature.settings.generated.resources.settings_sync_step_push_invites
import moneysurfer.feature.settings.generated.resources.settings_sync_step_push_members
import moneysurfer.feature.settings.generated.resources.settings_sync_step_push_transactions
import moneysurfer.feature.settings.generated.resources.settings_sync_step_push_workspace
import moneysurfer.feature.settings.generated.resources.settings_sync_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SyncScreen(
    onNavigateBack: () -> Unit,
    viewModel: SyncViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            SyncEffect.NavigateBack -> onNavigateBack()
        }
    }

    SyncContent(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun SyncContent(
    state: SyncState,
    onEvent: (SyncEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.settings_sync_title),
                onBack = { onEvent(SyncEvent.OnBackClick) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.materialColors.surface,
                    titleContentColor = AppTheme.materialColors.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SyncStatusHero(state.syncStatus)
            }

            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                SurferSettingsRow(
                    icon = SurferIcons.Sync,
                    title = stringResource(Res.string.settings_sync_action_force),
                    iconBackground = AppTheme.materialColors.primaryContainer,
                    iconTint = AppTheme.materialColors.onPrimaryContainer,
                    onClick = { onEvent(SyncEvent.OnSyncClick) },
                    trailing = { SurferSettingsChevron() },
                )
            }
            Spacer(Modifier.height(padding.calculateBottomPadding() + 24.dp))
        }
    }
}

@Composable
private fun SyncStatusHero(status: SyncStatus) {
    when (status) {
        SyncStatus.Idle -> SurferStatusHeroCard(
            title = stringResource(Res.string.settings_sync_hero_idle_title),
            supporting = stringResource(Res.string.settings_sync_hero_idle_supporting),
            icon = SurferIcons.Sync,
            tone = SurferStatusHeroTone.Tertiary,
        )
        is SyncStatus.InProgress -> SurferStatusHeroCard(
            title = stringResource(Res.string.settings_sync_hero_progress_title),
            supporting = when (val step = status.step) {
                null -> stringResource(Res.string.settings_sync_in_progress)
                else -> stringResource(Res.string.settings_sync_in_progress_with_step, syncStepLabel(step))
            },
            icon = SurferIcons.Sync,
            tone = SurferStatusHeroTone.Primary,
        )
        SyncStatus.NoWorkspace -> SurferStatusHeroCard(
            title = stringResource(Res.string.settings_sync_no_workspace),
            icon = SurferIcons.Cloud,
            tone = SurferStatusHeroTone.Secondary,
        )
        is SyncStatus.Done -> SurferStatusHeroCard(
            title = stringResource(Res.string.settings_sync_hero_idle_title),
            supporting = stringResource(Res.string.settings_sync_done, status.pushed, status.pulled),
            icon = SurferIcons.Sync,
            tone = SurferStatusHeroTone.Tertiary,
        )
        is SyncStatus.Failed -> SurferStatusHeroCard(
            title = stringResource(Res.string.settings_sync_failed, status.message),
            icon = SurferIcons.Sync,
            tone = SurferStatusHeroTone.Secondary,
        )
    }
}

@Composable
@Suppress("CyclomaticComplexMethod")
private fun syncStepLabel(step: SyncStep): String = stringResource(
    when (step) {
        SyncStep.PUSH_WORKSPACE -> Res.string.settings_sync_step_push_workspace
        SyncStep.PUSH_MEMBERS -> Res.string.settings_sync_step_push_members
        SyncStep.PUSH_INVITES -> Res.string.settings_sync_step_push_invites
        SyncStep.PUSH_ACCOUNTS -> Res.string.settings_sync_step_push_accounts
        SyncStep.PUSH_CATEGORIES -> Res.string.settings_sync_step_push_categories
        SyncStep.PUSH_TRANSACTIONS -> Res.string.settings_sync_step_push_transactions
        SyncStep.PULL_WORKSPACE -> Res.string.settings_sync_step_pull_workspace
        SyncStep.PULL_MEMBERS -> Res.string.settings_sync_step_pull_members
        SyncStep.PULL_INVITES -> Res.string.settings_sync_step_pull_invites
        SyncStep.PULL_ACCOUNTS -> Res.string.settings_sync_step_pull_accounts
        SyncStep.PULL_CATEGORIES -> Res.string.settings_sync_step_pull_categories
        SyncStep.PULL_TRANSACTIONS -> Res.string.settings_sync_step_pull_transactions
    },
)
