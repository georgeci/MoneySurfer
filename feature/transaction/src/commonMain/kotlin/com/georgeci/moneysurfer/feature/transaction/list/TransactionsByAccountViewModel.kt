package com.georgeci.moneysurfer.feature.transaction.list

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.CategorizedTransaction
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.primitives.Clock
import com.georgeci.moneysurfer.domain.repositories.TransactionRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import com.georgeci.moneysurfer.domain.usecase.GetAccountByIdUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionsByAccountUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class TransactionsByAccountViewModel(
    accountId: AccountId?,
    private val getTransactionsByAccount: GetTransactionsByAccountUseCase,
    private val getAccountById: GetAccountByIdUseCase,
    private val transactionRepository: TransactionRepository,
    private val clock: Clock,
) : MviViewModel<TransactionsByAccountState, TransactionsByAccountEvent, TransactionsByAccountEffect>(
    initialState = TransactionsByAccountState.Loading(accountId = accountId),
) {

    private val typeFilter = MutableStateFlow(TransactionTypeFilter.All)

    init {
        observeData()
    }

    override fun onEvent(event: TransactionsByAccountEvent) {
        when (event) {
            TransactionsByAccountEvent.OnBackClick ->
                postSideEffect(TransactionsByAccountEffect.NavigateBack)
            TransactionsByAccountEvent.OnAddTransactionClick ->
                postSideEffect(TransactionsByAccountEffect.NavigateToTransactionCreation(currentState.accountId))
            is TransactionsByAccountEvent.OnTransactionClick ->
                postSideEffect(TransactionsByAccountEffect.NavigateToTransactionDetails(event.transactionId))
            is TransactionsByAccountEvent.OnTypeFilterChanged ->
                typeFilter.value = event.filter
        }
    }

    private fun observeData() {
        launch {
            val accountId = currentState.accountId
            val transactions = if (accountId != null) {
                getTransactionsByAccount.categorized(accountId)
            } else {
                transactionRepository.getAllCategorized()
            }
            val account = if (accountId != null) flowOf(getAccountById(accountId)) else flowOf(null)
            combine(
                transactions,
                account,
                typeFilter,
            ) { txns, acc, filter ->
                buildContent(accountId, txns, acc, filter)
            }.collect { content -> updateState { content } }
        }
    }

    private fun buildContent(
        accountId: AccountId?,
        transactions: List<CategorizedTransaction>,
        account: Account?,
        filter: TransactionTypeFilter,
    ): TransactionsByAccountState.Content {
        val visible = transactions.filter { it.transaction.type != TransactionType.OPENING_BALANCE }
        val currency = visible.firstOrNull()?.currencyCode
            ?: account?.currencyCode
            ?: CurrencyCode("USD")
        val summary = buildSummary(visible, currency)

        val filtered = when (filter) {
            TransactionTypeFilter.All -> visible
            TransactionTypeFilter.Expenses -> visible.filter { it.type == TransactionType.EXPENSE }
            TransactionTypeFilter.Income -> visible.filter { it.type == TransactionType.INCOME }
        }
        val zone = TimeZone.currentSystemDefault()
        val sorted = filtered.sortedWith(
            compareByDescending<CategorizedTransaction> { it.transaction.operationDate }
                .thenByDescending { it.operationAt }
                .thenByDescending { it.transaction.createdAt },
        )
        val groups = sorted.groupBy { it.transaction.operationDate }
            .map { (key, txns) ->
                val net = txns.fold(Money.zero()) { acc, t -> acc + t.signedMoney() }
                val groupCurrency = txns.first().currencyCode
                TransactionGroupUi(
                    dateLabel = formatDateLabel(key, zone),
                    netFormatted = MoneyFormatter.format(net, groupCurrency),
                    netPositive = !net.isNegative(),
                    transactions = txns.map { t ->
                        val transaction = t.transaction
                        val categoryName = t.categoryName
                        TransactionRowUi(
                            id = transaction.id,
                            title = transaction.note.ifBlank { categoryName ?: "Transaction" },
                            subtitle = categoryName.orEmpty(),
                            formattedAmount = MoneyFormatter.format(transaction.money.abs(), transaction.currencyCode),
                            isExpense = transaction.type == TransactionType.EXPENSE,
                            categoryHueSeed = categoryName ?: transaction.categoryId?.value.orEmpty(),
                        )
                    },
                )
            }

        return TransactionsByAccountState.Content(
            accountId = accountId,
            accountName = account?.name.orEmpty(),
            groups = groups,
            summary = summary,
            typeFilter = filter,
            isFiltered = filter != TransactionTypeFilter.All,
        )
    }

    private fun buildSummary(
        transactions: List<CategorizedTransaction>,
        currency: CurrencyCode,
    ): TransactionSummaryUi {
        var income = Money.zero()
        var expense = Money.zero()
        transactions.forEach { row ->
            val t = row.transaction
            val magnitude = t.money.abs()
            when (t.type) {
                TransactionType.INCOME -> income += magnitude
                TransactionType.EXPENSE -> expense += magnitude
                TransactionType.OPENING_BALANCE -> Unit
            }
        }
        val net = income - expense
        return TransactionSummaryUi(
            incomeFormatted = "+" + MoneyFormatter.format(income, currency),
            expenseFormatted = "−" + MoneyFormatter.format(expense, currency),
            netFormatted = (if (net.isNegative()) "−" else "+") +
                MoneyFormatter.format(net.abs(), currency),
            netPositive = !net.isNegative(),
        )
    }

    private val CategorizedTransaction.currencyCode: CurrencyCode get() = transaction.currencyCode
    private val CategorizedTransaction.operationAt: Instant get() = transaction.operationAt
    private val CategorizedTransaction.type: TransactionType get() = transaction.type

    private fun CategorizedTransaction.signedMoney(): Money = when (transaction.type) {
        TransactionType.EXPENSE -> -transaction.money.abs()
        else -> transaction.money.abs()
    }

    private fun formatDateLabel(date: LocalDate, zone: TimeZone): String {
        // Best-effort relative label without dragging in full locale-aware formatting.
        val today = clock.now().toLocalDateTime(zone).date
        val yesterday = today.toEpochDays().let { LocalDate.fromEpochDays(it - 1) }
        return when (date) {
            today -> "Today"
            yesterday -> "Yesterday"
            else -> date.toString()
        }
    }
}

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
        val summary: TransactionSummaryUi,
        val typeFilter: TransactionTypeFilter,
        val isFiltered: Boolean,
    ) : TransactionsByAccountState {
        val isEmpty: Boolean get() = groups.isEmpty()

        companion object
    }

    companion object
}

