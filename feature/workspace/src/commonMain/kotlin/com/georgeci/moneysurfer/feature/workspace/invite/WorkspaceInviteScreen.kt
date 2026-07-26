package com.georgeci.moneysurfer.feature.workspace.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.usecase.InviteError
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.components.SurferButton
import com.georgeci.moneysurfer.uikit.components.SurferButtonSize
import com.georgeci.moneysurfer.uikit.components.SurferButtonStyle
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import kotlinx.coroutines.launch
import moneysurfer.feature.workspace.generated.resources.Res
import moneysurfer.feature.workspace.generated.resources.workspace_invite_close_content_description
import moneysurfer.feature.workspace.generated.resources.workspace_invite_email_helper
import moneysurfer.feature.workspace.generated.resources.workspace_invite_email_label
import moneysurfer.feature.workspace.generated.resources.workspace_invite_error_already_invited
import moneysurfer.feature.workspace.generated.resources.workspace_invite_error_already_member
import moneysurfer.feature.workspace.generated.resources.workspace_invite_error_invalid
import moneysurfer.feature.workspace.generated.resources.workspace_invite_error_not_owner
import moneysurfer.feature.workspace.generated.resources.workspace_invite_error_unknown
import moneysurfer.feature.workspace.generated.resources.workspace_invite_error_user_not_found
import moneysurfer.feature.workspace.generated.resources.workspace_invite_role_editor_hint
import moneysurfer.feature.workspace.generated.resources.workspace_invite_role_editor_title
import moneysurfer.feature.workspace.generated.resources.workspace_invite_role_label
import moneysurfer.feature.workspace.generated.resources.workspace_invite_role_viewer_hint
import moneysurfer.feature.workspace.generated.resources.workspace_invite_role_viewer_title
import moneysurfer.feature.workspace.generated.resources.workspace_invite_send
import moneysurfer.feature.workspace.generated.resources.workspace_invite_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Stable selectors for the workspace invite screen — see docs/testing/testing-strategy.md. */
object WorkspaceInviteTestTags {
    const val Root = "workspaceInvite:root"
    const val Email = "workspaceInvite:email"
    const val Send = "workspaceInvite:send"
}

@Composable
fun WorkspaceInviteScreen(
    workspaceId: WorkspaceId,
    onNavigateBack: () -> Unit,
    viewModel: WorkspaceInviteViewModel = koinViewModel(
        key = workspaceId.value,
    ) { parametersOf(workspaceId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unknownErrorText = stringResource(Res.string.workspace_invite_error_unknown)
    val notOwnerText = stringResource(Res.string.workspace_invite_error_not_owner)

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            WorkspaceInviteEffect.Dismiss -> onNavigateBack()
            is WorkspaceInviteEffect.Sent -> onNavigateBack()
            is WorkspaceInviteEffect.ShowError -> {
                val message = when (effect.error) {
                    InviteError.NotOwner -> notOwnerText
                    else -> unknownErrorText
                }
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
    }

    InviteContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InviteContent(
    state: WorkspaceInviteState,
    snackbarHostState: SnackbarHostState,
    onEvent: (WorkspaceInviteEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .testTag(WorkspaceInviteTestTags.Root)
            .surferTestTagAsId(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.workspace_invite_title)) },
                navigationIcon = {
                    IconButton(onClick = { onEvent(WorkspaceInviteEvent.OnDismiss) }) {
                        Icon(
                            imageVector = SurferIcons.Close,
                            contentDescription = stringResource(
                                Res.string.workspace_invite_close_content_description,
                            ),
                        )
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
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.large),
        ) {
            Spacer(Modifier.height(AppTheme.spacing.small))

            EmailField(
                value = state.email,
                error = state.emailError,
                enabled = !state.isSubmitting,
                onChange = { onEvent(WorkspaceInviteEvent.OnEmailChanged(it)) },
            )

            RoleSection(
                selected = state.role,
                onSelect = { onEvent(WorkspaceInviteEvent.OnRoleSelected(it)) },
            )

            Spacer(Modifier.height(AppTheme.spacing.medium))

            SurferButton(
                text = stringResource(Res.string.workspace_invite_send),
                style = SurferButtonStyle.Filled,
                size = SurferButtonSize.Biggest,
                enabled = state.email.isNotBlank() && !state.isSubmitting,
                onClick = { onEvent(WorkspaceInviteEvent.OnSubmit) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WorkspaceInviteTestTags.Send),
            )

            Spacer(Modifier.height(padding.calculateBottomPadding() + AppTheme.spacing.large))
        }
    }
}

@Composable
private fun EmailField(
    value: String,
    error: InviteFieldError?,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    val errorText = when (error) {
        InviteFieldError.Invalid -> stringResource(Res.string.workspace_invite_error_invalid)
        InviteFieldError.AlreadyMember -> stringResource(Res.string.workspace_invite_error_already_member)
        InviteFieldError.AlreadyInvited -> stringResource(Res.string.workspace_invite_error_already_invited)
        InviteFieldError.UserNotFound -> stringResource(Res.string.workspace_invite_error_user_not_found)
        null -> null
    }
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(WorkspaceInviteTestTags.Email),
            enabled = enabled,
            isError = error != null,
            singleLine = true,
            label = { Text(stringResource(Res.string.workspace_invite_email_label)) },
            supportingText = {
                Text(
                    text = errorText ?: stringResource(Res.string.workspace_invite_email_helper),
                    color = if (error != null) {
                        AppTheme.materialColors.error
                    } else {
                        AppTheme.materialColors.onSurfaceVariant
                    },
                )
            },
        )
    }
}

