package com.georgeci.moneysurfer.feature.settings.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.sync.api.LastSyncOutcome
import com.georgeci.moneysurfer.sync.api.SyncError
import com.georgeci.moneysurfer.sync.api.SyncStep
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import com.georgeci.moneysurfer.sync.repository.SyncMeta
import com.georgeci.moneysurfer.uikit.components.SurferConfirmDialog
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsChevron
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsGroup
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRow
import com.georgeci.moneysurfer.uikit.components.settings.SurferStatusHeroCard
import com.georgeci.moneysurfer.uikit.components.settings.SurferStatusHeroTone
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import moneysurfer.feature.settings.generated.resources.Res
import moneysurfer.feature.settings.generated.resources.settings_sync_action_force
import moneysurfer.feature.settings.generated.resources.settings_sync_action_force_supporting
import moneysurfer.feature.settings.generated.resources.settings_sync_action_reset_cursors
import moneysurfer.feature.settings.generated.resources.settings_sync_action_reset_cursors_supporting
import moneysurfer.feature.settings.generated.resources.settings_sync_actions_disabled_footnote
import moneysurfer.feature.settings.generated.resources.settings_sync_cursor_attempt
import moneysurfer.feature.settings.generated.resources.settings_sync_cursor_pulled
import moneysurfer.feature.settings.generated.resources.settings_sync_cursor_success
import moneysurfer.feature.settings.generated.resources.settings_sync_cursors_empty_supporting
import moneysurfer.feature.settings.generated.resources.settings_sync_cursors_empty_title
import moneysurfer.feature.settings.generated.resources.settings_sync_cursors_footnote
import moneysurfer.feature.settings.generated.resources.settings_sync_done
import moneysurfer.feature.settings.generated.resources.settings_sync_error_auth
import moneysurfer.feature.settings.generated.resources.settings_sync_error_cancelled
import moneysurfer.feature.settings.generated.resources.settings_sync_error_network
import moneysurfer.feature.settings.generated.resources.settings_sync_error_permission
import moneysurfer.feature.settings.generated.resources.settings_sync_error_storage
import moneysurfer.feature.settings.generated.resources.settings_sync_error_unknown
import moneysurfer.feature.settings.generated.resources.settings_sync_failed
import moneysurfer.feature.settings.generated.resources.settings_sync_hero_idle_title
import moneysurfer.feature.settings.generated.resources.settings_sync_hero_progress_title
import moneysurfer.feature.settings.generated.resources.settings_sync_in_progress_with_step
import moneysurfer.feature.settings.generated.resources.settings_sync_last_at
import moneysurfer.feature.settings.generated.resources.settings_sync_last_cancelled_title
import moneysurfer.feature.settings.generated.resources.settings_sync_last_counts
import moneysurfer.feature.settings.generated.resources.settings_sync_last_failed_title
import moneysurfer.feature.settings.generated.resources.settings_sync_last_none_supporting
import moneysurfer.feature.settings.generated.resources.settings_sync_last_none_title
import moneysurfer.feature.settings.generated.resources.settings_sync_last_success_title
import moneysurfer.feature.settings.generated.resources.settings_sync_live_idle_supporting
import moneysurfer.feature.settings.generated.resources.settings_sync_live_idle_title
import moneysurfer.feature.settings.generated.resources.settings_sync_live_queued_supporting
import moneysurfer.feature.settings.generated.resources.settings_sync_live_queued_title
import moneysurfer.feature.settings.generated.resources.settings_sync_live_reasons
import moneysurfer.feature.settings.generated.resources.settings_sync_live_request
import moneysurfer.feature.settings.generated.resources.settings_sync_live_running_title
import moneysurfer.feature.settings.generated.resources.settings_sync_live_scope
import moneysurfer.feature.settings.generated.resources.settings_sync_live_step
import moneysurfer.feature.settings.generated.resources.settings_sync_no_workspace
import moneysurfer.feature.settings.generated.resources.settings_sync_outbox_attempts
import moneysurfer.feature.settings.generated.resources.settings_sync_outbox_empty_supporting
import moneysurfer.feature.settings.generated.resources.settings_sync_outbox_empty_title
import moneysurfer.feature.settings.generated.resources.settings_sync_outbox_error
import moneysurfer.feature.settings.generated.resources.settings_sync_outbox_footnote
import moneysurfer.feature.settings.generated.resources.settings_sync_outbox_row_title
import moneysurfer.feature.settings.generated.resources.settings_sync_outbox_scope
import moneysurfer.feature.settings.generated.resources.settings_sync_outbox_scope_root
import moneysurfer.feature.settings.generated.resources.settings_sync_outbox_truncated
import moneysurfer.feature.settings.generated.resources.settings_sync_outbox_unknown_supporting
import moneysurfer.feature.settings.generated.resources.settings_sync_outbox_unknown_title
import moneysurfer.feature.settings.generated.resources.settings_sync_reset_cursors_confirm_cancel
import moneysurfer.feature.settings.generated.resources.settings_sync_reset_cursors_confirm_confirm
import moneysurfer.feature.settings.generated.resources.settings_sync_reset_cursors_confirm_detail
import moneysurfer.feature.settings.generated.resources.settings_sync_reset_cursors_confirm_message
import moneysurfer.feature.settings.generated.resources.settings_sync_reset_cursors_confirm_title
import moneysurfer.feature.settings.generated.resources.settings_sync_section_actions
import moneysurfer.feature.settings.generated.resources.settings_sync_section_cursors
import moneysurfer.feature.settings.generated.resources.settings_sync_section_last
import moneysurfer.feature.settings.generated.resources.settings_sync_section_live
import moneysurfer.feature.settings.generated.resources.settings_sync_section_outbox
import moneysurfer.feature.settings.generated.resources.settings_sync_section_outbox_unknown
import moneysurfer.feature.settings.generated.resources.settings_sync_step_cancelled
import moneysurfer.feature.settings.generated.resources.settings_sync_step_completed
import moneysurfer.feature.settings.generated.resources.settings_sync_step_failed
import moneysurfer.feature.settings.generated.resources.settings_sync_step_pulling
import moneysurfer.feature.settings.generated.resources.settings_sync_step_pulling_collection
import moneysurfer.feature.settings.generated.resources.settings_sync_step_recalculating
import moneysurfer.feature.settings.generated.resources.settings_sync_step_started
import moneysurfer.feature.settings.generated.resources.settings_sync_step_uploading
import moneysurfer.feature.settings.generated.resources.settings_sync_step_uploading_entity
import moneysurfer.feature.settings.generated.resources.settings_sync_step_waiting_network
import moneysurfer.feature.settings.generated.resources.settings_sync_title
import moneysurfer.feature.settings.generated.resources.settings_sync_value_never
import moneysurfer.feature.settings.generated.resources.settings_sync_workspace_scope
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Instant
import com.georgeci.moneysurfer.sync.api.SyncState as LiveSyncState

