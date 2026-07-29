package com.georgeci.moneysurfer.feature.workspace.members

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.uikit.components.SurferButton
import com.georgeci.moneysurfer.uikit.components.SurferButtonStyle
import com.georgeci.moneysurfer.uikit.components.base.SurferSegmentedControl
import com.georgeci.moneysurfer.uikit.components.workspace.SurferInvitePendingRow
import com.georgeci.moneysurfer.uikit.components.workspace.SurferInviteRow
import com.georgeci.moneysurfer.uikit.components.workspace.SurferMemberRow
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.preview.SurferBottomSheetPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.workspace.generated.resources.Res
import moneysurfer.feature.workspace.generated.resources.workspace_members_close_content_description
import moneysurfer.feature.workspace.generated.resources.workspace_members_invite_cancel_content_description
import moneysurfer.feature.workspace.generated.resources.workspace_members_invite_status_expired
import moneysurfer.feature.workspace.generated.resources.workspace_members_invite_status_pending
import moneysurfer.feature.workspace.generated.resources.workspace_members_invite_subtitle
import moneysurfer.feature.workspace.generated.resources.workspace_members_invite_title
import moneysurfer.feature.workspace.generated.resources.workspace_members_invites_empty
import moneysurfer.feature.workspace.generated.resources.workspace_members_leave
import moneysurfer.feature.workspace.generated.resources.workspace_members_leave_cancel
import moneysurfer.feature.workspace.generated.resources.workspace_members_leave_confirm
import moneysurfer.feature.workspace.generated.resources.workspace_members_leave_message_format
import moneysurfer.feature.workspace.generated.resources.workspace_members_leave_title_format
import moneysurfer.feature.workspace.generated.resources.workspace_members_role_editor
import moneysurfer.feature.workspace.generated.resources.workspace_members_role_owner
import moneysurfer.feature.workspace.generated.resources.workspace_members_role_viewer
import moneysurfer.feature.workspace.generated.resources.workspace_members_subtitle_editor_format
import moneysurfer.feature.workspace.generated.resources.workspace_members_subtitle_owner_format
import moneysurfer.feature.workspace.generated.resources.workspace_members_subtitle_unknown_format
import moneysurfer.feature.workspace.generated.resources.workspace_members_subtitle_viewer_format
import moneysurfer.feature.workspace.generated.resources.workspace_members_tab_active_format
import moneysurfer.feature.workspace.generated.resources.workspace_members_tab_invited_format
import moneysurfer.feature.workspace.generated.resources.workspace_members_title_format
import moneysurfer.feature.workspace.generated.resources.workspace_members_view_only_hint
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Stable selectors for the workspace members view — see docs/testing/testing-strategy.md. */
object WorkspaceMembersTestTags {
    const val Root = "workspaceMembers:root"
    const val Invite = "workspaceMembers:invite"
}

/**
 * Pure content composable for the Members view. Renders header + Active/Invited tabs +
 * member list / pending-invite list against the provided [state]/[onEvent]. Callers are
 * responsible for the surrounding container (bottom sheet, dialog, full screen, etc.).
 */
@Composable
fun WorkspaceMembersScreen(
    state: WorkspaceMembersState,
    onEvent: (WorkspaceMembersEvent) -> Unit,
    modifier: Modifier = Modifier,
    headerNavigationIcon: ImageVector = SurferIcons.Close,
    showHeader: Boolean = true,
) {
    when (state) {
        is WorkspaceMembersState.Loading ->
            Box(modifier = modifier.fillMaxWidth().height(200.dp))
        is WorkspaceMembersState.Content ->
            WorkspaceMembersContent(
                state = state,
                onEvent = onEvent,
                modifier = modifier,
                headerNavigationIcon = headerNavigationIcon,
                showHeader = showHeader,
            )
    }
    if (state is WorkspaceMembersState.Content && state.showLeaveDialog) {
        LeaveDialog(
            workspaceName = state.workspaceName,
            busy = state.busy,
            onCancel = { onEvent(WorkspaceMembersEvent.OnLeaveCancel) },
            onConfirm = { onEvent(WorkspaceMembersEvent.OnLeaveConfirm) },
        )
    }
}

