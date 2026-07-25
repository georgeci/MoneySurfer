package com.georgeci.moneysurfer.feature.workspace.selector

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.uikit.components.SurferButton
import com.georgeci.moneysurfer.uikit.components.SurferButtonSize
import com.georgeci.moneysurfer.uikit.components.SurferFullScreenLoader
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.workspace.SurferCreateWorkspaceRow
import com.georgeci.moneysurfer.uikit.components.workspace.SurferWorkspaceRow
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.workspace.generated.resources.Res
import moneysurfer.feature.workspace.generated.resources.workspace_selector_action_edit
import moneysurfer.feature.workspace.generated.resources.workspace_selector_action_members
import moneysurfer.feature.workspace.generated.resources.workspace_selector_continue
import moneysurfer.feature.workspace.generated.resources.workspace_selector_create_subtitle
import moneysurfer.feature.workspace.generated.resources.workspace_selector_create_title
import moneysurfer.feature.workspace.generated.resources.workspace_selector_empty
import moneysurfer.feature.workspace.generated.resources.workspace_selector_heading_subtitle
import moneysurfer.feature.workspace.generated.resources.workspace_selector_heading_subtitle_offline
import moneysurfer.feature.workspace.generated.resources.workspace_selector_heading_title
import moneysurfer.feature.workspace.generated.resources.workspace_selector_use_workspace
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Instant

/** Stable selectors for the workspace selector screen — see docs/testing/testing-strategy.md. */
object WorkspaceSelectorTestTags {
    const val Root = "workspaceSelector:root"
    const val Create = "workspaceSelector:create"
    const val Confirm = "workspaceSelector:confirm"
}

@Composable
fun WorkspaceSelectorScreen(
    showActions: Boolean,
    onNavigateToDashboard: () -> Unit,
    onNavigateToWorkspaceCreation: () -> Unit,
    onNavigateToWorkspaceEdit: (WorkspaceId) -> Unit,
    onNavigateToWorkspaceMembers: (WorkspaceId) -> Unit,
    viewModel: WorkspaceSelectorViewModel = koinViewModel(
        key = "selector:$showActions",
    ) { parametersOf(showActions) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            WorkspaceSelectorEffect.NavigateToDashboard -> onNavigateToDashboard()
            WorkspaceSelectorEffect.NavigateToWorkspaceCreation -> onNavigateToWorkspaceCreation()
            is WorkspaceSelectorEffect.NavigateToWorkspaceEdit -> onNavigateToWorkspaceEdit(effect.workspaceId)
            is WorkspaceSelectorEffect.NavigateToWorkspaceMembers -> onNavigateToWorkspaceMembers(effect.workspaceId)
        }
    }

    when (val current = state) {
        is WorkspaceSelectorState.Loading -> WorkspaceSelectorLoading()
        is WorkspaceSelectorState.Content -> WorkspaceSelectorContent(
            state = current,
            onEvent = viewModel::onEvent,
        )
    }
}

