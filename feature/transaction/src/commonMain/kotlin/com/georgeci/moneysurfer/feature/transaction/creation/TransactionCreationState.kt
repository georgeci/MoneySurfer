package com.georgeci.moneysurfer.feature.transaction.creation

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
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
        /**
         * Lines of the split editor, or empty when the form is entering an ordinary single-category
         * transaction. Non-empty is what "the user asked to split this" means — there is no separate
         * flag that could disagree with the lines.
         */
        val splitLines: List<TransactionSplitLineUi> = emptyList(),
    ) : TransactionCreationState {
        val isExpense: Boolean get() = type == TransactionTypeUi.Expense
        val isTransfer: Boolean get() = type == TransactionTypeUi.Transfer

        /** Whether the form is entering one receipt across several categories. */
        val isSplit: Boolean get() = splitLines.isNotEmpty()

        /**
         * Whether the row being edited is one leg of a stored receipt. Distinct from [isSplit],
         * which is about the editor being open: an edit never opens it, but deleting from here
         * still takes the whole group, and the confirmation has to say so.
         */
        val isEditingSplitLeg: Boolean get() = isEditMode && preserved.splitId != null

        /**
         * The receipt total: what the amount hero holds, which is also what the legs must add up to.
         * Unparseable input is zero here — the amount field flags it on its own.
         */
        val splitTotal: Money
            get() = Money.fromDouble(TransactionAmountInput.parse(amount) ?: 0.0).abs()

        /**
         * What is left for the last line after every line above it. May be negative, which is the
         * "over the amount" state the editor reports and Save refuses.
         */
        val splitRemainder: Money
            get() = splitLines.dropLast(1)
                .fold(splitTotal) { left, line -> left - line.enteredMoney() }

        /**
         * Every line's money, in line order, with the last line holding the remainder — the legs
         * a save would write. Only meaningful once [isSplitComplete] holds.
         */
        val splitAmounts: List<Money>
            get() = splitLines.dropLast(1).map { it.enteredMoney() } + listOfNotNull(
                splitRemainder.takeIf { splitLines.isNotEmpty() },
            )

        /**
         * Whether the lines describe a receipt that can be written: at least two of them, each
         * filed under a category and carrying a positive amount. The remainder line is what makes
         * the last condition non-trivial — assign more than the total above it and it goes negative.
         */
        val isSplitComplete: Boolean
            get() = splitLines.size >= MIN_SPLIT_LINES &&
                splitLines.all { it.category != null } &&
                splitAmounts.all { it.isPositive() }
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
            get() = when {
                isTransfer ->
                    TransactionAmountInput.isValid(amount) &&
                        (!crossCurrency || TransactionAmountInput.isValid(toAmount)) &&
                        fromAccount != null && toAccount != null &&
                        fromAccount.id != toAccount.id
                isSplit ->
                    TransactionAmountInput.isValid(amount) &&
                        selectedAccount != null &&
                        isSplitComplete
                else ->
                    TransactionAmountInput.isValid(amount) &&
                        selectedAccount != null &&
                        selectedCategory != null
            }

        companion object
    }

    companion object
}

/** Fewer lines than this is an ordinary transaction, not a split. */
const val MIN_SPLIT_LINES: Int = 2

/**
 * The line's typed amount as money, zero for anything unparseable — the editor shows what is left
 * to assign rather than an inline error per line, so a half-typed figure simply counts as nothing
 * yet.
 */
private fun TransactionSplitLineUi.enteredMoney(): Money =
    Money.fromDouble(TransactionAmountInput.parse(amount) ?: 0.0).abs()
