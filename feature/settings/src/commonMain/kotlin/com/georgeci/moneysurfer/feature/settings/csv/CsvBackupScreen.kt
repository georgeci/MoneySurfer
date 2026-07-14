package com.georgeci.moneysurfer.feature.settings.csv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.csv.CsvImportReport
import com.georgeci.moneysurfer.domain.csv.CsvRowError
import com.georgeci.moneysurfer.domain.csv.CsvRowIssue
import com.georgeci.moneysurfer.feature.settings.backup.BackupPickerFormat
import com.georgeci.moneysurfer.feature.settings.backup.rememberBackupPickerLauncher
import com.georgeci.moneysurfer.feature.settings.components.SettingsSubScreenScaffold
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsChevron
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsGroup
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRow
import com.georgeci.moneysurfer.uikit.components.settings.SurferStatusHeroCard
import com.georgeci.moneysurfer.uikit.components.settings.SurferStatusHeroTone
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.settings.generated.resources.Res
import moneysurfer.feature.settings.generated.resources.settings_csv_export_success
import moneysurfer.feature.settings.generated.resources.settings_csv_export_supporting
import moneysurfer.feature.settings.generated.resources.settings_csv_hero_supporting
import moneysurfer.feature.settings.generated.resources.settings_csv_hero_title
import moneysurfer.feature.settings.generated.resources.settings_csv_import_result
import moneysurfer.feature.settings.generated.resources.settings_csv_import_supporting
import moneysurfer.feature.settings.generated.resources.settings_csv_import_title
import moneysurfer.feature.settings.generated.resources.settings_csv_issue_columns
import moneysurfer.feature.settings.generated.resources.settings_csv_issue_invalid_value
import moneysurfer.feature.settings.generated.resources.settings_csv_issue_unknown_account
import moneysurfer.feature.settings.generated.resources.settings_csv_issue_unknown_category
import moneysurfer.feature.settings.generated.resources.settings_csv_issue_unknown_workspace
import moneysurfer.feature.settings.generated.resources.settings_csv_last_import_summary
import moneysurfer.feature.settings.generated.resources.settings_csv_notice_generic
import moneysurfer.feature.settings.generated.resources.settings_csv_notice_invalid_file
import moneysurfer.feature.settings.generated.resources.settings_csv_row_error_format
import moneysurfer.feature.settings.generated.resources.settings_csv_section_export
import moneysurfer.feature.settings.generated.resources.settings_csv_section_import
import moneysurfer.feature.settings.generated.resources.settings_csv_section_last_import
import moneysurfer.feature.settings.generated.resources.settings_csv_title
import moneysurfer.feature.settings.generated.resources.settings_export_csv
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun CsvBackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: CsvBackupViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingNotice by remember { mutableStateOf<CsvBackupNotice?>(null) }

    val launcher = rememberBackupPickerLauncher(
        format = BackupPickerFormat.Csv,
        onSavePicked = { sink -> viewModel.onEvent(CsvBackupEvent.OnSaveSinkChosen(sink)) },
        onOpenPicked = { source -> viewModel.onEvent(CsvBackupEvent.OnOpenSourceChosen(source)) },
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
            CsvBackupEffect.NavigateBack -> onNavigateBack()
            is CsvBackupEffect.RequestSaveFile -> launcher.launchSave(effect.suggestedName)
            CsvBackupEffect.RequestOpenFile -> launcher.launchOpen()
            is CsvBackupEffect.Notify -> { pendingNotice = effect.notice }
        }
    }

    CsvBackupContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun noticeText(notice: CsvBackupNotice): String = when (notice) {
    is CsvBackupNotice.ExportSuccess ->
        stringResource(Res.string.settings_csv_export_success, notice.count)
    is CsvBackupNotice.ImportFinished ->
        stringResource(
            Res.string.settings_csv_import_result,
            notice.imported,
            notice.skipped,
            notice.failed,
        )
    CsvBackupNotice.InvalidFile -> stringResource(Res.string.settings_csv_notice_invalid_file)
    CsvBackupNotice.Generic -> stringResource(Res.string.settings_csv_notice_generic)
}

@Composable
private fun CsvBackupContent(
    state: CsvBackupState,
    snackbarHostState: SnackbarHostState,
    onEvent: (CsvBackupEvent) -> Unit,
) {
    SettingsSubScreenScaffold(
        title = stringResource(Res.string.settings_csv_title),
        snackbarHostState = snackbarHostState,
        onBack = { onEvent(CsvBackupEvent.OnBackClick) },
        showProgressOverlay = state.phase != CsvBackupPhase.Idle,
    ) { padding ->
        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SurferStatusHeroCard(
                title = stringResource(Res.string.settings_csv_hero_title),
                supporting = stringResource(Res.string.settings_csv_hero_supporting),
                icon = SurferIcons.Download,
                tone = SurferStatusHeroTone.Primary,
            )
        }

        SurferSettingsGroup(title = stringResource(Res.string.settings_csv_section_export)) {
            SurferSettingsRow(
                icon = SurferIcons.Download,
                title = stringResource(Res.string.settings_export_csv),
                supportingText = stringResource(Res.string.settings_csv_export_supporting),
                onClick = { onEvent(CsvBackupEvent.OnExportClick) },
                trailing = { SurferSettingsChevron() },
            )
        }

        SurferSettingsGroup(title = stringResource(Res.string.settings_csv_section_import)) {
            SurferSettingsRow(
                icon = SurferIcons.Sync,
                title = stringResource(Res.string.settings_csv_import_title),
                supportingText = stringResource(Res.string.settings_csv_import_supporting),
                onClick = { onEvent(CsvBackupEvent.OnImportClick) },
                trailing = { SurferSettingsChevron() },
            )
        }

        val report = state.lastImportReport
        if (report != null) {
            LastImportGroup(report)
        }

        Spacer(Modifier.height(padding.calculateBottomPadding() + 32.dp))
    }
}

@Composable
private fun LastImportGroup(report: CsvImportReport) {
    SurferSettingsGroup(title = stringResource(Res.string.settings_csv_section_last_import)) {
        SurferSettingsRow(
            icon = SurferIcons.Info,
            title = stringResource(
                Res.string.settings_csv_last_import_summary,
                report.imported,
                report.skippedDuplicates,
                report.errors.size,
            ),
            multiline = true,
        )
        for (error in report.errors) {
            SurferSettingsRow(
                icon = SurferIcons.Info,
                title = rowErrorText(error),
                danger = true,
                multiline = true,
            )
        }
    }
}

@Composable
private fun rowErrorText(error: CsvRowError): String =
    stringResource(Res.string.settings_csv_row_error_format, error.rowNumber, issueText(error.issue))

@Composable
private fun issueText(issue: CsvRowIssue): String = when (issue) {
    is CsvRowIssue.ColumnCountMismatch ->
        stringResource(Res.string.settings_csv_issue_columns, issue.expected, issue.actual)
    is CsvRowIssue.InvalidValue ->
        stringResource(Res.string.settings_csv_issue_invalid_value, issue.column)
    is CsvRowIssue.UnknownWorkspace ->
        stringResource(Res.string.settings_csv_issue_unknown_workspace, issue.workspaceId)
    is CsvRowIssue.UnknownAccount ->
        stringResource(Res.string.settings_csv_issue_unknown_account, issue.accountId)
    is CsvRowIssue.UnknownCategory ->
        stringResource(Res.string.settings_csv_issue_unknown_category, issue.categoryId)
}
