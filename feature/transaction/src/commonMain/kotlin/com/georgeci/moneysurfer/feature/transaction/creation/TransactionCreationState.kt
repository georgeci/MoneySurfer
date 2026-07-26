package com.georgeci.moneysurfer.feature.transaction.creation

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import kotlinx.datetime.LocalDate

enum class TransactionTypeUi { Expense, Income, Transfer }

@optics
sealed interface TransactionCreationState {
    data object Loading : TransactionCreationState

    @optics
    data class Content(
        val amount: String,
        val note: String,
        val type: TransactionTypeUi,
        val accounts: List<Account>,
        val categories: List<Category>,
        val selectedAccount: Account?,
        val selectedCategory: Category?,
        val isEditMode: Boolean,
        val editingTransactionId: TransactionId?,
        /** Non-null only in edit mode: the stored row behind the identity band. */
        val editIdentity: TransactionEditIdentity? = null,
        /** Whether the delete confirmation is up — the same dialog the details screen shows. */
        val showDeleteConfirmation: Boolean = false,
        val editingCreatedAt: kotlin.time.Instant? = null,
        // Original `operationDate` from the persisted transaction. Preserved across
        // edits unless the user explicitly picks a new date — otherwise a timezone
        // change between the original save and the edit would silently shift the
        // stored business date.
        val pinnedOperationDate: LocalDate? = null,
        /**
         * Fields of the transaction being edited that this form cannot change but must not
         * destroy — see [PreservedTransactionFields].
         */
        val preserved: PreservedTransactionFields = PreservedTransactionFields(),
        val timestamp: Long,
        val categoryUsageCounts: Map<CategoryId, Int>,
        val displayCategories: List<Category>,
        val fromAccount: Account? = null,
        val toAccount: Account? = null,
        val toAmount: String = "",
        val transferEnabled: Boolean = true,
    ) : TransactionCreationState {
        val isExpense: Boolean get() = type == TransactionTypeUi.Expense
        val isTransfer: Boolean get() = type == TransactionTypeUi.Transfer
        val crossCurrency: Boolean
            get() = isTransfer &&
                fromAccount != null &&
                toAccount != null &&
                fromAccount.currencyCode != toAccount.currencyCode

        /** Inline validation error for the (from) amount field, or null when it is acceptable. */
        val amountError: TransactionAmountError?
            get() = TransactionAmountInput.errorFor(amount)

        /** Inline validation error for the cross-currency "to" amount, or null when it is acceptable. */
        val toAmountError: TransactionAmountError?
            get() = if (crossCurrency) TransactionAmountInput.errorFor(toAmount) else null

        val isSaveEnabled: Boolean
            get() = if (isTransfer) {
                TransactionAmountInput.isValid(amount) &&
                    (!crossCurrency || TransactionAmountInput.isValid(toAmount)) &&
                    fromAccount != null && toAccount != null &&
                    fromAccount.id != toAccount.id
            } else {
                TransactionAmountInput.isValid(amount) &&
                    selectedAccount != null &&
                    selectedCategory != null
            }

        companion object
    }

    companion object
}