@Composable
private fun WorkspaceSelectorLoading() {
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.surferSafeInsets(),
            containerColor = AppTheme.materialColors.surface,
            topBar = {
                SurferToolbar(title = stringResource(Res.string.workspace_selector_heading_title))
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding))
        }

        SurferFullScreenLoader(modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun WorkspaceSelectorContent(
    state: WorkspaceSelectorState.Content,
    onEvent: (WorkspaceSelectorEvent) -> Unit,
) {
    val editLabel = stringResource(Res.string.workspace_selector_action_edit)
    val membersLabel = stringResource(Res.string.workspace_selector_action_members)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .testTag(WorkspaceSelectorTestTags.Root)
                .surferTestTagAsId(),
            containerColor = AppTheme.materialColors.surface,
            topBar = {
                SurferToolbar(title = stringResource(Res.string.workspace_selector_heading_title))
            },
            bottomBar = {
                ConfirmBar(
                    workspace = state.activeWorkspace,
                    enabled = state.activeWorkspace != null && !state.isSelecting,
                    onClick = { onEvent(WorkspaceSelectorEvent.OnConfirmClick) },
                )
            },
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding()),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
                contentPadding = PaddingValues(
                    bottom = padding.calculateBottomPadding() + AppTheme.spacing.default,
                ),
            ) {
                item(key = "header") {
                    Text(
                        text = stringResource(
                            if (state.isOffline) {
                                Res.string.workspace_selector_heading_subtitle_offline
                            } else {
                                Res.string.workspace_selector_heading_subtitle
                            },
                        ),
                        style = AppTheme.typography.bodyMedium,
                        color = AppTheme.materialColors.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = AppTheme.spacing.large,
                            vertical = AppTheme.spacing.xSmall,
                        ),
                    )
                }

                if (state.workspaces.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = stringResource(Res.string.workspace_selector_empty),
                            style = AppTheme.typography.bodyLarge,
                            color = AppTheme.materialColors.onSurfaceVariant,
                            modifier = Modifier.padding(
                                horizontal = AppTheme.spacing.large,
                                vertical = AppTheme.spacing.small,
                            ),
                        )
                    }
                } else {
                    items(state.workspaces, key = { it.id.value }) { workspace ->
                        val subtitle = workspace.description.takeIf { it.isNotBlank() } ?: workspace.baseCurrency.value
                        SurferWorkspaceRow(
                            name = workspace.name,
                            subtitle = subtitle,
                            selected = workspace.id == state.activeWorkspaceId,
                            enabled = !state.isSelecting,
                            showActions = state.showActions,
                            editLabel = editLabel,
                            membersLabel = membersLabel.takeIf { state.showMemberActions },
                            onClick = { onEvent(WorkspaceSelectorEvent.OnWorkspaceClick(workspace)) },
                            onEditClick = { onEvent(WorkspaceSelectorEvent.OnEditWorkspaceClick(workspace)) },
                            onMembersClick = { onEvent(WorkspaceSelectorEvent.OnMembersClick(workspace)) },
                            modifier = Modifier.padding(horizontal = AppTheme.spacing.default),
                        )
                    }
                }

                item(key = "create") {
                    SurferCreateWorkspaceRow(
                        title = stringResource(Res.string.workspace_selector_create_title),
                        subtitle = stringResource(Res.string.workspace_selector_create_subtitle),
                        onClick = { onEvent(WorkspaceSelectorEvent.OnCreateWorkspaceClick) },
                        modifier = Modifier
                            .padding(
                                horizontal = AppTheme.spacing.default,
                                vertical = AppTheme.spacing.small,
                            )
                            .testTag(WorkspaceSelectorTestTags.Create),
                    )
                }
            }
        }

        if (state.isSelecting) {
            SurferFullScreenLoader(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ConfirmBar(
    workspace: Workspace?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = if (workspace != null) {
        stringResource(Res.string.workspace_selector_use_workspace, workspace.name)
    } else {
        stringResource(Res.string.workspace_selector_continue)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppTheme.materialColors.surface)
            .navigationBarsPadding()
            .padding(horizontal = AppTheme.spacing.default)
            .padding(top = AppTheme.spacing.medium, bottom = AppTheme.spacing.large),
    ) {
        SurferButton(
            text = label,
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WorkspaceSelectorTestTags.Confirm),
            size = SurferButtonSize.Biggest,
            enabled = enabled,
            endIcon = SurferIcons.ChevronRight,
        )
    }
}

@Preview
@Composable
private fun WorkspaceSelectorPreviewNoActions() {
    AppTheme {
        WorkspaceSelectorContent(
            state = WorkspaceSelectorState.Content(
                workspaces = previewWorkspaces,
                selectedWorkspaceId = WorkspaceId("preview-ws-2"),
                pendingWorkspaceId = WorkspaceId("preview-ws-2"),
                showActions = false,
                isSelecting = false,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun WorkspaceSelectorPreviewWithActions() {
    AppTheme {
        WorkspaceSelectorContent(
            state = WorkspaceSelectorState.Content(
                workspaces = previewWorkspaces,
                selectedWorkspaceId = WorkspaceId("preview-ws-2"),
                pendingWorkspaceId = WorkspaceId("preview-ws-2"),
                showActions = true,
                isSelecting = false,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun WorkspaceSelectorEmptyPreview() {
    AppTheme {
        WorkspaceSelectorContent(
            state = WorkspaceSelectorState.Content(
                workspaces = emptyList(),
                selectedWorkspaceId = null,
                pendingWorkspaceId = null,
                showActions = false,
                isSelecting = false,
            ),
            onEvent = {},
        )
    }
}

private val previewWorkspaces = listOf(
    Workspace(
        id = WorkspaceId("preview-ws-1"),
        name = "Personal",
        description = "Just you",
        baseCurrency = CurrencyCode("EUR"),
        ownerId = UserId("preview-user-1"),
        createdAt = Instant.fromEpochMilliseconds(0),
    ),
    Workspace(
        id = WorkspaceId("preview-ws-2"),
        name = "Family",
        description = "Household budget",
        baseCurrency = CurrencyCode("EUR"),
        ownerId = UserId("preview-user-1"),
        createdAt = Instant.fromEpochMilliseconds(0),
    ),
    Workspace(
        id = WorkspaceId("preview-ws-3"),
        name = "Lisbon trip",
        description = "Apr 12 – Apr 22",
        baseCurrency = CurrencyCode("EUR"),
        ownerId = UserId("preview-user-1"),
        createdAt = Instant.fromEpochMilliseconds(0),
    ),
)
