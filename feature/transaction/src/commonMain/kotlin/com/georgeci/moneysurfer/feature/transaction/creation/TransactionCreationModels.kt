package com.georgeci.moneysurfer.feature.transaction.creation

import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransferId

/**
 * Everything a stored transaction carries that this screen has no field for.
 *
 * `saveTransaction` builds a whole new [Transaction] instead of patching the stored row, so a
 * field that is not routed back through here is silently reset to its default on update — which
 * is how editing used to wipe a transaction's merchant and tags, downgrade a `PLANNED` row to
 * `ACTUAL`, and orphan the other leg of a transfer by dropping its `transferId`.
 *
 * The defaults are what a brand-new transaction should have, so a blank form needs no special case.
 */
data class PreservedTransactionFields(
    val merchant: String = "",
    val tags: List<String> = emptyList(),
    val status: TransactionStatus = TransactionStatus.ACTUAL,
    val transferId: TransferId? = null,
    val recurringRuleId: RecurringRuleId? = null,
) {
    companion object {
        /** Everything [transaction] holds outside the form — what an edit must hand back untouched. */
        fun of(transaction: Transaction): PreservedTransactionFields = PreservedTransactionFields(
            merchant = transaction.merchant,
            tags = transaction.tags,
            status = transaction.status,
            transferId = transaction.transferId,
            recurringRuleId = transaction.recurringRuleId,
        )
    }
}

/**
 * The existing transaction the creation screen opens on, and what it is there for.
 *
 * One nullable parameter rather than an id plus a flag: the screen loads exactly one transaction
 * in either mode, and two independent parameters could contradict each other (a duplicate flag
 * with no id, an id with no mode).
 */
data class TransactionCreationSeed(
    val transactionId: TransactionId,
    val mode: Mode,
) {
    enum class Mode {
        /** Update the transaction in place. */
        Edit,

        /** Use it as a template for a brand-new transaction. */
        Duplicate,
    }
}

/**
 * The transaction being edited, as it was stored — what the edit screen's identity band renders so
 * the user can tell which row they opened without scrolling or going back.
 *
 * A snapshot, not a view of the form: it is taken once when the transaction is loaded and never
 * follows the fields the user is editing.
 */
data class TransactionEditIdentity(
    /** Short human-readable id, e.g. `TX-8213`. */
    val reference: String,
    val type: TransactionTypeUi,
    /** The note, falling back to the merchant when there is none; may still be blank. */
    val note: String,
    /** Signed for income and expense, unsigned for a transfer leg. */
    val formattedAmount: String,
    /** The category's stored appearance, fed to the shared bubble resolver. */
    val categoryId: String,
    val categoryIconKey: String,
    val categoryHue: Int,
    val categorySystemKind: String?,
)
