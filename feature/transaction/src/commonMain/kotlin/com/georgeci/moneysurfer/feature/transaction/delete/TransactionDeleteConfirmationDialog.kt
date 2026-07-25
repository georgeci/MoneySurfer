package com.georgeci.moneysurfer.feature.transaction.delete

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextAlign
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_cancel
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_confirm
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_message
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_message_generic
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_title
import org.jetbrains.compose.resources.stringResource

/**
 * The one "delete this transaction?" dialog, shared by the details screen and by edit mode on the
 * creation screen. Deleting is the same act from either place, so a second dialog with its own
 * wording (or its own idea of what Cancel does) would be two answers to one question.
 *
 * The strings keep their `transaction_details_*` names: they are the copy this dialog has always
 * shown, and renaming them would only churn both translations.
 *
 * @param noteOrNull the transaction's note, quoted back so the user can see which row is meant;
 *   null falls back to the generic wording rather than quoting an empty string.
 */
@Composable
internal fun TransactionDeleteConfirmationDialog(
    noteOrNull: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
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
                text = stringResource(Res.string.transaction_details_delete_title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            val msg = if (noteOrNull != null) {
                stringResource(Res.string.transaction_details_delete_message, noteOrNull)
            } else {
                stringResource(Res.string.transaction_details_delete_message_generic)
            }
            Text(text = msg, textAlign = TextAlign.Center)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.materialColors.error,
                    contentColor = AppTheme.materialColors.onError,
                ),
            ) {
                Text(stringResource(Res.string.transaction_details_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.transaction_details_delete_cancel))
            }
        },
    )
}
