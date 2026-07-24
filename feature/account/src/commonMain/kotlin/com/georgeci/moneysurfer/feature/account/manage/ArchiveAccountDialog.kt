package com.georgeci.moneysurfer.feature.account.manage

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archive_cancel
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archive_confirm
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archive_message
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archive_title
import com.georgeci.moneysurfer.uikit.components.SurferConfirmDialog
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import org.jetbrains.compose.resources.stringResource

/** Confirmation shown before archiving an account. Reversible, so it is not styled destructive. */
@Composable
fun ArchiveAccountDialog(
    accountName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    SurferConfirmDialog(
        title = stringResource(Res.string.accounts_manage_archive_title),
        message = stringResource(Res.string.accounts_manage_archive_message, accountName),
        confirmLabel = stringResource(Res.string.accounts_manage_archive_confirm),
        cancelLabel = stringResource(Res.string.accounts_manage_archive_cancel),
        icon = SurferIcons.Archive,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Preview
@Composable
private fun ArchiveAccountDialogPreview() {
    AppTheme {
        ArchiveAccountDialog(
            accountName = "Everyday",
            onConfirm = {},
            onDismiss = {},
        )
    }
}
