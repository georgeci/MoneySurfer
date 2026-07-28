package com.georgeci.moneysurfer.uikit.components.transaction

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.uikit.generated.resources.Res
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_cancel
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_confirm
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_message
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_message_generic
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_split_message
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_split_message_generic
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_split_title
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_title
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_transfer_message
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_transfer_message_generic
import moneysurfer.uikit.generated.resources.uikit_transaction_delete_transfer_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Stable selectors for the shared delete dialog — see docs/testing/testing-strategy.md.
 *
 * The value keeps its `transactionDetails:` prefix even though the dialog is no longer owned by
 * that screen: it is the id two Maestro flows already tap (`08`, `17`), and renaming it would
 * break them for nothing. The prefix names where the flows first meet this dialog, not where the
 * composable lives.
 */
object SurferDeleteTransactionDialogTestTags {
    const val Confirm = "transactionDetails:confirmDelete"
}

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
 * @param isSplit whether the row stands for a receipt split across categories, for the same
 *   reason: the delete takes every leg of it, so a user who swiped what looks like one row has to
 *   be told several transactions are about to go. Ignored when [isTransfer] is set — a transfer leg
 *   is never split, and a caller passing both is naming a state the app cannot produce.
 */
@Composable
fun SurferDeleteTransactionDialog(
    titleOrNull: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isTransfer: Boolean = false,
    isSplit: Boolean = false,
) {
    val variant = DeleteVariant.of(isTransfer = isTransfer, isSplit = isSplit)
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
                text = stringResource(variant.title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(text = deleteMessage(titleOrNull, variant), textAlign = TextAlign.Center)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                // The dialog is its own window, so the hosting screen's `surferTestTagAsId()`
                // does not reach in here — it has to be applied again alongside the tag.
                modifier = Modifier
                    .surferTestTagAsId()
                    .testTag(SurferDeleteTransactionDialogTestTags.Confirm),
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

/**
 * What is actually being deleted, which is what the copy has to say. Two of the three take more
 * than the row the user pointed at with them, and that is the whole reason the wording differs.
 */
private enum class DeleteVariant(
    val title: StringResource,
    val message: StringResource,
    val genericMessage: StringResource,
) {
    Single(
        title = Res.string.uikit_transaction_delete_title,
        message = Res.string.uikit_transaction_delete_message,
        genericMessage = Res.string.uikit_transaction_delete_message_generic,
    ),
    Transfer(
        title = Res.string.uikit_transaction_delete_transfer_title,
        message = Res.string.uikit_transaction_delete_transfer_message,
        genericMessage = Res.string.uikit_transaction_delete_transfer_message_generic,
    ),
    Split(
        title = Res.string.uikit_transaction_delete_split_title,
        message = Res.string.uikit_transaction_delete_split_message,
        genericMessage = Res.string.uikit_transaction_delete_split_message_generic,
    ),
    ;

    companion object {
        fun of(isTransfer: Boolean, isSplit: Boolean): DeleteVariant = when {
            isTransfer -> Transfer
            isSplit -> Split
            else -> Single
        }
    }
}

/** One sentence, quoting the row's title when there is one to quote. */
@Composable
private fun deleteMessage(titleOrNull: String?, variant: DeleteVariant): String =
    if (titleOrNull != null) {
        stringResource(variant.message, titleOrNull)
    } else {
        stringResource(variant.genericMessage)
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
private fun SurferDeleteTransactionDialogSplitPreview() {
    SurferComponentPreview {
        SurferDeleteTransactionDialog(
            titleOrNull = "Pyaterochka",
            onConfirm = {},
            onDismiss = {},
            isSplit = true,
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
