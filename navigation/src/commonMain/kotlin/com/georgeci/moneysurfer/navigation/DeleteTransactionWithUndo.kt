package com.georgeci.moneysurfer.navigation

import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.usecase.DeleteTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.RestoreTransactionsUseCase
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.Single

/**
 * Deletes a transaction and offers it back through the app-level snackbar.
 *
 * Three lists can now delete a row by swiping it — transactions, an account's history, a
 * category's history — and the delete is only half the interaction: without the Undo beside it a
 * gesture that destroys data on a single sideways drag is not one users can afford to make. Both
 * halves live here so the three ViewModels cannot drift into three different answers about what
 * a swipe destroys or how long the way back stays open.
 *
 * The Undo runs on [SnackbarController]'s app-scoped coroutine, so it still works after the user
 * navigates away from the list that produced the message.
 *
 * Each caller passes its own [message] and [undoLabel] because the wording belongs to the screen,
 * not to the delete.
 */
@Single
class DeleteTransactionWithUndo(
    private val deleteTransaction: DeleteTransactionUseCase,
    private val restoreTransactions: RestoreTransactionsUseCase,
    private val snackbar: SnackbarController,
) {

    suspend operator fun invoke(
        transactionId: TransactionId,
        message: StringResource,
        undoLabel: StringResource,
    ) {
        // Both legs of a transfer, when that is what was swiped — see DeleteTransactionUseCase.
        val deleted = deleteTransaction(transactionId)
        // Nothing was removed (the row is already gone), so there is nothing to undo and a
        // "Deleted" message would be a lie.
        if (deleted.isEmpty()) return
        snackbar.show(
            message = message,
            actionLabel = undoLabel,
            onAction = { restoreTransactions(deleted) },
        )
    }
}
