package com.georgeci.moneysurfer.feature.settings.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.backup.AppRestarter
import com.georgeci.moneysurfer.feature.settings.components.SettingsSubScreenScaffold
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsChevron
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsGroup
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRow
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsValuePill
import com.georgeci.moneysurfer.uikit.components.settings.SurferStatusHeroCard
import com.georgeci.moneysurfer.uikit.components.settings.SurferStatusHeroTone
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
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
import moneysurfer.feature.settings.generated.resources.settings_backup_export_confirm
import moneysurfer.feature.settings.generated.resources.settings_backup_export_passphrase_confirm_label
import moneysurfer.feature.settings.generated.resources.settings_backup_export_passphrase_label
import moneysurfer.feature.settings.generated.resources.settings_backup_export_passphrase_mismatch
import moneysurfer.feature.settings.generated.resources.settings_backup_export_success
import moneysurfer.feature.settings.generated.resources.settings_backup_export_warning
import moneysurfer.feature.settings.generated.resources.settings_backup_frequency_pill
import moneysurfer.feature.settings.generated.resources.settings_backup_frequency_title
import moneysurfer.feature.settings.generated.resources.settings_backup_hero_supporting
import moneysurfer.feature.settings.generated.resources.settings_backup_hero_title
import moneysurfer.feature.settings.generated.resources.settings_backup_import_passphrase_body
import moneysurfer.feature.settings.generated.resources.settings_backup_import_passphrase_confirm
import moneysurfer.feature.settings.generated.resources.settings_backup_import_passphrase_label
import moneysurfer.feature.settings.generated.resources.settings_backup_import_passphrase_title
import moneysurfer.feature.settings.generated.resources.settings_backup_location_pill
import moneysurfer.feature.settings.generated.resources.settings_backup_location_title
import moneysurfer.feature.settings.generated.resources.settings_backup_notice_corrupted
import moneysurfer.feature.settings.generated.resources.settings_backup_notice_format_mismatch
import moneysurfer.feature.settings.generated.resources.settings_backup_notice_generic
import moneysurfer.feature.settings.generated.resources.settings_backup_notice_invalid_archive
import moneysurfer.feature.settings.generated.resources.settings_backup_notice_missing_file
import moneysurfer.feature.settings.generated.resources.settings_backup_notice_passphrase_required
import moneysurfer.feature.settings.generated.resources.settings_backup_notice_schema_mismatch
import moneysurfer.feature.settings.generated.resources.settings_backup_notice_wrong_passphrase
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
    var pendingNotice by remember { mutableStateOf<BackupNotice?>(null) }

    val launcher = rememberBackupPickerLauncher(
        format = BackupPickerFormat.Zip,
        onSavePicked = { sink -> viewModel.onEvent(BackupEvent.OnSaveSinkChosen(sink)) },
        onOpenPicked = { source -> viewModel.onEvent(BackupEvent.OnOpenSourceChosen(source)) },
    )

    // Resolve through Compose's standard format-args path so positional
    // placeholders survive translator reordering. We snapshot at composition;
    // the LaunchedEffect below feeds the snackbar from the resolved value.
    val pendingNoticeText: String? = pendingNotice?.let { noticeText(it) }
    LaunchedEffect(pendingNoticeText) {
        val text = pendingNoticeText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = text, duration = SnackbarDuration.Short)
        pendingNotice = null
    }

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            BackupEffect.NavigateBack -> onNavigateBack()
            is BackupEffect.RequestSaveFile -> launcher.launchSave(effect.suggestedName)
            BackupEffect.RequestOpenFile -> launcher.launchOpen()
            BackupEffect.RestartApp -> appRestarter.restart()
            is BackupEffect.Notify -> { pendingNotice = effect.notice }
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

@Composable
private fun noticeText(notice: BackupNotice): String? = when (notice) {
    BackupNotice.ExportSuccess -> stringResource(Res.string.settings_backup_export_success)
    BackupNotice.Cancelled -> null
    BackupNotice.InvalidArchive -> stringResource(Res.string.settings_backup_notice_invalid_archive)
    BackupNotice.Corrupted -> stringResource(Res.string.settings_backup_notice_corrupted)
    BackupNotice.PassphraseRequired ->
        stringResource(Res.string.settings_backup_notice_passphrase_required)
    BackupNotice.WrongPassphrase ->
        stringResource(Res.string.settings_backup_notice_wrong_passphrase)
    BackupNotice.Generic -> stringResource(Res.string.settings_backup_notice_generic)
    is BackupNotice.MissingFile ->
        stringResource(Res.string.settings_backup_notice_missing_file, notice.name)
    is BackupNotice.FormatMismatch ->
        stringResource(Res.string.settings_backup_notice_format_mismatch, notice.actual, notice.expected)
    is BackupNotice.SchemaMismatch ->
        stringResource(Res.string.settings_backup_notice_schema_mismatch, notice.actual, notice.expected)
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
    if (state.showExportOptions) {
        ExportBackupDialog(
            onConfirm = { passphrase -> onEvent(BackupEvent.OnExportOptionsConfirmed(passphrase)) },
            onDismiss = { onEvent(BackupEvent.OnExportOptionsDismissed) },
        )
    }
    if (state.showImportPassphrase) {
        ImportPassphraseDialog(
            onConfirm = { passphrase -> onEvent(BackupEvent.OnImportPassphraseSubmitted(passphrase)) },
            onDismiss = { onEvent(BackupEvent.OnImportPassphraseDismissed) },
        )
    }

    SettingsSubScreenScaffold(
        title = stringResource(Res.string.settings_backup_title),
        snackbarHostState = snackbarHostState,
        onBack = { onEvent(BackupEvent.OnBackClick) },
        showProgressOverlay = state.phase != BackupPhase.Idle,
    ) { padding ->
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

/**
 * Pre-export options: warns that a plain export is readable by anyone holding
 * the file, and offers an optional passphrase (with confirmation to guard
 * against a typo locking the user out of their own backup).
 */
@Composable
private fun ExportBackupDialog(
    onConfirm: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val mismatch = passphrase.isNotEmpty() && confirmation != passphrase

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_backup_download_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.settings_backup_export_warning))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(Res.string.settings_backup_export_passphrase_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                if (passphrase.isNotEmpty()) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = {
                            Text(
                                stringResource(
                                    Res.string.settings_backup_export_passphrase_confirm_label,
                                ),
                            )
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = mismatch,
                        supportingText = {
                            if (mismatch) {
                                Text(
                                    stringResource(
                                        Res.string.settings_backup_export_passphrase_mismatch,
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !mismatch,
                onClick = { onConfirm(passphrase.takeIf { it.isNotEmpty() }) },
            ) {
                Text(stringResource(Res.string.settings_backup_export_confirm))
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
private fun ImportPassphraseDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_backup_import_passphrase_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(Res.string.settings_backup_import_passphrase_body))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(Res.string.settings_backup_import_passphrase_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = passphrase.isNotEmpty(),
                onClick = { onConfirm(passphrase) },
            ) {
                Text(stringResource(Res.string.settings_backup_import_passphrase_confirm))
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