/** Stable selectors for the sync panel — see docs/testing/testing-strategy.md. */
object SyncTestTags {
    const val Hero = "sync:hero"
    const val Live = "sync:live"
    const val LastOutcome = "sync:lastOutcome"
    const val Outbox = "sync:outbox"
    const val OutboxEmpty = "sync:outbox:empty"
    const val OutboxUnknown = "sync:outbox:unknown"
    const val Cursors = "sync:cursors"
    const val CursorsEmpty = "sync:cursors:empty"
    const val ForceSyncRow = "sync:action:force"
    const val ResetCursorsRow = "sync:action:resetCursors"
    const val ResetCursorsDialog = "sync:dialog:resetCursors"

    fun outboxRow(id: String): String = "sync:outbox:row:$id"
    fun cursorRow(collection: String): String = "sync:cursors:row:$collection"
}

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

/**
 * Stateless content, public so `:composeApp`'s desktop UI tests can mount it — as with the other
 * screens.
 */
@Composable
fun SyncContent(
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

            LiveStateSection(state)
            LastOutcomeSection(state.lastOutcome)
            OutboxSection(state)
            CursorsSection(state.cursors)
            ActionsSection(state = state, onEvent = onEvent)

            Spacer(Modifier.height(padding.calculateBottomPadding() + 24.dp))
        }
    }

    if (state.showResetCursorsConfirm) {
        SurferConfirmDialog(
            title = stringResource(Res.string.settings_sync_reset_cursors_confirm_title),
            message = stringResource(Res.string.settings_sync_reset_cursors_confirm_message),
            detail = stringResource(Res.string.settings_sync_reset_cursors_confirm_detail),
            confirmLabel = stringResource(Res.string.settings_sync_reset_cursors_confirm_confirm),
            cancelLabel = stringResource(Res.string.settings_sync_reset_cursors_confirm_cancel),
            icon = SurferIcons.Restore,
            destructive = true,
            onConfirm = { onEvent(SyncEvent.OnResetCursorsConfirmed) },
            onDismiss = { onEvent(SyncEvent.OnResetCursorsDismissed) },
            modifier = Modifier.testTag(SyncTestTags.ResetCursorsDialog),
        )
    }
}

