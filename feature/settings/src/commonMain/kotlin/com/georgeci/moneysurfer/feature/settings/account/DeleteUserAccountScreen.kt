package com.georgeci.moneysurfer.feature.settings.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.feature.settings.components.SettingsSubScreenScaffold
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.settings.generated.resources.Res
import moneysurfer.feature.settings.generated.resources.settings_delete_account_button
import moneysurfer.feature.settings.generated.resources.settings_delete_account_confirm_cancel
import moneysurfer.feature.settings.generated.resources.settings_delete_account_confirm_confirm
import moneysurfer.feature.settings.generated.resources.settings_delete_account_confirm_message
import moneysurfer.feature.settings.generated.resources.settings_delete_account_confirm_title
import moneysurfer.feature.settings.generated.resources.settings_delete_account_deleted_memberships
import moneysurfer.feature.settings.generated.resources.settings_delete_account_deleted_section
import moneysurfer.feature.settings.generated.resources.settings_delete_account_deleted_sync
import moneysurfer.feature.settings.generated.resources.settings_delete_account_deleted_workspaces
import moneysurfer.feature.settings.generated.resources.settings_delete_account_intro
import moneysurfer.feature.settings.generated.resources.settings_delete_account_kept_local
import moneysurfer.feature.settings.generated.resources.settings_delete_account_kept_section
import moneysurfer.feature.settings.generated.resources.settings_delete_account_kept_shared
import moneysurfer.feature.settings.generated.resources.settings_delete_account_notice_auth_failed
import moneysurfer.feature.settings.generated.resources.settings_delete_account_notice_cleanup_failed
import moneysurfer.feature.settings.generated.resources.settings_delete_account_notice_generic
import moneysurfer.feature.settings.generated.resources.settings_delete_account_notice_recent_login
import moneysurfer.feature.settings.generated.resources.settings_delete_account_notice_wrong_password
import moneysurfer.feature.settings.generated.resources.settings_delete_account_password_label
import moneysurfer.feature.settings.generated.resources.settings_delete_account_password_missing
import moneysurfer.feature.settings.generated.resources.settings_delete_account_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stable selectors — see docs/testing/testing-strategy.md. */
object DeleteUserAccountTestTags {
    const val PasswordField = "deleteAccount:passwordField"
    const val DeleteButton = "deleteAccount:deleteButton"
}

@Composable
fun DeleteUserAccountScreen(
    onNavigateBack: () -> Unit,
    viewModel: DeleteUserAccountViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingNotice by remember { mutableStateOf<DeleteUserAccountNotice?>(null) }

    val pendingNoticeText: String? = pendingNotice?.let { noticeText(it) }
    LaunchedEffect(pendingNoticeText) {
        val text = pendingNoticeText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = text, duration = SnackbarDuration.Short)
        pendingNotice = null
    }

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            DeleteUserAccountEffect.NavigateBack -> onNavigateBack()
            is DeleteUserAccountEffect.Notify -> pendingNotice = effect.notice
        }
    }

    DeleteUserAccountContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun noticeText(notice: DeleteUserAccountNotice): String = when (notice) {
    DeleteUserAccountNotice.WrongPassword ->
        stringResource(Res.string.settings_delete_account_notice_wrong_password)
    DeleteUserAccountNotice.RequiresRecentLogin ->
        stringResource(Res.string.settings_delete_account_notice_recent_login)
    DeleteUserAccountNotice.CleanupFailed ->
        stringResource(Res.string.settings_delete_account_notice_cleanup_failed)
    DeleteUserAccountNotice.AuthDeletionFailed ->
        stringResource(Res.string.settings_delete_account_notice_auth_failed)
    DeleteUserAccountNotice.Generic ->
        stringResource(Res.string.settings_delete_account_notice_generic)
}

@Composable
private fun DeleteUserAccountContent(
    state: DeleteUserAccountState,
    snackbarHostState: SnackbarHostState,
    onEvent: (DeleteUserAccountEvent) -> Unit,
) {
    if (state.showConfirmDialog) {
        ConfirmDeleteUserAccountDialog(
            onConfirm = { onEvent(DeleteUserAccountEvent.OnConfirmAccepted) },
            onDismiss = { onEvent(DeleteUserAccountEvent.OnConfirmDismissed) },
        )
    }

    SettingsSubScreenScaffold(
        title = stringResource(Res.string.settings_delete_account_title),
        snackbarHostState = snackbarHostState,
        onBack = { onEvent(DeleteUserAccountEvent.OnBackClick) },
        showProgressOverlay = state.isDeleting,
    ) { padding ->
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(Res.string.settings_delete_account_intro),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.materialColors.onSurfaceVariant,
            )

            SectionTitle(stringResource(Res.string.settings_delete_account_deleted_section))
            BulletLine(stringResource(Res.string.settings_delete_account_deleted_workspaces))
            BulletLine(stringResource(Res.string.settings_delete_account_deleted_memberships))
            BulletLine(stringResource(Res.string.settings_delete_account_deleted_sync))

            SectionTitle(stringResource(Res.string.settings_delete_account_kept_section))
            BulletLine(stringResource(Res.string.settings_delete_account_kept_shared))
            BulletLine(stringResource(Res.string.settings_delete_account_kept_local))

            if (state.showPasswordField) {
                Spacer(Modifier.height(AppTheme.spacing.small))
                OutlinedTextField(
                    value = state.password,
                    onValueChange = { onEvent(DeleteUserAccountEvent.OnPasswordChange(it)) },
                    label = { Text(stringResource(Res.string.settings_delete_account_password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = state.passwordMissing,
                    supportingText = {
                        if (state.passwordMissing) {
                            Text(stringResource(Res.string.settings_delete_account_password_missing))
                        }
                    },
                    enabled = !state.isDeleting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(DeleteUserAccountTestTags.PasswordField),
                )
            }

            Spacer(Modifier.height(AppTheme.spacing.small))
            Button(
                onClick = { onEvent(DeleteUserAccountEvent.OnDeleteClick) },
                enabled = !state.isDeleting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.materialColors.error,
                    contentColor = AppTheme.materialColors.onError,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(DeleteUserAccountTestTags.DeleteButton),
            ) {
                Text(stringResource(Res.string.settings_delete_account_button))
            }
            Spacer(Modifier.height(padding.calculateBottomPadding() + AppTheme.spacing.large))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = AppTheme.typography.titleSmall,
        color = AppTheme.materialColors.onSurface,
        modifier = Modifier.padding(top = AppTheme.spacing.small),
    )
}

@Composable
private fun BulletLine(text: String) {
    Row {
        Text(
            text = "•",
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
    }
}

/** Final confirmation — mirrors the M3 dialog shape used by the financial-account delete. */
@Composable
private fun ConfirmDeleteUserAccountDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = SurferIcons.Delete,
                contentDescription = null,
                tint = AppTheme.materialColors.error,
            )
        },
        title = {
            Text(
                text = stringResource(Res.string.settings_delete_account_confirm_title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.settings_delete_account_confirm_message),
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.materialColors.error,
                    contentColor = AppTheme.materialColors.onError,
                ),
            ) {
                Text(stringResource(Res.string.settings_delete_account_confirm_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_delete_account_confirm_cancel))
            }
        },
    )
}

@Preview
@Composable
private fun DeleteUserAccountScreenPreview() {
    AppTheme {
        DeleteUserAccountContent(
            state = DeleteUserAccountState(userEmail = "kasia@georgeci.com"),
            snackbarHostState = SnackbarHostState(),
            onEvent = {},
        )
    }
}
