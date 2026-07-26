package com.georgeci.moneysurfer.feature.transaction.list

import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionDateRange
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionSort
import com.georgeci.moneysurfer.feature.transaction.filter.TransactionTypeFilter
import kotlinx.datetime.LocalDate

data class TransactionGroupUi(
    val date: LocalDate,
    val dateLabel: TransactionDateUi,
    val netFormatted: String,
    val netPositive: Boolean,
    val transactions: List<TransactionRowUi>,
)

/** Day-header label, resolved to text by the screen. */
sealed interface TransactionDateUi {
    data object Today : TransactionDateUi
    data object Yesterday : TransactionDateUi
    data class Exact(val date: LocalDate) : TransactionDateUi
}

/** Pager label, resolved to text by the screen — see the `PeriodPager` design component. */
sealed interface TransactionPeriodUi {
    data class Month(val monthNumber: Int, val year: Int) : TransactionPeriodUi
    data class Week(
        val from: LocalDate,
        val to: LocalDate,
        val weekNumber: Int,
        val weekYear: Int,
    ) : TransactionPeriodUi

    data object AllTime : TransactionPeriodUi
}

/**
 * The filter chip rail's contents. Every chip is a shortcut into the filter screen, so these carry
 * only what the chip label shows — a name when exactly one thing is picked, a count otherwise.
 */
data class TransactionFilterChipsUi(
    val dateRange: TransactionDateRange,
    val type: TransactionTypeFilter,
    val accountCount: Int,
    val accountName: String?,
    val categoryCount: Int,
    val categoryName: String?,
    val sort: TransactionSort,
)

data class TransactionRowUi(
    val id: TransactionId,
    val title: String,
    val subtitle: String,
    val formattedAmount: String,
    val isExpense: Boolean,
    val categoryHueSeed: String,
    /** Name of the owning account, rendered only where the list mixes several — may be blank. */
    val accountName: String = "",
    /** One leg of a transfer: money moved sideways, drawn neutral with a swap glyph. */
    val isTransfer: Boolean = false,
)

data class TransactionSummaryUi(
    val incomeFormatted: String,
    val expenseFormatted: String,
    val netFormatted: String,
    val netPositive: Boolean,
)