@Composable
private fun SyncStatusHero(status: SyncStatus) {
    when (status) {
        SyncStatus.Idle -> SurferStatusHeroCard(
            title = stringResource(Res.string.settings_sync_hero_idle_title),
            supporting = stringResource(Res.string.settings_sync_live_idle_supporting),
            icon = SurferIcons.Sync,
            tone = SurferStatusHeroTone.Tertiary,
            modifier = Modifier.testTag(SyncTestTags.Hero),
        )
        SyncStatus.NoWorkspace -> SurferStatusHeroCard(
            title = stringResource(Res.string.settings_sync_no_workspace),
            icon = SurferIcons.Cloud,
            tone = SurferStatusHeroTone.Secondary,
            modifier = Modifier.testTag(SyncTestTags.Hero),
        )
        is SyncStatus.Queued -> SurferStatusHeroCard(
            title = stringResource(Res.string.settings_sync_hero_progress_title),
            supporting = stringResource(Res.string.settings_sync_live_queued_supporting, status.count),
            icon = SurferIcons.Sync,
            tone = SurferStatusHeroTone.Primary,
            modifier = Modifier.testTag(SyncTestTags.Hero),
        )
        is SyncStatus.Running -> SurferStatusHeroCard(
            title = stringResource(Res.string.settings_sync_hero_progress_title),
            supporting = stringResource(Res.string.settings_sync_in_progress_with_step, syncStepLabel(status.step)),
            icon = SurferIcons.Sync,
            tone = SurferStatusHeroTone.Primary,
            modifier = Modifier.testTag(SyncTestTags.Hero),
        )
        is SyncStatus.Done -> SurferStatusHeroCard(
            title = stringResource(Res.string.settings_sync_hero_idle_title),
            supporting = stringResource(
                Res.string.settings_sync_done,
                status.summary.uploadedCount,
                status.summary.downloadedCount,
            ),
            icon = SurferIcons.Sync,
            tone = SurferStatusHeroTone.Tertiary,
            modifier = Modifier.testTag(SyncTestTags.Hero),
        )
        is SyncStatus.Failed -> SurferStatusHeroCard(
            title = stringResource(Res.string.settings_sync_failed, syncErrorMessage(status.error)),
            icon = SurferIcons.Sync,
            tone = SurferStatusHeroTone.Secondary,
            modifier = Modifier.testTag(SyncTestTags.Hero),
        )
    }
}

/**
 * The coordinator's own `state` — live activity only. `Running` spells out the merged request
 * (id, reasons, scope) because a sync the user did not ask for is the usual explanation for a
 * "Force sync" that appears to do nothing: it was merged into the one already in flight.
 */
