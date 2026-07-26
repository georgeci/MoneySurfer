package com.georgeci.moneysurfer.uikit.components.transaction

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.uikit.generated.resources.Res
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_cancel
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_confirm
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_message
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_message_generic
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_title
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_transfer_message
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_transfer_message_generic
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_transfer_title
import org.jetbrains.compose.resources.stringResource

/**
 * The one "delete this transaction?" dialog. Deleting is the same act wherever it is started —
 * the details screen, edit mode on the creation screen, or a swipe on any of the three
 * transaction lists — so a second dialog with its own wording (or its own idea of what Cancel
 * does) would be two answers to one question.
 *
 * It lives in uikit rather than in the transaction feature because those lists span three feature
 * modules, and uikit is the only place all three can see.
 *
 * @param titleOrNull the row's title, quoted back so the user can see which one is meant; null
 *   falls back to the generic wording rather than quoting an empty string.
 * @param isTransfer whether the row is one leg of a transfer, which changes the copy: a transfer
 *   is deleted whole, so the dialog has to say the money disappears from *both* accounts before
 *   the user agrees to it.
 */
@Composable
fun SurferDeleteTransactionDialog(
    titleOrNull: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isTransfer: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = SurferIcons.Delete,
                contentDescription = SurferSemantics.Decorative,
                tint = AppTheme.materialColors.error,
            )
        },
        title = {
            Text(
                text = stringResource(
                    if (isTransfer) {
                        Res.string.uikit_transaction_delete_transfer_title
                    } else {
                        Res.string.uikit_transaction_delete_title
                    },
                ),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(text = deleteMessage(titleOrNull, isTransfer), textAlign = TextAlign.Center)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.materialColors.error,
                    contentColor = AppTheme.materialColors.onError,
                ),
            ) {
                Text(stringResource(Res.string.uikit_transaction_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.uikit_transaction_delete_cancel))
            }
        },
    )
}

/** Four variants of one sentence: with or without a title to quote, transfer or not. */
@Composable
private fun deleteMessage(titleOrNull: String?, isTransfer: Boolean): String = when {
    titleOrNull != null && isTransfer ->
        stringResource(Res.string.uikit_transaction_delete_transfer_message, titleOrNull)
    titleOrNull != null -> stringResource(Res.string.uikit_transaction_delete_message, titleOrNull)
    isTransfer -> stringResource(Res.string.uikit_transaction_delete_transfer_message_generic)
    else -> stringResource(Res.string.uikit_transaction_delete_message_generic)
}

@Preview
@Composable
private fun SurferDeleteTransactionDialogPreview() {
    SurferComponentPreview {
        SurferDeleteTransactionDialog(
            titleOrNull = "Starbucks",
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun SurferDeleteTransactionDialogTransferPreview() {
    SurferComponentPreview {
        SurferDeleteTransactionDialog(
            titleOrNull = "Savings top-up",
            onConfirm = {},
            onDismiss = {},
            isTransfer = true,
        )
    }
}