@Composable
private fun WorkspaceMembersContent(
    state: WorkspaceMembersState.Content,
    onEvent: (WorkspaceMembersEvent) -> Unit,
    modifier: Modifier = Modifier,
    headerNavigationIcon: ImageVector = SurferIcons.Close,
    showHeader: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WorkspaceMembersTestTags.Root)
            .surferTestTagAsId(),
    ) {
        if (showHeader) {
            Header(
                workspaceName = state.workspaceName,
                memberCount = state.activeCount,
                viewerRole = state.viewerRole,
                navigationIcon = headerNavigationIcon,
                onClose = { onEvent(WorkspaceMembersEvent.OnDismiss) },
            )
        }

        if (state.canManage) {
            Box(modifier = Modifier.padding(horizontal = AppTheme.spacing.default)) {
                SurferInviteRow(
                    title = stringResource(Res.string.workspace_members_invite_title),
                    subtitle = stringResource(Res.string.workspace_members_invite_subtitle),
                    onClick = { onEvent(WorkspaceMembersEvent.OnInviteClick) },
                    modifier = Modifier.testTag(WorkspaceMembersTestTags.Invite),
                )
            }
            Spacer(Modifier.height(AppTheme.spacing.medium))
        }

        SurferSegmentedControl(
            options = listOf(MembersTab.Active, MembersTab.Invited),
            selected = state.tab,
            label = { tab ->
                when (tab) {
                    MembersTab.Active -> stringResource(
                        Res.string.workspace_members_tab_active_format,
                        state.activeCount,
                    )
                    MembersTab.Invited -> stringResource(
                        Res.string.workspace_members_tab_invited_format,
                        state.invitedCount,
                    )
                }
            },
            onSelect = { onEvent(WorkspaceMembersEvent.OnTabSelected(it)) },
            modifier = Modifier.padding(horizontal = AppTheme.spacing.large),
        )
        Spacer(Modifier.height(AppTheme.spacing.small))

        when (state.tab) {
            MembersTab.Active -> ActiveList(state = state, onEvent = onEvent)
            MembersTab.Invited -> InvitedList(state = state, onEvent = onEvent)
        }

        if (state.canLeave) {
            Spacer(Modifier.height(AppTheme.spacing.medium))
            Box(
                modifier = Modifier.padding(horizontal = AppTheme.spacing.large).fillMaxWidth(),
            ) {
                SurferButton(
                    text = stringResource(Res.string.workspace_members_leave),
                    style = SurferButtonStyle.Outlined,
                    enabled = !state.busy,
                    startIcon = SurferIcons.Logout,
                    onClick = { onEvent(WorkspaceMembersEvent.OnLeaveClick) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Spacer(Modifier.height(AppTheme.spacing.large))
    }
}

@Composable
private fun Header(
    workspaceName: String,
    memberCount: Int,
    viewerRole: WorkspaceRole?,
    navigationIcon: ImageVector,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.large, end = AppTheme.spacing.small, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppTheme.materialColors.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = workspaceName.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                style = AppTheme.typography.titleMedium,
                color = AppTheme.materialColors.onSurface,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.workspace_members_title_format, workspaceName),
                style = AppTheme.typography.titleLarge,
                color = AppTheme.materialColors.onSurface,
            )
            Text(
                text = subtitleFor(viewerRole, memberCount),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        IconButton(onClick = onClose) {
            Icon(
                imageVector = navigationIcon,
                contentDescription = stringResource(Res.string.workspace_members_close_content_description),
                tint = AppTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun subtitleFor(role: WorkspaceRole?, memberCount: Int): String = when (role) {
    WorkspaceRole.OWNER ->
        pluralStringResource(Res.plurals.workspace_members_subtitle_owner_format, memberCount, memberCount)
    WorkspaceRole.EDITOR ->
        pluralStringResource(Res.plurals.workspace_members_subtitle_editor_format, memberCount, memberCount)
    WorkspaceRole.VIEWER ->
        pluralStringResource(Res.plurals.workspace_members_subtitle_viewer_format, memberCount, memberCount)
    null ->
        pluralStringResource(Res.plurals.workspace_members_subtitle_unknown_format, memberCount, memberCount)
}

@Composable
private fun ActiveList(
    state: WorkspaceMembersState.Content,
    onEvent: (WorkspaceMembersEvent) -> Unit,
) {
    state.members.forEachIndexed { index, member ->
        if (index > 0) Spacer(Modifier.height(AppTheme.spacing.small))
        val clickable = state.canManage && !member.isYou && member.role != WorkspaceRole.OWNER
        val rowModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.large)
            .let { if (clickable) it.clickable { onEvent(WorkspaceMembersEvent.OnMemberClick(member.userId)) } else it }
        Box(modifier = rowModifier) {
            SurferMemberRow(
                name = member.name,
                initial = member.initial,
                roleLabel = roleLabel(member.role),
                isYou = member.isYou,
                secondaryLine = member.email.takeIf { it.isNotBlank() },
            )
        }
    }
    if (!state.canManage && state.viewerRole != null) {
        ViewOnlyHint()
    }
}

@Composable
private fun InvitedList(
    state: WorkspaceMembersState.Content,
    onEvent: (WorkspaceMembersEvent) -> Unit,
) {
    val pending = state.pendingInvites.filter { !it.isExpired }
    val expired = state.pendingInvites.filter { it.isExpired }
    if (pending.isEmpty() && expired.isEmpty()) {
        EmptyHint(stringResource(Res.string.workspace_members_invites_empty))
        return
    }
    val pendingLabel = stringResource(Res.string.workspace_members_invite_status_pending)
    val expiredLabel = stringResource(Res.string.workspace_members_invite_status_expired)
    val all = pending + expired
    all.forEachIndexed { index, invite ->
        if (index > 0) Spacer(Modifier.height(AppTheme.spacing.small))
        InviteRow(
            invite = invite,
            statusLabel = if (invite.isExpired) expiredLabel else pendingLabel,
            canCancel = state.canManage && !state.busy,
            onCancel = { onEvent(WorkspaceMembersEvent.OnInviteCancel(invite.id)) },
        )
    }
}

@Composable
private fun InviteRow(
    invite: InviteUi,
    statusLabel: String,
    canCancel: Boolean,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.large),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            SurferInvitePendingRow(
                email = invite.email,
                statusLabel = statusLabel,
                isExpired = invite.isExpired,
                roleLabel = roleLabel(invite.role),
            )
        }
        if (canCancel) {
            IconButton(onClick = onCancel) {
                Icon(
                    imageVector = SurferIcons.Close,
                    contentDescription = stringResource(
                        Res.string.workspace_members_invite_cancel_content_description,
                    ),
                    tint = AppTheme.materialColors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ViewOnlyHint() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.large, vertical = AppTheme.spacing.small)
            .clip(RoundedCornerShape(12.dp))
            .background(AppTheme.materialColors.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = SurferIcons.People,
            contentDescription = SurferSemantics.Decorative,
            tint = AppTheme.materialColors.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(Res.string.workspace_members_view_only_hint),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    val outline = AppTheme.materialColors.outlineVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.large, vertical = AppTheme.spacing.small)
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    style = Stroke(width = 1.dp.toPx()),
                    cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                )
            }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = AppTheme.typography.bodySmall,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
    }
}

@Composable
private fun LeaveDialog(
    workspaceName: String,
    busy: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(
                    text = stringResource(Res.string.workspace_members_leave_confirm),
                    color = AppTheme.materialColors.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel, enabled = !busy) {
                Text(stringResource(Res.string.workspace_members_leave_cancel))
            }
        },
        title = { Text(stringResource(Res.string.workspace_members_leave_title_format, workspaceName)) },
        text = { Text(stringResource(Res.string.workspace_members_leave_message_format, workspaceName)) },
    )
}

