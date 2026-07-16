package com.georgeci.moneysurfer.feature.workspace.members

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.workspace.generated.resources.Res
import moneysurfer.feature.workspace.generated.resources.workspace_members_back_content_description
import moneysurfer.feature.workspace.generated.resources.workspace_members_subtitle_editor_format
import moneysurfer.feature.workspace.generated.resources.workspace_members_subtitle_owner_format
import moneysurfer.feature.workspace.generated.resources.workspace_members_subtitle_unknown_format
import moneysurfer.feature.workspace.generated.resources.workspace_members_subtitle_viewer_format
import moneysurfer.feature.workspace.generated.resources.workspace_members_title_format
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Full-screen host for [WorkspaceMembersScreen]. Reuses the same view-model as the bottom-sheet
 * variant — only the surrounding chrome differs. Used from Settings → Members entry, where a
 * dedicated screen reads better than a modal sheet.
 */
@Composable
fun WorkspaceManageScreen(
    workspaceId: WorkspaceId,
    onNavigateBack: () -> Unit,
    onNavigateToInvite: (WorkspaceId) -> Unit,
    onNavigateToMemberActions: (WorkspaceId, UserId) -> Unit,
    viewModel: WorkspaceMembersViewModel = koinViewModel(
        key = workspaceId.value,
    ) { parametersOf(workspaceId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            WorkspaceMembersEffect.Dismiss -> onNavigateBack()
            WorkspaceMembersEffect.OpenInvite -> onNavigateToInvite(workspaceId)
            is WorkspaceMembersEffect.OpenMemberActions ->
                onNavigateToMemberActions(effect.workspaceId, effect.targetUserId)
            WorkspaceMembersEffect.LeftWorkspace -> onNavigateBack()
            is WorkspaceMembersEffect.ShowError -> Unit
        }
    }

    WorkspaceManageScreenContent(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onNavigateBack,
    )
}

@Composable
private fun WorkspaceManageScreenContent(
    state: WorkspaceMembersState,
    onEvent: (WorkspaceMembersEvent) -> Unit,
    onBack: () -> Unit,
) {
    val content = state as? WorkspaceMembersState.Content
    val title = content?.workspaceName
        ?.takeIf { it.isNotEmpty() }
        ?.let { stringResource(Res.string.workspace_members_title_format, it) }
        .orEmpty()
    val subtitle = content?.let { roleSubtitle(it.viewerRole, it.activeCount) }.orEmpty()
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = SurferIcons.Back,
                            contentDescription = stringResource(Res.string.workspace_members_back_content_description),
                            tint = AppTheme.materialColors.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.materialColors.surface,
                    titleContentColor = AppTheme.materialColors.onSurface,
                ),
                title = {
                    Column {
                        Text(
                            text = title,
                            style = AppTheme.typography.titleMedium,
                            color = AppTheme.materialColors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                style = AppTheme.typography.bodySmall,
                                color = AppTheme.materialColors.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            WorkspaceMembersScreen(
                state = state,
                onEvent = onEvent,
                showHeader = false,
            )
            Spacer(Modifier.height(padding.calculateBottomPadding()))
        }
    }
}

@Composable
private fun roleSubtitle(role: WorkspaceRole?, memberCount: Int): String = when (role) {
    WorkspaceRole.OWNER ->
        pluralStringResource(Res.plurals.workspace_members_subtitle_owner_format, memberCount, memberCount)
    WorkspaceRole.EDITOR ->
        pluralStringResource(Res.plurals.workspace_members_subtitle_editor_format, memberCount, memberCount)
    WorkspaceRole.VIEWER ->
        pluralStringResource(Res.plurals.workspace_members_subtitle_viewer_format, memberCount, memberCount)
    null ->
        pluralStringResource(Res.plurals.workspace_members_subtitle_unknown_format, memberCount, memberCount)
}

// ── Previews ─────────────────────────────────────────────────────────────────

@Preview
@Composable
private fun WorkspaceManageScreenPreview() {
    AppTheme {
        WorkspaceManageScreenContent(
            state = previewState(WorkspaceRole.OWNER),
            onEvent = {},
            onBack = {},
        )
    }
}
