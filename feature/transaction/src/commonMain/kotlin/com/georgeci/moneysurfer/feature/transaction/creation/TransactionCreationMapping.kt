package com.georgeci.moneysurfer.feature.transaction.creation

import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.CategoryAppearance
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.reference
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionType

private const val CATEGORY_PREVIEW_SIZE = 7

/**
 * Fills the blank form from an existing transaction.
 *
 * In [TransactionCreationSeed.Mode.Edit] that means the row itself, identity and timestamps
 * included. A duplicate copies only what the user typed — no id, no `createdAt`, and today's
 * date rather than the original's: it is a new transaction that merely starts out looking
 * like an old one.
 */
internal fun TransactionCreationState.Content.seededFrom(
    seed: TransactionCreationSeed,
    transaction: Transaction,
): TransactionCreationState.Content {
    val account = accounts.find { it.id == transaction.accountId }
    val category = categories.find { it.id == transaction.categoryId }
    val resolvedType = when (transaction.type) {
        TransactionType.INCOME -> TransactionTypeUi.Income
        else -> TransactionTypeUi.Expense
    }
    val resolvedSelected = category ?: selectedCategory
    val editing = seed.mode == TransactionCreationSeed.Mode.Edit
    return copy(
        amount = transaction.money.toAmountInput(),
        note = transaction.note,
        type = resolvedType,
        selectedAccount = account ?: selectedAccount,
        selectedCategory = resolvedSelected,
        timestamp = if (editing) transaction.operationAt.toEpochMilliseconds() else timestamp,
        isEditMode = editing,
        editingTransactionId = seed.transactionId.takeIf { editing },
        editingCreatedAt = transaction.createdAt.takeIf { editing },
        pinnedOperationDate = transaction.operationDate.takeIf { editing },
        editIdentity = if (editing) identityOf(transaction, category) else null,
        preserved = if (editing) {
            PreservedTransactionFields.of(transaction)
        } else {
            // A duplicate keeps what the user typed about the counterparty, but is a fresh
            // manual entry: it inherits neither the transfer pairing (a second leg would be
            // missing), the recurring rule that generated the original, nor its planned state.
            PreservedTransactionFields(merchant = transaction.merchant, tags = transaction.tags)
        },
        displayCategories = buildDisplayCategories(
            categories = categories,
            counts = categoryUsageCounts,
            type = resolvedType.categoryType(),
            selected = resolvedSelected,
        ),
    )
}

/** Categories the type's picker offers: everything but income belongs to the expense side. */
internal fun TransactionTypeUi.categoryType(): CategoryType =
    if (this == TransactionTypeUi.Income) CategoryType.INCOME else CategoryType.EXPENSE

/** Amount as the text field spells it — a whole number keeps no trailing `.0`. */
internal fun Money.toAmountInput(): String {
    val major = minor / Money.MINOR_PER_MAJOR
    return if (major == major.toLong().toDouble()) major.toLong().toString() else major.toString()
}

/**
 * The stored row, frozen for the identity band — the design's `04b · Edit transaction`.
 *
 * Read from [transaction] rather than from the live form on purpose: the band answers "which
 * transaction am I editing", so it has to keep saying what was opened even after the amount or
 * the note has been typed over. A transfer leg is labelled as a transfer and left unsigned,
 * matching the details screen.
 */
internal fun identityOf(transaction: Transaction, category: Category?): TransactionEditIdentity {
    val formatted = MoneyFormatter.format(transaction.money.abs(), transaction.currencyCode)
    return TransactionEditIdentity(
        reference = transaction.id.reference,
        type = when {
            transaction.transferId != null -> TransactionTypeUi.Transfer
            transaction.type == TransactionType.INCOME -> TransactionTypeUi.Income
            else -> TransactionTypeUi.Expense
        },
        note = transaction.note.ifBlank { transaction.merchant },
        formattedAmount = when {
            transaction.transferId != null -> formatted
            transaction.type == TransactionType.INCOME -> "+$formatted"
            transaction.type == TransactionType.EXPENSE -> "−$formatted"
            else -> formatted
        },
        categoryId = category?.id?.value.orEmpty(),
        categoryIconKey = category?.iconKey.orEmpty(),
        categoryHue = category?.hue ?: CategoryAppearance.UNSET_HUE,
        categorySystemKind = category?.systemKind?.name,
    )
}

internal fun pickDefaultCategory(
    categories: List<Category>,
    counts: Map<CategoryId, Int>,
    type: CategoryType,
): Category? = categories
    .filter { it.type == type }
    .maxByOrNull { counts[it.id] ?: 0 }

internal fun buildDisplayCategories(
    categories: List<Category>,
    counts: Map<CategoryId, Int>,
    type: CategoryType,
    selected: Category?,
): List<Category> {
    val byUsage = categories
        .filter { it.type == type }
        .sortedByDescending { counts[it.id] ?: 0 }
    val top = byUsage.take(CATEGORY_PREVIEW_SIZE)
    val resolvedSelected = selected?.takeIf { it.type == type }
    return if (resolvedSelected == null || top.any { it.id == resolvedSelected.id }) {
        top
    } else {
        top.take(CATEGORY_PREVIEW_SIZE - 1) + resolvedSelected
    }
}