@Composable
private fun LiveStateSection(state: SyncState) {
    val title: String
    val details: List<String>
    when (val live = state.live) {
        is LiveSyncState.Queued -> {
            title = stringResource(Res.string.settings_sync_live_queued_title)
            details = listOf(stringResource(Res.string.settings_sync_live_queued_supporting, live.count))
        }
        is LiveSyncState.Running -> {
            title = stringResource(Res.string.settings_sync_live_running_title)
            details = listOf(
                stringResource(Res.string.settings_sync_live_request, live.requestId.value),
                stringResource(Res.string.settings_sync_live_reasons, live.reasons.joinToString { it.name }),
                stringResource(Res.string.settings_sync_live_scope, live.scope.name),
                stringResource(Res.string.settings_sync_live_step, syncStepLabel(live.currentStep)),
            )
        }
        LiveSyncState.Idle -> {
            title = stringResource(Res.string.settings_sync_live_idle_title)
            details = listOf(stringResource(Res.string.settings_sync_live_idle_supporting))
        }
    }
    val workspaceId = state.workspaceId?.value

    SurferSettingsGroup(title = stringResource(Res.string.settings_sync_section_live)) {
        SurferSettingsRow(
            icon = SurferIcons.Sync,
            title = title,
            supporting = {
                DetailLines(
                    if (workspaceId == null) {
                        details
                    } else {
                        details + stringResource(Res.string.settings_sync_workspace_scope, workspaceId)
                    },
                )
            },
            multiline = true,
            modifier = Modifier.testTag(SyncTestTags.Live),
        )
    }
}

/**
 * The persisted terminal outcome, which the live state deliberately does not carry (FAQ №10).
 * Conflicts and recalculations are shown next to the transfer counts because a pull that
 * "downloaded 40" and resolved 40 conflicts kept none of them.
 */
@Composable
private fun LastOutcomeSection(outcome: LastSyncOutcome) {
    val title: String
    val details: List<String>
    when (outcome) {
        LastSyncOutcome.None -> {
            title = stringResource(Res.string.settings_sync_last_none_title)
            details = listOf(stringResource(Res.string.settings_sync_last_none_supporting))
        }
        is LastSyncOutcome.Success -> {
            title = stringResource(Res.string.settings_sync_last_success_title)
            details = listOf(
                stringResource(
                    Res.string.settings_sync_last_counts,
                    outcome.summary.uploadedCount,
                    outcome.summary.downloadedCount,
                    outcome.summary.conflictCount,
                    outcome.summary.recalculatedCount,
                ),
                stringResource(Res.string.settings_sync_last_at, outcome.at.asLabel()),
            )
        }
        is LastSyncOutcome.Failed -> {
            title = stringResource(Res.string.settings_sync_last_failed_title)
            details = listOf(
                syncErrorMessage(outcome.error),
                stringResource(Res.string.settings_sync_last_at, outcome.at.asLabel()),
            )
        }
        is LastSyncOutcome.Cancelled -> {
            title = stringResource(Res.string.settings_sync_last_cancelled_title)
            details = listOf(stringResource(Res.string.settings_sync_last_at, outcome.at.asLabel()))
        }
    }

    SurferSettingsGroup(title = stringResource(Res.string.settings_sync_section_last)) {
        SurferSettingsRow(
            icon = SurferIcons.Clock,
            title = title,
            supporting = { DetailLines(details) },
            multiline = true,
            modifier = Modifier.testTag(SyncTestTags.LastOutcome),
        )
    }
}

/**
 * Sync being off is not the same answer as an empty queue: the view model stops reading the outbox
 * then, while the rows stay in Room waiting for the switch to come back. Saying "nothing queued"
 * there would report a fact nobody checked, so the disabled case gets its own row and the header
 * drops the count.
 */
