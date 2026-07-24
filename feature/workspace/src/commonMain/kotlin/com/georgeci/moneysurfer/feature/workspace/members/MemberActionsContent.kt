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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferBottomSheetPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.workspace.generated.resources.Res
import moneysurfer.feature.workspace.generated.resources.workspace_member_actions_confirm_remove_cancel
import moneysurfer.feature.workspace.generated.resources.workspace_member_actions_confirm_remove_confirm
import moneysurfer.feature.workspace.generated.resources.workspace_member_actions_confirm_remove_message
import moneysurfer.feature.workspace.generated.resources.workspace_member_actions_confirm_remove_title_format
import moneysurfer.feature.workspace.generated.resources.workspace_member_actions_remove
import moneysurfer.feature.workspace.generated.resources.workspace_member_actions_remove_hint
import moneysurfer.feature.workspace.generated.resources.workspace_member_actions_role_editor_hint
import moneysurfer.feature.workspace.generated.resources.workspace_member_actions_role_label
import moneysurfer.feature.workspace.generated.resources.workspace_member_actions_role_viewer_hint
import moneysurfer.feature.workspace.generated.resources.workspace_members_role_editor
import moneysurfer.feature.workspace.generated.resources.workspace_members_role_viewer
import org.jetbrains.compose.resources.stringResource

/**
 * Static content of the per-member actions bottom sheet. Renders the avatar header card, role
 * picker rows (Editor / Viewer), an optional destructive Remove row, and the confirm-removal
 * dialog. The hosting [Composable] provides the surrounding `ModalBottomSheet` chrome and
 * wires events through a ViewModel.
 */
@Composable
internal fun MemberActionsContent(
    state: MemberActionsState.Content,
    onEvent: (MemberActionsEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = AppTheme.spacing.large)) {
        HeaderCard(state)

        Spacer(Modifier.height(AppTheme.spacing.medium))

        Text(
            text = stringResource(Res.string.workspace_member_actions_role_label),
            style = AppTheme.typography.labelLarge,
            color = AppTheme.materialColors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = AppTheme.spacing.small),
        )

        RoleRow(
            label = stringResource(Res.string.workspace_members_role_editor),
            hint = stringResource(Res.string.workspace_member_actions_role_editor_hint),
            selected = state.role == WorkspaceRole.EDITOR,
            enabled = !state.isBusy && state.role != WorkspaceRole.OWNER,
            onClick = { onEvent(MemberActionsEvent.OnRoleSelected(WorkspaceRole.EDITOR)) },
        )
        RoleRow(
            label = stringResource(Res.string.workspace_members_role_viewer),
            hint = stringResource(Res.string.workspace_member_actions_role_viewer_hint),
            selected = state.role == WorkspaceRole.VIEWER,
            enabled = !state.isBusy && state.role != WorkspaceRole.OWNER,
            onClick = { onEvent(MemberActionsEvent.OnRoleSelected(WorkspaceRole.VIEWER)) },
        )

        if (state.canRemove) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = AppTheme.spacing.small)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AppTheme.materialColors.outlineVariant),
            )
            DangerRow(
                label = stringResource(Res.string.workspace_member_actions_remove),
                hint = stringResource(Res.string.workspace_member_actions_remove_hint),
                enabled = !state.isBusy,
                onClick = { onEvent(MemberActionsEvent.OnRemoveClick) },
            )
        }

        if (state.showConfirmRemove) {
            ConfirmRemoveDialog(
                name = state.name,
                onCancel = { onEvent(MemberActionsEvent.OnRemoveCancel) },
                onConfirm = { onEvent(MemberActionsEvent.OnRemoveConfirm) },
            )
        }
    }
}

