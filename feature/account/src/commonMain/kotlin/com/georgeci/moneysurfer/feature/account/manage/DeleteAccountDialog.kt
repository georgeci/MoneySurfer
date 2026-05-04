package com.georgeci.moneysurfer.feature.account.manage

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_delete_cancel
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_delete_confirm
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_delete_message
import com.georgeci.moneysurfer.feature.account.generated.resources.accounts_manage_delete_title
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Confirmation dialog shown before permanently deleting an account. Mirrors the M3 dialog
 * shape from the Accounts.html spec — circular error-tinted Trash glyph, centered title and
 * supporting text, Cancel / Delete buttons in the bottom-trailing row.
 */
@Composable
fun DeleteAccountDialog(
    accountName: String,
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
                text = stringResource(Res.string.accounts_manage_delete_title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.accounts_manage_delete_message, accountName),
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
                Text(stringResource(Res.string.accounts_manage_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.accounts_manage_delete_cancel))
            }
        },
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