@Composable
private fun roleLabel(role: WorkspaceRole): String = when (role) {
    WorkspaceRole.OWNER -> stringResource(Res.string.workspace_members_role_owner)
    WorkspaceRole.EDITOR -> stringResource(Res.string.workspace_members_role_editor)
    WorkspaceRole.VIEWER -> stringResource(Res.string.workspace_members_role_viewer)
}

// ── Previews ─────────────────────────────────────────────────────────────────
// Each variant exercises a different role / tab / dialog combination so changes
// to the layout get reviewed in isolation rather than only via the running app.

@Preview
@Composable
private fun WorkspaceMembersScreenPreviewOwnerActive() {
    SurferBottomSheetPreview {
        WorkspaceMembersScreen(state = previewState(WorkspaceRole.OWNER), onEvent = {})
    }
}

@Preview
@Composable
private fun WorkspaceMembersScreenPreviewOwnerInvited() {
    SurferBottomSheetPreview {
        WorkspaceMembersScreen(
            state = previewState(WorkspaceRole.OWNER, tab = MembersTab.Invited),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun WorkspaceMembersScreenPreviewOwnerInvitedEmpty() {
    SurferBottomSheetPreview {
        WorkspaceMembersScreen(
            state = previewState(WorkspaceRole.OWNER, tab = MembersTab.Invited, invites = emptyList()),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun WorkspaceMembersScreenPreviewEditorActive() {
    SurferBottomSheetPreview {
        WorkspaceMembersScreen(state = previewState(WorkspaceRole.EDITOR), onEvent = {})
    }
}

@Preview
@Composable
private fun WorkspaceMembersScreenPreviewViewerActive() {
    SurferBottomSheetPreview {
        WorkspaceMembersScreen(state = previewState(WorkspaceRole.VIEWER), onEvent = {})
    }
}

@Preview
@Composable
private fun WorkspaceMembersScreenPreviewLeaveDialog() {
    SurferBottomSheetPreview {
        WorkspaceMembersScreen(
            state = previewState(WorkspaceRole.EDITOR, showLeaveDialog = true),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun WorkspaceMembersScreenPreviewLoading() {
    SurferBottomSheetPreview {
        WorkspaceMembersScreen(
            state = WorkspaceMembersState.Loading(WorkspaceId("preview-ws-1")),
            onEvent = {},
        )
    }
}
