package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.Money

/**
 * One receipt: either an ordinary transaction on its own, or the legs it was split across.
 *
 * A group is what the transaction list, the recent-activity widget and search results render as a
 * single row — a payment the user made once should be one line, whatever number of categories it
 * was filed under. Everything that reasons about categories (budgets, monthly totals, spend
 * history, CSV) keeps working on the legs and never sees this type.
 */
data class TransactionSplitGroup(val legs: List<Transaction>) {

    init {
        require(legs.isNotEmpty()) { "A split group needs at least one leg" }
    }

    /** The row that stands for the group: its id is what opening the group navigates to. */
    val primary: Transaction get() = legs.first()

    val isSplit: Boolean get() = legs.size > 1

    /** Magnitude of the whole receipt — the legs share a currency, so a plain sum is sound. */
    val total: Money = legs.fold(Money.zero()) { acc, leg -> acc + leg.money.abs() }

    /**
     * Distinct categories the receipt was filed under. Legs left uncategorized count once between
     * them, which is what the "N categories" badge should say for a half-filled split.
     */
    val categoryCount: Int = legs.map { it.categoryId }.distinct().size
}

/**
 * Collapses split legs into groups, preserving the order the rows arrived in: a group takes the
 * position of its topmost leg, so a list sorted newest-first stays sorted.
 *
 * Legs are matched across the whole list rather than only between neighbours — two legs of one
 * receipt sort adjacently in practice but nothing guarantees another row cannot land between them.
 */
fun List<Transaction>.groupSplitLegs(): List<TransactionSplitGroup> {
    val bySplit = filter { it.splitId != null }.groupBy { it.splitId }
    val seen = mutableSetOf<Any>()
    return mapNotNull { transaction ->
        val splitId = transaction.splitId
            ?: return@mapNotNull TransactionSplitGroup(listOf(transaction))
        if (!seen.add(splitId)) return@mapNotNull null
        TransactionSplitGroup(bySplit.getValue(splitId))
    }
}
