package com.georgeci.moneysurfer.feature.transaction.details

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategoryAppearance
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.model.reference
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.usecase.GetAccountByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransferCounterpartUseCase
import com.georgeci.moneysurfer.navigation.DeleteTransactionWithUndo
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.first
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_undo
import moneysurfer.feature.transaction.generated.resources.transaction_details_deleted_snackbar
import org.koin.core.annotation.KoinViewModel

// ViewModel resolves the transaction plus everything the three screen variants need around it.
@KoinViewModel
@Suppress("LongParameterList")
class TransactionDetailsViewModel(
    transactionId: TransactionId,
    private val getTransactionById: GetTransactionByIdUseCase,
    private val getAccountById: GetAccountByIdUseCase,
    private val getCategories: GetCategoriesUseCase,
    private val getTransferCounterpart: GetTransferCounterpartUseCase,
    private val deleteWithUndo: DeleteTransactionWithUndo,
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
            TransactionDetailsEvent.OnDuplicateClick ->
                postSideEffect(TransactionDetailsEffect.NavigateToDuplicate(currentState.transactionId))
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
            val parentCategory = category?.parentId?.let { parentId -> categories.find { it.id == parentId } }
            val transfer = resolveTransferLeg(transaction, account)

            updateState {
                TransactionDetailsState.Content(
                    transactionId = transactionId,
                    formattedAmount = formatAmount(transaction, isTransfer = transfer != null),
                    type = transaction.type,
                    transfer = transfer,
                    note = transaction.note,
                    merchant = transaction.merchant,
                    accountName = account?.name.orEmpty(),
                    categoryName = category?.name.orEmpty(),
                    parentCategoryName = parentCategory?.name,
                    categorySystemKind = category?.systemKind?.name,
                    categoryId = category?.id?.value.orEmpty(),
                    categoryIconKey = category?.iconKey.orEmpty(),
                    categoryHue = category?.hue ?: CategoryAppearance.UNSET_HUE,
                    tags = transaction.tags,
                    reference = transactionId.reference,
                    formattedDate = formatDate(transaction.operationDate),
                    isPlanned = transaction.status == TransactionStatus.PLANNED,
                    showDeleteConfirmation = false,
                )
            }
        }
    }

    /**
     * Null unless [transaction] is one leg of a transfer. Neither leg stores the other account,
     * so the sibling has to be resolved through the shared `transferId` — and the pair may be
     * missing, in which case the screen still renders as a transfer with one side blank rather
     * than silently degrading to an expense.
     */
    private suspend fun resolveTransferLeg(transaction: Transaction, account: Account?): TransferLeg? {
        if (transaction.transferId == null) return null
        val counterpartName = getTransferCounterpart(transaction)
            ?.let { getAccountById(it.accountId) }
            ?.name
            .orEmpty()
        val ownName = account?.name.orEmpty()
        return if (transaction.type == TransactionType.INCOME) {
            TransferLeg(fromAccountName = counterpartName, toAccountName = ownName)
        } else {
            TransferLeg(fromAccountName = ownName, toAccountName = counterpartName)
        }
    }

    /**
     * Transfers stay unsigned — money moved sideways, it neither arrived nor left. Only the
     * income/expense variants carry a sign, matching the transaction list.
     */
    private fun formatAmount(transaction: Transaction, isTransfer: Boolean): String {
        val formatted = MoneyFormatter.format(transaction.money.abs(), transaction.currencyCode)
        if (isTransfer) return formatted
        return when (transaction.type) {
            TransactionType.INCOME -> "+$formatted"
            TransactionType.EXPENSE -> "−$formatted"
            TransactionType.OPENING_BALANCE -> formatted
        }
    }

    private fun formatDate(date: kotlinx.datetime.LocalDate): String {
        val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
        val day = date.day.toString().padStart(2, '0')
        return "$day $month ${date.year}"
    }

    private fun handleDelete() {
        launch {
            deleteWithUndo(
                transactionId = currentState.transactionId,
                message = Res.string.transaction_details_deleted_snackbar,
                undoLabel = Res.string.transaction_details_delete_undo,
            )
            postSideEffect(TransactionDetailsEffect.NavigateBack)
        }
    }
}

/**
 * The two accounts a transfer moves money between, already oriented for display: [fromAccountName]
 * is always the source regardless of which leg is on screen. Either name is blank when the account
 * (or the paired leg) could not be resolved.
 */
data class TransferLeg(
    val fromAccountName: String,
    val toAccountName: String,
)

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
        /**
         * The stored type. A transfer is not a type of its own — both its legs are an ordinary
         * `EXPENSE`/`INCOME` pair — so [transfer] is what tells the transfer variant apart.
         */
        val type: TransactionType,
        /** Non-null when this transaction is one leg of a transfer. */
        val transfer: TransferLeg? = null,
        val note: String,
        val merchant: String = "",
        val accountName: String,
        val categoryName: String,
        /** Parent of [categoryName] when the category is nested, else null. */
        val parentCategoryName: String? = null,
        val categorySystemKind: String? = null,
        /** Stored appearance of the transaction's category, fed to the shared bubble resolver. */
        val categoryId: String = "",
        val categoryIconKey: String = "",
        val categoryHue: Int = CategoryAppearance.UNSET_HUE,
        val tags: List<String> = emptyList(),
        /** Derived short label (`TX-8A13`), see [com.georgeci.moneysurfer.domain.model.reference]. */
        val reference: String = "",
        val formattedDate: String,
        val isPlanned: Boolean,
        val showDeleteConfirmation: Boolean,
    ) : TransactionDetailsState {

        val isTransfer: Boolean get() = transfer != null

        val isIncome: Boolean get() = !isTransfer && type == TransactionType.INCOME

        /**
         * Duplicating one leg of a transfer would produce a half-transfer, and an opening balance
         * is an account artefact rather than something a user re-enters — neither offers the action.
         */
        val canDuplicate: Boolean
            get() = !isTransfer && type != TransactionType.OPENING_BALANCE

        companion object
    }

    companion object
}

sealed interface TransactionDetailsEvent {
    data object OnBackClick : TransactionDetailsEvent
    data object OnEditClick : TransactionDetailsEvent
    data object OnDuplicateClick : TransactionDetailsEvent
    data object OnDeleteClick : TransactionDetailsEvent
    data object OnDeleteConfirmed : TransactionDetailsEvent
    data object OnDeleteDismissed : TransactionDetailsEvent
}

sealed interface TransactionDetailsEffect {
    data object NavigateBack : TransactionDetailsEffect
    data class NavigateToEdit(val transactionId: TransactionId) : TransactionDetailsEffect

    /** Open the creation screen prefilled from [transactionId], as a brand-new transaction. */
    data class NavigateToDuplicate(val transactionId: TransactionId) : TransactionDetailsEffect
}
