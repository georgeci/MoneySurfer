package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.RecurringRuleId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.TransferId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import kotlinx.datetime.LocalDate
import kotlin.time.Instant

/**
 * `operationAt` is the absolute moment of the operation (sortable). `operationDate` is
 * the business date the user assigned the transaction to — what the budget treats it as.
 * Storing both explicitly avoids re-deriving the date at every read site (timezone
 * boundaries can flip the day) and keeps backdating intent explicit (`operationDate`
 * stays as picked even if the user enters at midnight in a different zone).
 */
data class Transaction(
    val id: TransactionId,
    val workspaceId: WorkspaceId,
    val accountId: AccountId,
    val money: Money,
    val currencyCode: CurrencyCode,
    val categoryId: CategoryId?,
    val note: String,
    /**
     * Who the money went to or came from ("Starbucks"), kept separate from [note] so the
     * details screen can show both and search can weight them independently. Empty means
     * "not recorded" — there is no null state to distinguish.
     */
    val merchant: String = "",
    /**
     * Free-form labels, normalized through [TransactionTags.normalize] before it gets here.
     * Order is the user's; duplicates and commas are already gone.
     */
    val tags: List<String> = emptyList(),
    /** Absolute moment. Sorting and audit only — never re-derive a calendar day from it. */
    val operationAt: Instant,
    /** Business date. What budgets count against and what SQL date windows filter on. */
    val operationDate: LocalDate,
    val type: TransactionType,
    val status: TransactionStatus = TransactionStatus.ACTUAL,
    // Required: technical timestamps — they intentionally differ from operationAt for
    // backdated entries. createdAt is the local-row creation moment, NOT the operation
    // moment. Aliasing them silently breaks (operationDate, operationAt, createdAt)
    // sort and audit semantics.
    val createdAt: Instant,
    val updatedAt: Instant,
    val transferId: TransferId? = null,
    /**
     * The rule that generated this row, or null for a manually entered one. A link rather
     * than an `isRecurring` boolean: the schedule already lives on [RecurringRule], and a
     * flag would be a second copy of that fact free to drift from it. "Recurring only"
     * filters on `recurringRuleId != null`; the details screen resolves the rule to show
     * its cadence.
     *
     * Deliberately not a Room foreign key — `recurring_rules` is not synced yet, so a pulled
     * transaction can legitimately arrive before (or without) the rule it names.
     */
    val recurringRuleId: RecurringRuleId? = null,
)
