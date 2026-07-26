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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import moneysurfer.feature.workspace.generated.resources.workspace_selector_cloud_unavailable
import moneysurfer.feature.workspace.generated.resources.workspace_selector_continue
import moneysurfer.feature.workspace.generated.resources.workspace_selector_create_subtitle
import moneysurfer.feature.workspace.generated.resources.workspace_selector_create_title
import moneysurfer.feature.workspace.generated.resources.workspace_selector_empty
import moneysurfer.feature.workspace.generated.resources.workspace_selector_heading_subtitle
import moneysurfer.feature.workspace.generated.resources.workspace_selector_heading_subtitle_offline
import moneysurfer.feature.workspace.generated.resources.workspace_selector_heading_title
import moneysurfer.feature.workspace.generated.resources.workspace_selector_sign_out
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

/**
 * The exits [WorkspaceSelectorScreen] offers, one per [WorkspaceSelectorEffect]. Grouped into a
 * holder so the entry point stays under the parameter limit as destinations are added — see
 * AGENTS.md → UI Rules.
 */
data class WorkspaceSelectorNavigation(
    val onNavigateToDashboard: () -> Unit,
    val onNavigateToSignIn: () -> Unit,
    val onNavigateToWorkspaceCreation: () -> Unit,
    val onNavigateToWorkspaceEdit: (WorkspaceId) -> Unit,
    val onNavigateToWorkspaceMembers: (WorkspaceId) -> Unit,
)

/**
 * [cloudDataUnavailable] comes straight from the route: the account owns workspaces that the
 * post-auth pull never brought down, so the empty list below is a hydration failure rather than
 * a new account. Pure presentation — nothing in the view model depends on it (issue #342).
 */
@Composable
fun WorkspaceSelectorScreen(
    showActions: Boolean,
    navigation: WorkspaceSelectorNavigation,
    cloudDataUnavailable: Boolean = false,
    viewModel: WorkspaceSelectorViewModel = koinViewModel(
        key = "selector:$showActions",
    ) { parametersOf(showActions) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            WorkspaceSelectorEffect.NavigateToDashboard -> navigation.onNavigateToDashboard()
            WorkspaceSelectorEffect.NavigateToSignIn -> navigation.onNavigateToSignIn()
            WorkspaceSelectorEffect.NavigateToWorkspaceCreation -> navigation.onNavigateToWorkspaceCreation()
            is WorkspaceSelectorEffect.NavigateToWorkspaceEdit ->
                navigation.onNavigateToWorkspaceEdit(effect.workspaceId)

            is WorkspaceSelectorEffect.NavigateToWorkspaceMembers ->
                navigation.onNavigateToWorkspaceMembers(effect.workspaceId)
        }
    }

    when (val current = state) {
        is WorkspaceSelectorState.Loading -> WorkspaceSelectorLoading()
        is WorkspaceSelectorState.Content -> WorkspaceSelectorContent(
            state = current,
            cloudDataUnavailable = cloudDataUnavailable,
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
    cloudDataUnavailable: Boolean = false,
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
                SurferToolbar(
                    title = stringResource(Res.string.workspace_selector_heading_title),
                    actions = {
                        // The only exit when the selector is the root of the stack: no back
                        // entry, and Settings sits behind Dashboard, which needs a workspace.
                        if (state.canSignOut) {
                            IconButton(onClick = { onEvent(WorkspaceSelectorEvent.OnSignOutClick) }) {
                                Icon(
                                    imageVector = SurferIcons.Logout,
                                    contentDescription = stringResource(
                                        Res.string.workspace_selector_sign_out,
                                    ),
                                )
                            }
                        }
                    },
                )
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
                            text = stringResource(
                                if (cloudDataUnavailable) {
                                    Res.string.workspace_selector_cloud_unavailable
                                } else {
                                    Res.string.workspace_selector_empty
                                },
                            ),
                            style = AppTheme.typography.bodyLarge,
                            color = if (cloudDataUnavailable) {
                                AppTheme.materialColors.error
                            } else {
                                AppTheme.materialColors.onSurfaceVariant
                            },
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

@Preview
@Composable
private fun WorkspaceSelectorCloudUnavailablePreview() {
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
            cloudDataUnavailable = true,
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
