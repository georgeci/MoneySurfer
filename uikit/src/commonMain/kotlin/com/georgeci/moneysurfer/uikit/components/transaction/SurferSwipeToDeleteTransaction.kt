package com.georgeci.moneysurfer.uikit.components.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.base.SurferSwipeAccessibilityAction
import com.georgeci.moneysurfer.uikit.components.base.SurferSwipeAction
import com.georgeci.moneysurfer.uikit.components.base.SurferSwipeRevealRow
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.uikit.generated.resources.Res
import moneysurfer.uikit.generated.resources.uikit_transaction_delete
import org.jetbrains.compose.resources.stringResource

/**
 * Total swipe travel: one 72dp action button plus the reveal row's 6dp gutters. Narrower than the
 * manage screens' two-action track, because a transaction row reveals only Delete.
 */
private val REVEAL_WIDTH = 84.dp

/**
 * A transaction row that can be swiped left to delete, with the deletion routed through a
 * confirmation dialog and (via the caller) an Undo snackbar.
 *
 * Two details that are easy to get wrong and are handled here rather than at each call site:
 *
 * - The row is filled with the surface colour. [SurferTransactionLine] draws no background of its
 *   own, so without this the Delete button underneath shows straight through the text as the row
 *   slides.
 * - The gesture is also published as a custom accessibility action, since a horizontal drag is not
 *   something a screen reader user can perform. That is the non-gesture equivalent — no extra
 *   visible affordance is added for it.
 *
 * The confirmation is kept even though an Undo follows it: deleting is confirmed everywhere else
 * in the app, and a swipe is easy enough to trigger by accident while scrolling that the dialog is
 * also what tells a mis-swipe apart from an intent.
 *
 * @param transactionTitle the row's title, shown in the dialog so the user can see which row is
 *   about to go; blank falls back to the dialog's generic wording.
 * @param isTransfer whether the row is one leg of a transfer, which both legs of are deleted
 *   together — the dialog says so.
 * @param isSplit whether the row stands for a receipt split across categories, every leg of which
 *   is deleted together — the dialog says so.
 */
@Composable
fun SurferSwipeToDeleteTransaction(
    transactionTitle: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    isTransfer: Boolean = false,
    isSplit: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Survives configuration changes and process death: the dialog is a question already put to
    // the user, and re-asking it (or worse, dropping it) on rotation loses their place.
    var showConfirmation by rememberSaveable { mutableStateOf(false) }
    val deleteLabel = stringResource(Res.string.uikit_transaction_delete)

    SurferSwipeRevealRow(
        revealWidth = REVEAL_WIDTH,
        modifier = modifier,
        accessibilityActions = listOf(
            SurferSwipeAccessibilityAction(label = deleteLabel) { showConfirmation = true },
        ),
        actions = {
            SurferSwipeAction(
                icon = SurferIcons.Delete,
                label = deleteLabel,
                onClick = { showConfirmation = true },
                destructive = true,
            )
        },
    ) {
        Box(modifier = Modifier.background(AppTheme.materialColors.surface)) {
            content()
        }
    }

    if (showConfirmation) {
        SurferDeleteTransactionDialog(
            titleOrNull = transactionTitle.takeIf { it.isNotBlank() },
            onConfirm = {
                showConfirmation = false
                onDelete()
            },
            onDismiss = { showConfirmation = false },
            isTransfer = isTransfer,
            isSplit = isSplit,
        )
    }
}
