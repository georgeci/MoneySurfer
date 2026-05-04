package com.georgeci.moneysurfer.feature.workspace.incoming

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId
import com.georgeci.moneysurfer.uikit.components.SurferButton
import com.georgeci.moneysurfer.uikit.components.SurferButtonStyle
import com.georgeci.moneysurfer.uikit.components.workspace.SurferIncomingInviteCard
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import kotlinx.coroutines.launch
import moneysurfer.feature.workspace.generated.resources.Res
import moneysurfer.feature.workspace.generated.resources.workspace_incoming_accept
import moneysurfer.feature.workspace.generated.resources.workspace_incoming_decline
import moneysurfer.feature.workspace.generated.resources.workspace_incoming_empty_subtitle
import moneysurfer.feature.workspace.generated.resources.workspace_incoming_empty_title
import moneysurfer.feature.workspace.generated.resources.workspace_incoming_error_unknown
import moneysurfer.feature.workspace.generated.resources.workspace_incoming_expired
import moneysurfer.feature.workspace.generated.resources.workspace_incoming_header_subtitle
import moneysurfer.feature.workspace.generated.resources.workspace_incoming_header_title_format
import moneysurfer.feature.workspace.generated.resources.workspace_incoming_refresh_failed
import moneysurfer.feature.workspace.generated.resources.workspace_incoming_retry
import moneysurfer.feature.workspace.generated.resources.workspace_incoming_title
import moneysurfer.feature.workspace.generated.resources.workspace_incoming_workspace_unknown_format
import moneysurfer.feature.workspace.generated.resources.workspace_members_role_editor
import moneysurfer.feature.workspace.generated.resources.workspace_members_role_owner
import moneysurfer.feature.workspace.generated.resources.workspace_members_role_viewer
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun IncomingInvitesScreen(
    onNavigateBack: () -> Unit,
    viewModel: IncomingInvitesViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val errorText = stringResource(Res.string.workspace_incoming_error_unknown)

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            IncomingInvitesEffect.NavigateBack -> onNavigateBack()
            is IncomingInvitesEffect.ShowError -> {
                scope.launch { snackbarHostState.showSnackbar(errorText) }
            }
        }
    }

    // Re-pull invites whenever the screen returns to the foreground — VM only does
    // a one-shot pull in init {}, so without this an invite that landed while the user
    // was away wouldn't appear until they kill+reopen the app.
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(IncomingInvitesEvent.OnRefresh)
        onPauseOrDispose { }
    }

    Body(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Body(
    state: IncomingInvitesState,
    snackbarHostState: SnackbarHostState,
    onEvent: (IncomingInvitesEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.workspace_incoming_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(IncomingInvitesEvent.OnBackClick) }) {
                        Icon(imageVector = SurferIcons.Back, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.materialColors.surface,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            if (state.refreshError != null) {
                RefreshErrorBanner(onRetry = { onEvent(IncomingInvitesEvent.OnRefresh) })
            }
            ScreenContent(
                state = state,
                bottomPadding = padding.calculateBottomPadding(),
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun ScreenContent(
    state: IncomingInvitesState,
    bottomPadding: Dp,
    onEvent: (IncomingInvitesEvent) -> Unit,
) {
    when {
        state.loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "…",
                style = AppTheme.typography.titleLarge,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
        }

        state.invites.isEmpty() -> EmptyView()

        else -> Column(modifier = Modifier.fillMaxSize()) {
            Header(activeCount = state.invites.count { !it.expired })
            Spacer(Modifier.height(AppTheme.spacing.small))
            LazyColumn(
                contentPadding = PaddingValues(
                    start = AppTheme.spacing.large,
                    top = AppTheme.spacing.small,
                    end = AppTheme.spacing.large,
                    bottom = bottomPadding + AppTheme.spacing.small,
                ),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
            ) {
                items(state.invites, key = { it.id.value }) { invite ->
                    SurferIncomingInviteCard(
                        workspaceName = invite.workspaceName.ifBlank {
                            stringResource(
                                Res.string.workspace_incoming_workspace_unknown_format,
                                invite.workspaceIdShort,
                            )
                        },
                        inviterLabel = "@${invite.invitedByUserId}",
                        roleLabel = roleLabel(invite.role),
                        expired = invite.expired,
                        expiredLabel = stringResource(Res.string.workspace_incoming_expired),
                        acceptLabel = stringResource(Res.string.workspace_incoming_accept),
                        declineLabel = stringResource(Res.string.workspace_incoming_decline),
                        onAccept = { onEvent(IncomingInvitesEvent.OnAccept(invite.id)) },
                        onDecline = { onEvent(IncomingInvitesEvent.OnDecline(invite.id)) },
                        busy = state.busyId == invite.id,
                    )
                }
            }
        }
    }
}

@Composable
private fun RefreshErrorBanner(onRetry: () -> Unit) {
    val cs = AppTheme.materialColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(cs.errorContainer)
            .padding(horizontal = AppTheme.spacing.large, vertical = AppTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.workspace_incoming_refresh_failed),
            style = AppTheme.typography.bodyMedium,
            color = cs.onErrorContainer,
            modifier = Modifier.weight(1f),
        )
        SurferButton(
            text = stringResource(Res.string.workspace_incoming_retry),
            style = SurferButtonStyle.Text,
            onClick = onRetry,
        )
    }
}

@Composable
private fun Header(activeCount: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.large, vertical = AppTheme.spacing.small),
    ) {
        Text(
            text = stringResource(Res.string.workspace_incoming_header_title_format, activeCount),
            style = AppTheme.typography.headlineSmall,
            color = AppTheme.materialColors.onSurface,
        )
        Text(
            text = stringResource(Res.string.workspace_incoming_header_subtitle),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.materialColors.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun roleLabel(role: WorkspaceRole): String = when (role) {
    WorkspaceRole.OWNER -> stringResource(Res.string.workspace_members_role_owner)
    WorkspaceRole.EDITOR -> stringResource(Res.string.workspace_members_role_editor)
    WorkspaceRole.VIEWER -> stringResource(Res.string.workspace_members_role_viewer)
}

@Composable
private fun EmptyView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppTheme.spacing.large),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = SurferIcons.Mail,
            contentDescription = null,
            tint = AppTheme.materialColors.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.workspace_incoming_empty_title),
            style = AppTheme.typography.titleMedium,
            color = AppTheme.materialColors.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.workspace_incoming_empty_subtitle),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.materialColors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private val PreviewIncoming = listOf(
    IncomingInviteUi(
        id = WorkspaceInviteId("i-1"),
        workspaceName = "Lisbon trip '26",
        workspaceIdShort = "ws-001",
        role = WorkspaceRole.EDITOR,
        invitedByUserId = "kasiam",
        expired = false,
        expiresAt = 0L,
    ),
    IncomingInviteUi(
        id = WorkspaceInviteId("i-2"),
        workspaceName = "Office lunch club",
        workspaceIdShort = "ws-002",
        role = WorkspaceRole.VIEWER,
        invitedByUserId = "marekw",
        expired = false,
        expiresAt = 0L,
    ),
    IncomingInviteUi(
        id = WorkspaceInviteId("i-3"),
        workspaceName = "",
        workspaceIdShort = "ws-003",
        role = WorkspaceRole.VIEWER,
        invitedByUserId = "lenab",
        expired = true,
        expiresAt = 0L,
    ),
)

@Preview
@Composable
private fun IncomingInvitesScreenPreview_WithInvites() {
    AppTheme {
        Body(
            state = IncomingInvitesState(loading = false, invites = PreviewIncoming),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun IncomingInvitesScreenPreview_AcceptingBusy() {
    AppTheme {
        Body(
            state = IncomingInvitesState(
                loading = false,
                invites = PreviewIncoming,
                busyId = WorkspaceInviteId("i-1"),
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun IncomingInvitesScreenPreview_Empty() {
    AppTheme {
        Body(
            state = IncomingInvitesState(loading = false, invites = emptyList()),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun IncomingInvitesScreenPreview_Loading() {
    AppTheme {
        Body(
            state = IncomingInvitesState(loading = true),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
        )
    }
}
