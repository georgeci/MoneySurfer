package com.georgeci.moneysurfer.feature.account.manage

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_delete_cancel
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_delete_confirm
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_delete_message
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_delete_title
import com.georgeci.moneysurfer.uikit.components.SurferConfirmDialog
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import org.jetbrains.compose.resources.stringResource

/** Confirmation shown before permanently deleting an account. */
@Composable
fun DeleteAccountDialog(
    accountName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    SurferConfirmDialog(
        title = stringResource(Res.string.accounts_manage_delete_title),
        message = stringResource(Res.string.accounts_manage_delete_message, accountName),
        confirmLabel = stringResource(Res.string.accounts_manage_delete_confirm),
        cancelLabel = stringResource(Res.string.accounts_manage_delete_cancel),
        icon = SurferIcons.Delete,
        destructive = true,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Preview
@Composable
private fun DeleteAccountDialogPreview() {
    AppTheme {
        DeleteAccountDialog(
            accountName = "Everyday",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