data class TransactionGroupUi(
    val dateLabel: String,
    val netFormatted: String,
    val netPositive: Boolean,
    val transactions: List<TransactionRowUi>,
)

data class TransactionRowUi(
    val id: TransactionId,
    val title: String,
    val subtitle: String,
    val formattedAmount: String,
    val isExpense: Boolean,
    val categoryHueSeed: String,
)

data class TransactionSummaryUi(
    val incomeFormatted: String,
    val expenseFormatted: String,
    val netFormatted: String,
    val netPositive: Boolean,
)

enum class TransactionTypeFilter { All, Expenses, Income }

sealed interface TransactionsByAccountEvent {
    data object OnBackClick : TransactionsByAccountEvent
    data object OnAddTransactionClick : TransactionsByAccountEvent
    data class OnTransactionClick(val transactionId: TransactionId) : TransactionsByAccountEvent
    data class OnTypeFilterChanged(val filter: TransactionTypeFilter) : TransactionsByAccountEvent
}

sealed interface TransactionsByAccountEffect {
    data object NavigateBack : TransactionsByAccountEffect
    data class NavigateToTransactionCreation(val accountId: AccountId?) : TransactionsByAccountEffect
    data class NavigateToTransactionDetails(val transactionId: TransactionId) : TransactionsByAccountEffect
}
