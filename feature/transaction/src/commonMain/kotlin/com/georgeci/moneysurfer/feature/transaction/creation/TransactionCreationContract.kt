package com.georgeci.moneysurfer.feature.transaction.creation

import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType

sealed interface TransactionCreationEvent {
    data class OnAmountChanged(val amount: String) : TransactionCreationEvent
    data class OnToAmountChanged(val amount: String) : TransactionCreationEvent
    data class OnNoteChanged(val note: String) : TransactionCreationEvent
    data class OnAccountSelected(val account: Account) : TransactionCreationEvent
    data class OnCategorySelected(val category: Category) : TransactionCreationEvent
    data class OnCategoryPicked(val id: CategoryId) : TransactionCreationEvent
    data class OnAccountPicked(val id: AccountId) : TransactionCreationEvent
    data class OnTypeChanged(val type: TransactionTypeUi) : TransactionCreationEvent
    data class OnDateChanged(val timestamp: Long) : TransactionCreationEvent
    data object OnTodayClick : TransactionCreationEvent
    data object OnOpenCategoryChooser : TransactionCreationEvent
    data object OnOpenCategoryCreation : TransactionCreationEvent
    data object OnOpenAccountChooser : TransactionCreationEvent
    data object OnOpenFromAccountChooser : TransactionCreationEvent
    data object OnOpenToAccountChooser : TransactionCreationEvent
    data object OnSwapAccountsClick : TransactionCreationEvent
    data object OnSaveClick : TransactionCreationEvent
    data object OnDeleteClick : TransactionCreationEvent
    data object OnDeleteConfirmed : TransactionCreationEvent
    data object OnDeleteDismissed : TransactionCreationEvent
    data object OnBackClick : TransactionCreationEvent
}

sealed interface TransactionCreationEffect {
    data object NavigateBack : TransactionCreationEffect

    /**
     * The edited transaction is gone, so the screen that opened this one — the details of that very
     * row — must not be returned to. Distinct from [NavigateBack] because only the caller knows how
     * far back that is.
     */
    data object NavigateBackAfterDelete : TransactionCreationEffect
    data class NavigateToCategoryChooser(
        val selectedCategoryId: CategoryId?,
        val filterType: CategoryType,
    ) : TransactionCreationEffect
    data object NavigateToCategoryCreation : TransactionCreationEffect
    data class NavigateToAccountChooser(
        val selectedAccountId: AccountId?,
        val excludeAccountId: AccountId? = null,
        val showTransferShortcut: Boolean = false,
    ) : TransactionCreationEffect
}

internal enum class AccountSlot { Single, From, To }
