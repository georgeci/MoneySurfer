package com.georgeci.moneysurfer.feature.transaction.details

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.usecase.ApplyTransactionChangeUseCase
import com.georgeci.moneysurfer.domain.usecase.DeleteTransactionUseCase
import com.georgeci.moneysurfer.domain.usecase.GetAccountByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionByIdUseCase
import com.georgeci.moneysurfer.navigation.SnackbarController
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.first
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_undo
import moneysurfer.feature.transaction.generated.resources.transaction_details_deleted_snackbar
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class TransactionDetailsViewModel(
    transactionId: TransactionId,
    private val getTransactionById: GetTransactionByIdUseCase,
    private val getAccountById: GetAccountByIdUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
    private val applyTransactionChange: ApplyTransactionChangeUseCase,
    private val snackbar: SnackbarController,
) : MviViewModel<TransactionDetailsState, TransactionDetailsEvent, TransactionDetailsEffect>(
    initialState = TransactionDetailsState.Loading(transactionId),
) {

    init {
        loadData()
    }

    override fun onEvent(event: TransactionDetailsEvent) {
        when (event) {
            TransactionDetailsEvent.OnBackClick ->
                postSideEffect(TransactionDetailsEffect.NavigateBack)
            TransactionDetailsEvent.OnEditClick ->
                postSideEffect(TransactionDetailsEffect.NavigateToEdit(currentState.transactionId))
            TransactionDetailsEvent.OnDeleteClick ->
                updateState {
                    TransactionDetailsState.content.showDeleteConfirmation.modify(this) { true }
                }
            TransactionDetailsEvent.OnDeleteConfirmed -> handleDelete()
            TransactionDetailsEvent.OnDeleteDismissed ->
                updateState {
                    TransactionDetailsState.content.showDeleteConfirmation.modify(this) { false }
                }
        }
    }

    private fun loadData() {
        launch {
            val transactionId = currentState.transactionId
            val transaction = getTransactionById(transactionId) ?: return@launch
            val account = getAccountById(transaction.accountId)
            val categories = getCategories().first()
            val category = categories.find { it.id == transaction.categoryId }

            updateState {
                TransactionDetailsState.Content(
                    transactionId = transactionId,
                    formattedAmount = MoneyFormatter.format(transaction.money.abs(), transaction.currencyCode),
                    isExpense = transaction.type == TransactionType.EXPENSE,
                    note = transaction.note,
                    accountName = account?.name.orEmpty(),
                    categoryName = category?.name.orEmpty(),
                    categorySystemKind = category?.systemKind?.name,
                    currency = transaction.currencyCode.value,
                    formattedDate = formatDate(transaction.operationDate),
                    isPlanned = transaction.status == TransactionStatus.PLANNED,
                    showDeleteConfirmation = false,
                )
            }
        }
    }

    private fun formatDate(date: kotlinx.datetime.LocalDate): String {
        val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
        val day = date.day.toString().padStart(2, '0')
        return "$day $month ${date.year}"
    }

    private fun handleDelete() {
        launch {
            val deleted = deleteTransaction(currentState.transactionId)
            if (deleted != null) {
                snackbar.show(
                    message = Res.string.transaction_details_deleted_snackbar,
                    actionLabel = Res.string.transaction_details_delete_undo,
                    onAction = { applyTransactionChange(old = null, new = deleted) },
                )
            }
            postSideEffect(TransactionDetailsEffect.NavigateBack)
        }
    }
}

@optics
sealed interface TransactionDetailsState {
    val transactionId: TransactionId

    @optics
    data class Loading(override val transactionId: TransactionId) : TransactionDetailsState {
        companion object
    }

    @optics
    data class Content(
        override val transactionId: TransactionId,
        val formattedAmount: String,
        val isExpense: Boolean,
        val note: String,
        val accountName: String,
        val categoryName: String,
        val categorySystemKind: String? = null,
        val currency: String,
        val formattedDate: String,
        val isPlanned: Boolean,
        val showDeleteConfirmation: Boolean,
    ) : TransactionDetailsState {
        companion object
    }

    companion object
}

sealed interface TransactionDetailsEvent {
    data object OnBackClick : TransactionDetailsEvent
    data object OnEditClick : TransactionDetailsEvent
    data object OnDeleteClick : TransactionDetailsEvent
    data object OnDeleteConfirmed : TransactionDetailsEvent
    data object OnDeleteDismissed : TransactionDetailsEvent
}

sealed interface TransactionDetailsEffect {
    data object NavigateBack : TransactionDetailsEffect
    data class NavigateToEdit(val transactionId: TransactionId) : TransactionDetailsEffect
}