@Composable
private fun OutboxSection(state: SyncState) {
    if (!state.syncEnabled) {
        SurferSettingsGroup(
            modifier = Modifier.testTag(SyncTestTags.Outbox),
            title = stringResource(Res.string.settings_sync_section_outbox_unknown),
        ) {
            SurferSettingsRow(
                icon = SurferIcons.Cloud,
                title = stringResource(Res.string.settings_sync_outbox_unknown_title),
                supportingText = stringResource(Res.string.settings_sync_outbox_unknown_supporting),
                multiline = true,
                modifier = Modifier.testTag(SyncTestTags.OutboxUnknown),
            )
        }
        return
    }

    val rows = state.visibleOutbox
    SurferSettingsGroup(
        modifier = Modifier.testTag(SyncTestTags.Outbox),
        title = stringResource(Res.string.settings_sync_section_outbox, rows.size),
        footnote = when {
            rows.isEmpty() -> null
            state.outboxTruncated -> stringResource(Res.string.settings_sync_outbox_footnote) + " " +
                stringResource(Res.string.settings_sync_outbox_truncated, rows.size)
            else -> stringResource(Res.string.settings_sync_outbox_footnote)
        },
    ) {
        if (rows.isEmpty()) {
            SurferSettingsRow(
                icon = SurferIcons.Check,
                title = stringResource(Res.string.settings_sync_outbox_empty_title),
                supportingText = stringResource(Res.string.settings_sync_outbox_empty_supporting),
                modifier = Modifier.testTag(SyncTestTags.OutboxEmpty),
            )
            return@SurferSettingsGroup
        }
        rows.forEach { row -> OutboxRow(row) }
    }
}

@Composable
private fun OutboxRow(row: PendingMutation) {
    val scope = row.scopeKey ?: stringResource(Res.string.settings_sync_outbox_scope_root)
    val details = buildList {
        add(row.entityId)
        add(stringResource(Res.string.settings_sync_outbox_scope, scope))
        add(stringResource(Res.string.settings_sync_outbox_attempts, row.attempts))
        row.lastError?.let { add(stringResource(Res.string.settings_sync_outbox_error, it)) }
    }
    SurferSettingsRow(
        icon = SurferIcons.ArrowUp,
        title = stringResource(Res.string.settings_sync_outbox_row_title, row.operation.name, row.entityType),
        supporting = { DetailLines(details, highlightLast = row.lastError != null) },
        multiline = true,
        modifier = Modifier.testTag(SyncTestTags.outboxRow(row.id)),
    )
}

/**
 * Per-collection pull cursors. This section is the reason the screen exists: `lastPulledAt` is the
 * `updatedAt >` filter of the next pull, so a cursor sitting ahead of a remote write is why that
 * write never arrives — the failure mode behind issue #356 and the cloud-login hydration bug.
 */
@Composable
private fun CursorsSection(cursors: List<SyncMeta>) {
    SurferSettingsGroup(
        modifier = Modifier.testTag(SyncTestTags.Cursors),
        title = stringResource(Res.string.settings_sync_section_cursors),
        footnote = stringResource(Res.string.settings_sync_cursors_footnote),
    ) {
        if (cursors.isEmpty()) {
            SurferSettingsRow(
                icon = SurferIcons.Download,
                title = stringResource(Res.string.settings_sync_cursors_empty_title),
                supportingText = stringResource(Res.string.settings_sync_cursors_empty_supporting),
                modifier = Modifier.testTag(SyncTestTags.CursorsEmpty),
            )
            return@SurferSettingsGroup
        }
        cursors.forEach { meta ->
            SurferSettingsRow(
                icon = SurferIcons.Download,
                title = meta.collection,
                supporting = {
                    DetailLines(
                        listOf(
                            stringResource(Res.string.settings_sync_cursor_pulled, meta.lastPulledAt.asLabel()),
                            stringResource(Res.string.settings_sync_cursor_success, meta.lastSyncSuccessAt.asLabel()),
                            stringResource(Res.string.settings_sync_cursor_attempt, meta.lastSyncAttemptAt.asLabel()),
                        ),
                    )
                },
                multiline = true,
                modifier = Modifier.testTag(SyncTestTags.cursorRow(meta.collection)),
            )
        }
    }
}

