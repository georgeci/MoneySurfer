package com.georgeci.moneysurfer.feature.account.manage

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archive_cancel
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archive_confirm
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archive_message
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_archive_title
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun ArchiveAccountDialog(
    accountName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = SurferIcons.Archive,
                contentDescription = SurferSemantics.Decorative,
                tint = AppTheme.materialColors.primary,
            )
        },
        title = {
            Text(
                text = stringResource(Res.string.accounts_manage_archive_title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.accounts_manage_archive_message, accountName),
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringResource(Res.string.accounts_manage_archive_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.accounts_manage_archive_cancel))
            }
        },
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