@Composable
private fun HeaderCard(state: MemberActionsState.Content) {
    Column(
        modifier = Modifier
            .padding(horizontal = AppTheme.spacing.large)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AppTheme.materialColors.surfaceContainerLow)
            .padding(vertical = 20.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(AppTheme.materialColors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.name.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                style = AppTheme.typography.titleLarge,
                color = AppTheme.materialColors.onPrimaryContainer,
            )
        }
        Text(
            text = state.name,
            style = AppTheme.typography.titleLarge,
            color = AppTheme.materialColors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.email.isNotBlank()) {
            Text(
                text = state.email,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.materialColors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RoleRow(
    label: String,
    hint: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = AppTheme.materialColors
    val bg = if (selected) cs.secondaryContainer else cs.surface
    val fg = if (selected) cs.onSecondaryContainer else cs.onSurface
    val hintFg = if (selected) cs.onSecondaryContainer else cs.onSurfaceVariant
    Row(
        modifier = Modifier
            .padding(horizontal = AppTheme.spacing.medium, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(cs.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.first().uppercaseChar().toString(),
                style = AppTheme.typography.labelLarge,
                color = cs.onSurface,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(text = label, style = AppTheme.typography.bodyLarge, color = fg)
            Text(
                text = hint,
                style = AppTheme.typography.bodySmall,
                color = hintFg,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (selected) {
            Icon(
                imageVector = SurferIcons.Check,
                contentDescription = SurferSemantics.Decorative,
                tint = cs.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun DangerRow(
    label: String,
    hint: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = AppTheme.materialColors
    Row(
        modifier = Modifier
            .padding(horizontal = AppTheme.spacing.medium, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(cs.errorContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SurferIcons.Delete,
                contentDescription = SurferSemantics.Decorative,
                tint = cs.onErrorContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(text = label, style = AppTheme.typography.bodyLarge, color = cs.error)
            Text(
                text = hint,
                style = AppTheme.typography.bodySmall,
                color = cs.error.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ConfirmRemoveDialog(name: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val firstName = name.substringBefore(' ').ifBlank { name }
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(Res.string.workspace_member_actions_confirm_remove_confirm),
                    color = AppTheme.materialColors.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.workspace_member_actions_confirm_remove_cancel))
            }
        },
        title = {
            Text(stringResource(Res.string.workspace_member_actions_confirm_remove_title_format, firstName))
        },
        text = { Text(stringResource(Res.string.workspace_member_actions_confirm_remove_message)) },
    )
}

// ── Previews ─────────────────────────────────────────────────────────────────

private fun previewContent(
    role: WorkspaceRole = WorkspaceRole.EDITOR,
    showConfirmRemove: Boolean = false,
    isBusy: Boolean = false,
): MemberActionsState.Content = MemberActionsState.Content(
    workspaceId = WorkspaceId("preview-ws-1"),
    targetUserId = UserId("u-3"),
    name = "Asia W.",
    email = "asia@mail.com",
    role = role,
    status = WorkspaceMemberStatus.ACTIVE,
    isBusy = isBusy,
    showConfirmRemove = showConfirmRemove,
)

@Preview
@Composable
private fun MemberActionsPreview_EditorTarget() {
    SurferBottomSheetPreview {
        MemberActionsContent(state = previewContent(role = WorkspaceRole.EDITOR), onEvent = {})
    }
}

@Preview
@Composable
private fun MemberActionsPreview_ViewerTarget() {
    SurferBottomSheetPreview {
        MemberActionsContent(state = previewContent(role = WorkspaceRole.VIEWER), onEvent = {})
    }
}

@Preview
@Composable
private fun MemberActionsPreview_OwnerTarget() {
    // Owner target — role rows disabled, danger Remove row hidden by `canRemove`.
    SurferBottomSheetPreview {
        MemberActionsContent(state = previewContent(role = WorkspaceRole.OWNER), onEvent = {})
    }
}

@Preview
@Composable
private fun MemberActionsPreview_ConfirmRemove() {
    SurferBottomSheetPreview {
        MemberActionsContent(
            state = previewContent(role = WorkspaceRole.EDITOR, showConfirmRemove = true),
            onEvent = {},
        )
    }
}
