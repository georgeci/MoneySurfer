package com.georgeci.moneysurfer.feature.settings.backup

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.backup.AppRestarter
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
import kotlinx.coroutines.launch
import moneysurfer.feature.settings.generated.resources.Res
import moneysurfer.feature.settings.generated.resources.settings_backup_back_up_now_supporting
import moneysurfer.feature.settings.generated.resources.settings_backup_back_up_now_title
import moneysurfer.feature.settings.generated.resources.settings_backup_cancel
import moneysurfer.feature.settings.generated.resources.settings_backup_delete_supporting
import moneysurfer.feature.settings.generated.resources.settings_backup_delete_title
import moneysurfer.feature.settings.generated.resources.settings_backup_download_supporting
import moneysurfer.feature.settings.generated.resources.settings_backup_download_title
import moneysurfer.feature.settings.generated.resources.settings_backup_encryption_supporting
import moneysurfer.feature.settings.generated.resources.settings_backup_encryption_title
import moneysurfer.feature.settings.generated.resources.settings_backup_export_success
import moneysurfer.feature.settings.generated.resources.settings_backup_frequency_pill
import moneysurfer.feature.settings.generated.resources.settings_backup_frequency_title
import moneysurfer.feature.settings.generated.resources.settings_backup_hero_supporting
import moneysurfer.feature.settings.generated.resources.settings_backup_hero_title
import moneysurfer.feature.settings.generated.resources.settings_backup_location_pill
import moneysurfer.feature.settings.generated.resources.settings_backup_location_title
import moneysurfer.feature.settings.generated.resources.settings_backup_notice_corrupted
import moneysurfer.feature.settings.generated.resources.settings_backup_notice_format_mismatch
import moneysurfer.feature.settings.generated.resources.settings_backup_notice_generic
import moneysurfer.feature.settings.generated.resources.settings_backup_notice_invalid_archive
import moneysurfer.feature.settings.generated.resources.settings_backup_notice_missing_file
import moneysurfer.feature.settings.generated.resources.settings_backup_notice_schema_mismatch
import moneysurfer.feature.settings.generated.resources.settings_backup_restore_confirm_body
import moneysurfer.feature.settings.generated.resources.settings_backup_restore_confirm_confirm
import moneysurfer.feature.settings.generated.resources.settings_backup_restore_confirm_title
import moneysurfer.feature.settings.generated.resources.settings_backup_restore_supporting
import moneysurfer.feature.settings.generated.resources.settings_backup_restore_title
import moneysurfer.feature.settings.generated.resources.settings_backup_schedule_footnote
import moneysurfer.feature.settings.generated.resources.settings_backup_section_manual
import moneysurfer.feature.settings.generated.resources.settings_backup_section_restore
import moneysurfer.feature.settings.generated.resources.settings_backup_section_schedule
import moneysurfer.feature.settings.generated.resources.settings_backup_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun BackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: BackupViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()
    val appRestarter: AppRestarter = koinInject()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    val launcher = rememberBackupPickerLauncher(
        onSavePicked = { sink -> viewModel.onEvent(BackupEvent.OnSaveSinkChosen(sink)) },
        onOpenPicked = { source -> viewModel.onEvent(BackupEvent.OnOpenSourceChosen(source)) },
    )

    val noticeMessages = NoticeStrings(
        exportSuccess = stringResource(Res.string.settings_backup_export_success),
        invalidArchive = stringResource(Res.string.settings_backup_notice_invalid_archive),
        corrupted = stringResource(Res.string.settings_backup_notice_corrupted),
        generic = stringResource(Res.string.settings_backup_notice_generic),
        missingFileFormat = stringResource(Res.string.settings_backup_notice_missing_file),
        formatMismatchFormat = stringResource(Res.string.settings_backup_notice_format_mismatch),
        schemaMismatchFormat = stringResource(Res.string.settings_backup_notice_schema_mismatch),
    )

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            BackupEffect.NavigateBack -> onNavigateBack()
            is BackupEffect.RequestSaveFile -> launcher.launchSave(effect.suggestedName)
            BackupEffect.RequestOpenFile -> launcher.launchOpen()
            BackupEffect.RestartApp -> appRestarter.restart()
            is BackupEffect.Notify -> {
                val text = noticeMessages.resolve(effect.notice) ?: return@HandleSideEffect
                snackbarScope.launch {
                    snackbarHostState.showSnackbar(message = text, duration = SnackbarDuration.Short)
                }
            }
            BackupEffect.OpenFrequencyPicker,
            BackupEffect.OpenLocationPicker,
            BackupEffect.OpenEncryptionScreen,
            BackupEffect.NotImplemented,
            -> Unit
        }
    }

    BackupContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
    )
}

private data class NoticeStrings(
    val exportSuccess: String,
    val invalidArchive: String,
    val corrupted: String,
    val generic: String,
    val missingFileFormat: String,
    val formatMismatchFormat: String,
    val schemaMismatchFormat: String,
) {
    /** Returns `null` for notices the screen intentionally swallows (e.g., user-cancelled picker). */
    fun resolve(notice: BackupNotice): String? = when (notice) {
        BackupNotice.ExportSuccess -> exportSuccess
        BackupNotice.Cancelled -> null
        BackupNotice.InvalidArchive -> invalidArchive
        BackupNotice.Corrupted -> corrupted
        BackupNotice.Generic -> generic
        is BackupNotice.MissingFile -> missingFileFormat.replace("%s", notice.name)
        is BackupNotice.FormatMismatch ->
            formatMismatchFormat
                .replace("%1\$d", notice.actual.toString())
                .replace("%2\$d", notice.expected.toString())
        is BackupNotice.SchemaMismatch ->
            schemaMismatchFormat
                .replace("%1\$d", notice.actual.toString())
                .replace("%2\$d", notice.expected.toString())
    }
}

@Composable
private fun BackupContent(
    state: BackupState,
    snackbarHostState: SnackbarHostState,
    onEvent: (BackupEvent) -> Unit,
) {
    if (state.showDeleteConfirmation) {
        DeleteBackupDialog(
            onConfirm = { onEvent(BackupEvent.OnDeleteConfirmed) },
            onDismiss = { onEvent(BackupEvent.OnDeleteDismissed) },
        )
    }
    if (state.showRestoreConfirmation) {
        RestoreBackupDialog(
            onConfirm = { onEvent(BackupEvent.OnRestoreConfirmed) },
            onDismiss = { onEvent(BackupEvent.OnRestoreDismissed) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
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

            if (state.phase != BackupPhase.Idle) {
                ProgressOverlay()
            }
        }
    }
}

/**
 * Modal scrim while export/import is in flight. The `clickable` with no-op
 * onClick + null indication consumes pointer events so taps don't reach the
 * settings rows underneath.
 */
@Composable
private fun ProgressOverlay() {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.materialColors.scrim.copy(alpha = SCRIM_ALPHA))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = AppTheme.materialColors.primary)
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
                Text(stringResource(Res.string.settings_backup_cancel))
            }
        },
    )
}

@Composable
private fun RestoreBackupDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_backup_restore_confirm_title)) },
        text = { Text(stringResource(Res.string.settings_backup_restore_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(Res.string.settings_backup_restore_confirm_confirm),
                    color = AppTheme.materialColors.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_backup_cancel))
            }
        },
    )
}

private const val SCRIM_ALPHA = 0.4f