@Composable
private fun RoleSection(
    selected: WorkspaceRole,
    onSelect: (WorkspaceRole) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)) {
        Text(
            text = stringResource(Res.string.workspace_invite_role_label),
            style = AppTheme.typography.labelLarge,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
        RoleCard(
            title = stringResource(Res.string.workspace_invite_role_editor_title),
            hint = stringResource(Res.string.workspace_invite_role_editor_hint),
            selected = selected == WorkspaceRole.EDITOR,
            onClick = { onSelect(WorkspaceRole.EDITOR) },
        )
        RoleCard(
            title = stringResource(Res.string.workspace_invite_role_viewer_title),
            hint = stringResource(Res.string.workspace_invite_role_viewer_hint),
            selected = selected == WorkspaceRole.VIEWER,
            onClick = { onSelect(WorkspaceRole.VIEWER) },
        )
    }
}

@Composable
private fun RoleCard(
    title: String,
    hint: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    SurferCard(
        modifier = Modifier.fillMaxWidth(),
        selected = selected,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = title, style = AppTheme.typography.titleSmall)
                Text(
                    text = hint,
                    style = AppTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────────────────

private fun previewInviteState(
    email: String = "",
    role: WorkspaceRole = WorkspaceRole.EDITOR,
    isSubmitting: Boolean = false,
    emailError: InviteFieldError? = null,
): WorkspaceInviteState = WorkspaceInviteState(
    workspaceId = WorkspaceId("preview-ws-1"),
    email = email,
    role = role,
    isSubmitting = isSubmitting,
    emailError = emailError,
)

@Preview
@Composable
private fun WorkspaceInviteScreenPreview_Empty() {
    AppTheme {
        InviteContent(
            state = previewInviteState(),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun WorkspaceInviteScreenPreview_Filled() {
    AppTheme {
        InviteContent(
            state = previewInviteState(email = "kasia.nowak@gmail.com", role = WorkspaceRole.EDITOR),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun WorkspaceInviteScreenPreview_ViewerRole() {
    AppTheme {
        InviteContent(
            state = previewInviteState(email = "tom@mail.com", role = WorkspaceRole.VIEWER),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun WorkspaceInviteScreenPreview_DuplicateError() {
    AppTheme {
        InviteContent(
            state = previewInviteState(
                email = "alex@mail.com",
                emailError = InviteFieldError.AlreadyMember,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun WorkspaceInviteScreenPreview_InvalidEmail() {
    AppTheme {
        InviteContent(
            state = previewInviteState(
                email = "not-an-email",
                emailError = InviteFieldError.Invalid,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun WorkspaceInviteScreenPreview_Submitting() {
    AppTheme {
        InviteContent(
            state = previewInviteState(
                email = "kasia.nowak@gmail.com",
                isSubmitting = true,
            ),
            snackbarHostState = remember { SnackbarHostState() },
            onEvent = {},
        )
    }
}