@Composable
private fun ActionsSection(
    state: SyncState,
    onEvent: (SyncEvent) -> Unit,
) {
    SurferSettingsGroup(
        title = stringResource(Res.string.settings_sync_section_actions),
        footnote = if (state.syncEnabled) {
            null
        } else {
            stringResource(Res.string.settings_sync_actions_disabled_footnote)
        },
    ) {
        SurferSettingsRow(
            icon = SurferIcons.Sync,
            title = stringResource(Res.string.settings_sync_action_force),
            supportingText = stringResource(Res.string.settings_sync_action_force_supporting),
            iconBackground = AppTheme.materialColors.primaryContainer,
            iconTint = AppTheme.materialColors.onPrimaryContainer,
            onClick = { onEvent(SyncEvent.OnSyncClick) },
            trailing = { SurferSettingsChevron() },
            multiline = true,
            modifier = Modifier.testTag(SyncTestTags.ForceSyncRow),
        )
        SurferSettingsRow(
            icon = SurferIcons.Restore,
            title = stringResource(Res.string.settings_sync_action_reset_cursors),
            supportingText = stringResource(Res.string.settings_sync_action_reset_cursors_supporting),
            danger = true,
            // No workspace means no scope to clear; leaving the row inert beats clearing nothing
            // and reporting it as done.
            onClick = if (state.canResetCursors) ({ onEvent(SyncEvent.OnResetCursorsClick) }) else null,
            multiline = true,
            modifier = Modifier.testTag(SyncTestTags.ResetCursorsRow),
        )
    }
}

/** Stacked diagnostic lines; [highlightLast] paints a trailing error line in the error tint. */
@Composable
private fun DetailLines(
    lines: List<String>,
    highlightLast: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEachIndexed { index, line ->
            val isError = highlightLast && index == lines.lastIndex
            Text(
                text = line,
                style = AppTheme.typography.labelSmall,
                color = if (isError) AppTheme.materialColors.error else AppTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

@Composable
@Suppress("CyclomaticComplexMethod")
private fun syncStepLabel(step: SyncStep): String = when (step) {
    SyncStep.WaitingForNetwork -> stringResource(Res.string.settings_sync_step_waiting_network)
    SyncStep.Started -> stringResource(Res.string.settings_sync_step_started)
    SyncStep.UploadingPendingChanges -> stringResource(Res.string.settings_sync_step_uploading)
    is SyncStep.UploadingEntity -> stringResource(
        Res.string.settings_sync_step_uploading_entity,
        step.entityType,
        step.current,
        step.total,
    )
    SyncStep.PullingRemoteChanges -> stringResource(Res.string.settings_sync_step_pulling)
    is SyncStep.PullingCollection ->
        stringResource(Res.string.settings_sync_step_pulling_collection, step.collection)
    SyncStep.RecalculatingProjections -> stringResource(Res.string.settings_sync_step_recalculating)
    is SyncStep.Completed -> stringResource(Res.string.settings_sync_step_completed)
    is SyncStep.Cancelled -> stringResource(Res.string.settings_sync_step_cancelled)
    is SyncStep.Failed -> stringResource(Res.string.settings_sync_step_failed)
}

@Composable
private fun syncErrorMessage(error: SyncError): String = when (error) {
    SyncError.Cancelled -> stringResource(Res.string.settings_sync_error_cancelled)
    SyncError.NetworkUnavailable -> stringResource(Res.string.settings_sync_error_network)
    SyncError.AuthRequired -> stringResource(Res.string.settings_sync_error_auth)
    SyncError.PermissionDenied -> stringResource(Res.string.settings_sync_error_permission)
    // Server-authored copy naming the required version — showing it beats a generic line.
    is SyncError.UnsupportedAppVersion -> error.message
    is SyncError.StorageError -> stringResource(Res.string.settings_sync_error_storage)
    is SyncError.Unknown -> error.cause.message ?: stringResource(Res.string.settings_sync_error_unknown)
}

/** Local wall-clock stamp; [Res.string.settings_sync_value_never] stands in for an absent one. */
@Composable
private fun Instant?.asLabel(): String {
    val instant = this ?: return stringResource(Res.string.settings_sync_value_never)
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val time = listOf(local.hour, local.minute, local.second)
        .joinToString(":") { it.toString().padStart(2, '0') }
    return "${local.date} $time"
}
