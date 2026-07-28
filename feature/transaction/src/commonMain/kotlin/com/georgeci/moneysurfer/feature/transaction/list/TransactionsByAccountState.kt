package com.georgeci.moneysurfer.feature.transaction.list

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.primitives.AccountId

@optics
sealed interface TransactionsByAccountState {
    val accountId: AccountId?

    @optics
    data class Loading(override val accountId: AccountId?) : TransactionsByAccountState {
        companion object
    }

    @optics
    data class Content(
        override val accountId: AccountId?,
        val accountName: String,
        val groups: List<TransactionGroupUi>,
        /** Whether a row should name the account it belongs to — true on the all-accounts list. */
        val showAccountOnRows: Boolean,
        val summary: TransactionSummaryUi,
        val query: String,
        val filters: TransactionFilterChipsUi,
        val activeFilterCount: Int,
        val isFiltered: Boolean,
        val periodMode: TransactionPeriodMode,
        val period: TransactionPeriodUi,
        val showPeriodPager: Boolean,
        val canGoToPreviousPeriod: Boolean,
        val canGoToNextPeriod: Boolean,
        val canLoadMore: Boolean,
    ) : TransactionsByAccountState {
        val isEmpty: Boolean get() = groups.isEmpty()

        /** Rows actually shown, across every day group — what the LazyColumn can scroll. */
        val renderedRowCount: Int get() = groups.sumOf { it.transactions.size }

        companion object
    }

    companion object
}
