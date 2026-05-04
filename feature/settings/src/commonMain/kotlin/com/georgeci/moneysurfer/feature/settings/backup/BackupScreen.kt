package com.georgeci.moneysurfer.feature.settings.backup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsChevron
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsGroup
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRow
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsValuePill
import com.georgeci.moneysurfer.uikit.components.settings.SurferStatusHeroCard
import com.georgeci.moneysurfer.uikit.components.settings.SurferStatusHeroTone
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.settings.generated.resources.Res
import moneysurfer.feature.settings.generated.resources.settings_backup_back_up_now_supporting
import moneysurfer.feature.settings.generated.resources.settings_backup_back_up_now_title
import moneysurfer.feature.settings.generated.resources.settings_backup_delete_supporting
import moneysurfer.feature.settings.generated.resources.settings_backup_delete_title
import moneysurfer.feature.settings.generated.resources.settings_backup_download_supporting
import moneysurfer.feature.settings.generated.resources.settings_backup_download_title
import moneysurfer.feature.settings.generated.resources.settings_backup_encryption_supporting
import moneysurfer.feature.settings.generated.resources.settings_backup_encryption_title
import moneysurfer.feature.settings.generated.resources.settings_backup_frequency_pill
import moneysurfer.feature.settings.generated.resources.settings_backup_frequency_title
import moneysurfer.feature.settings.generated.resources.settings_backup_hero_supporting
import moneysurfer.feature.settings.generated.resources.settings_backup_hero_title
import moneysurfer.feature.settings.generated.resources.settings_backup_location_pill
import moneysurfer.feature.settings.generated.resources.settings_backup_location_title
import moneysurfer.feature.settings.generated.resources.settings_backup_restore_supporting
import moneysurfer.feature.settings.generated.resources.settings_backup_restore_title
import moneysurfer.feature.settings.generated.resources.settings_backup_schedule_footnote
import moneysurfer.feature.settings.generated.resources.settings_backup_section_manual
import moneysurfer.feature.settings.generated.resources.settings_backup_section_restore
import moneysurfer.feature.settings.generated.resources.settings_backup_section_schedule
import moneysurfer.feature.settings.generated.resources.settings_backup_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            BackupEffect.NavigateBack -> onNavigateBack()
            BackupEffect.OpenFrequencyPicker,
            BackupEffect.OpenLocationPicker,
            BackupEffect.OpenEncryptionScreen,
            BackupEffect.NavigateToRestore,
            BackupEffect.NotImplemented,
            -> Unit
        }
    }

    BackupContent(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun BackupContent(
    state: BackupState,
    onEvent: (BackupEvent) -> Unit,
) {
    if (state.showDeleteConfirmation) {
        DeleteBackupDialog(
            onConfirm = { onEvent(BackupEvent.OnDeleteConfirmed) },
            onDismiss = { onEvent(BackupEvent.OnDeleteDismissed) },
        )
    }

    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.settings_backup_title),
                onBack = { onEvent(BackupEvent.OnBackClick) },
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
                SurferStatusHeroCard(
                    title = stringResource(Res.string.settings_backup_hero_title),
                    supporting = stringResource(Res.string.settings_backup_hero_supporting),
                    icon = SurferIcons.Cloud,
                    tone = SurferStatusHeroTone.Primary,
                )
            }

            SurferSettingsGroup(
                title = stringResource(Res.string.settings_backup_section_schedule),
                footnote = stringResource(Res.string.settings_backup_schedule_footnote),
            ) {
                SurferSettingsRow(
                    icon = SurferIcons.Calendar,
                    title = stringResource(Res.string.settings_backup_frequency_title),
                    onClick = { onEvent(BackupEvent.OnFrequencyClick) },
                    trailing = {
                        SurferSettingsValuePill(stringResource(Res.string.settings_backup_frequency_pill))
                    },
                )
                SurferSettingsRow(
                    icon = SurferIcons.Shield,
                    title = stringResource(Res.string.settings_backup_encryption_title),
                    supportingText = stringResource(Res.string.settings_backup_encryption_supporting),
                    onClick = { onEvent(BackupEvent.OnEncryptionClick) },
                    trailing = { SurferSettingsValuePill("On") },
                )
                SurferSettingsRow(
                    icon = SurferIcons.Cloud,
                    title = stringResource(Res.string.settings_backup_location_title),
                    onClick = { onEvent(BackupEvent.OnLocationClick) },
                    trailing = {
                        SurferSettingsValuePill(stringResource(Res.string.settings_backup_location_pill))
                    },
                )
            }

            SurferSettingsGroup(title = stringResource(Res.string.settings_backup_section_manual)) {
                SurferSettingsRow(
                    icon = SurferIcons.Cloud,
                    title = stringResource(Res.string.settings_backup_back_up_now_title),
                    supportingText = stringResource(Res.string.settings_backup_back_up_now_supporting),
                    onClick = { onEvent(BackupEvent.OnBackUpNowClick) },
                    trailing = { SurferSettingsChevron() },
                )
                SurferSettingsRow(
                    icon = SurferIcons.Download,
                    title = stringResource(Res.string.settings_backup_download_title),
                    supportingText = stringResource(Res.string.settings_backup_download_supporting),
                    onClick = { onEvent(BackupEvent.OnDownloadClick) },
                    trailing = { SurferSettingsChevron() },
                )
            }

            SurferSettingsGroup(title = stringResource(Res.string.settings_backup_section_restore)) {
                SurferSettingsRow(
                    icon = SurferIcons.Sync,
                    title = stringResource(Res.string.settings_backup_restore_title),
                    supportingText = stringResource(Res.string.settings_backup_restore_supporting),
                    onClick = { onEvent(BackupEvent.OnRestoreClick) },
                    trailing = { SurferSettingsChevron() },
                )
            }

            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                SurferSettingsRow(
                    icon = SurferIcons.Delete,
                    title = stringResource(Res.string.settings_backup_delete_title),
                    supportingText = stringResource(Res.string.settings_backup_delete_supporting),
                    danger = true,
                    multiline = true,
                    onClick = { onEvent(BackupEvent.OnDeleteClick) },
                )
            }
            Spacer(Modifier.height(padding.calculateBottomPadding() + 32.dp))
        }
    }
}

@Composable
private fun DeleteBackupDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_backup_delete_title)) },
        text = { Text(stringResource(Res.string.settings_backup_delete_supporting)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(Res.string.settings_backup_delete_title),
                    color = AppTheme.materialColors.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
