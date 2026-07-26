package com.georgeci.moneysurfer.feature.transaction.list

import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.TransactionId

sealed interface TransactionsByAccountEvent {
    data object OnBackClick : TransactionsByAccountEvent
    data object OnAddTransactionClick : TransactionsByAccountEvent
    data class OnTransactionClick(val transactionId: TransactionId) : TransactionsByAccountEvent
    data class OnSearchQueryChanged(val query: String) : TransactionsByAccountEvent
    data object OnOpenFiltersClick : TransactionsByAccountEvent

    /** The empty state's CTA when nothing matched: drops every filter *and* the search text. */
    data object OnClearFiltersClick : TransactionsByAccountEvent
    data object OnPreviousPeriodClick : TransactionsByAccountEvent
    data object OnNextPeriodClick : TransactionsByAccountEvent
    data class OnPeriodModeChanged(val mode: TransactionPeriodMode) : TransactionsByAccountEvent
    data object OnLoadMore : TransactionsByAccountEvent
}

sealed interface TransactionsByAccountEffect {
    data object NavigateBack : TransactionsByAccountEffect
    data class NavigateToTransactionCreation(val accountId: AccountId?) : TransactionsByAccountEffect
    data class NavigateToTransactionDetails(val transactionId: TransactionId) : TransactionsByAccountEffect
    data class NavigateToFilters(
        val accountId: AccountId?,
        val anchorEpochDay: Long,
    ) : TransactionsByAccountEffect
}
