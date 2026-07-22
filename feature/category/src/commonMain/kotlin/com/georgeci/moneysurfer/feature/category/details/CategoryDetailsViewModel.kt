package com.georgeci.moneysurfer.feature.category.details

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.model.CategorySpendHistory
import com.georgeci.moneysurfer.domain.model.Transaction
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.usecase.GetCategoriesUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCategorySpendHistoryUseCase
import com.georgeci.moneysurfer.domain.usecase.GetTransactionsByCategoryUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.Month
import kotlinx.datetime.YearMonth
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class CategoryDetailsViewModel(
    categoryId: CategoryId,
    private val getCategories: GetCategoriesUseCase,
    private val getCategorySpendHistory: GetCategorySpendHistoryUseCase,
    private val getTransactionsByCategory: GetTransactionsByCategoryUseCase,
) : MviViewModel<CategoryDetailsState, CategoryDetailsEvent, CategoryDetailsEffect>(
    initialState = CategoryDetailsState.Loading(categoryId),
) {

    init {
        observe()
    }

    override fun onEvent(event: CategoryDetailsEvent) {
        when (event) {
            CategoryDetailsEvent.OnBackClick -> postSideEffect(CategoryDetailsEffect.NavigateBack)
            CategoryDetailsEvent.OnEditClick ->
                postSideEffect(CategoryDetailsEffect.NavigateToCategoryEdit(currentState.categoryId))
            CategoryDetailsEvent.OnAddTransactionClick ->
                postSideEffect(CategoryDetailsEffect.NavigateToTransactionCreation)
            CategoryDetailsEvent.OnManageSubcategoriesClick ->
                postSideEffect(CategoryDetailsEffect.NavigateToCategoriesManage)
            is CategoryDetailsEvent.OnSubcategoryClick ->
                postSideEffect(CategoryDetailsEffect.NavigateToCategoryDetails(event.categoryId))
            is CategoryDetailsEvent.OnTransactionClick ->
                postSideEffect(CategoryDetailsEffect.NavigateToTransactionDetails(event.transactionId))
        }
    }

    private fun observe() {
        val categoryId = currentState.categoryId
        launch {
            combine(
                getCategories(),
                getCategorySpendHistory(categoryId),
                getTransactionsByCategory(categoryId),
            ) { categories, history, transactions ->
                Triple(categories, history, transactions)
            }.collect { (categories, history, transactions) ->
                val category = categories.firstOrNull { it.id == categoryId }
                // The category can vanish under an open screen — a delete on this device, or a
                // sync pull from another. Falling back to Missing gives the screen something
                // honest to render instead of a half-populated page about nothing.
                if (category == null) {
                    updateState { CategoryDetailsState.Missing(categoryId) }
                    return@collect
                }
                val content = buildContent(category, categories, history, transactions)
                updateState { content }
            }
        }
    }

    private fun buildContent(
        category: Category,
        categories: List<Category>,
        history: CategorySpendHistory,
        transactions: List<Transaction>,
    ): CategoryDetailsState.Content {
        // Currency follows the transactions actually booked here; the workspace's own currency
        // is the fallback for a category that has none yet.
        val currency = transactions.firstOrNull()?.currencyCode ?: DefaultCurrency
        val byId = categories.associateBy { it.id }
        val parentTotal = history.currentMonth

        return CategoryDetailsState.Content(
            categoryId = category.id,
            name = category.name,
            type = category.type,
            iconKey = category.iconKey,
            hue = category.hue,
            systemKind = category.systemKind?.name,
            isLeaf = history.subcategories.isEmpty(),
            formattedTotal = MoneyFormatter.format(parentTotal, currency),
            formattedAverage = MoneyFormatter.format(history.monthlyAverage, currency),
            formattedPerTransaction = history.perTransaction?.let {
                MoneyFormatter.format(it, currency)
            },
            transactionCount = history.transactionCount,
            months = history.months.map { month ->
                CategoryTrendMonthUi(
                    month = month.month,
                    minorValue = month.total.minor,
                    formattedValue = MoneyFormatter.format(month.total, currency),
                )
            },
            subcategories = history.subcategories.map { child ->
                CategorySubcategoryUi(
                    id = child.categoryId,
                    name = byId[child.categoryId]?.name.orEmpty(),
                    iconKey = byId[child.categoryId]?.iconKey.orEmpty(),
                    hue = byId[child.categoryId]?.hue ?: UnsetHue,
                    formattedAmount = MoneyFormatter.format(child.total, currency),
                    share = shareOf(child.total, parentTotal),
                )
            },
            transactions = transactions.map { it.toUi(currency) },
        )
    }

    private fun Transaction.toUi(currency: CurrencyCode) = CategoryTransactionUi(
        id = id,
        title = note,
        formattedAmount = MoneyFormatter.format(money.abs(), currency),
        isExpense = type == TransactionType.EXPENSE,
        categoryHueSeed = categoryId?.value.orEmpty(),
    )

    private companion object {
        val DefaultCurrency = CurrencyCode("USD")

        /** Matches `CategoryAppearance.UNSET_HUE` — the palette resolves it to the id-hashed default. */
        const val UnsetHue = -1

        /**
         * A child's slice of the parent. Zero when the parent booked nothing, rather than a
         * division by zero — with no total there is no share to draw.
         */
        fun shareOf(part: Money, whole: Money): Float =
            if (whole.minor <= 0L) 0f else (part.minor.toFloat() / whole.minor).coerceIn(0f, 1f)
    }
}

@optics
sealed interface CategoryDetailsState {
    val categoryId: CategoryId

    @optics
    data class Loading(override val categoryId: CategoryId) : CategoryDetailsState {
        companion object
    }

    /** The category is gone — deleted here or removed by a sync pull while the screen was open. */
    @optics
    data class Missing(override val categoryId: CategoryId) : CategoryDetailsState {
        companion object
    }

    @optics
    data class Content(
        override val categoryId: CategoryId,
        val name: String,
        val type: CategoryType,
        val iconKey: String,
        val hue: Int,
        val systemKind: String?,
        val isLeaf: Boolean,
        val formattedTotal: String,
        val formattedAverage: String,
        val formattedPerTransaction: String?,
        val transactionCount: Int,
        val months: List<CategoryTrendMonthUi>,
        val subcategories: List<CategorySubcategoryUi>,
        val transactions: List<CategoryTransactionUi>,
    ) : CategoryDetailsState {
        companion object
    }

    companion object
}

/** One trend column. [minorValue] stays raw so the bar can scale against its siblings. */
data class CategoryTrendMonthUi(
    val month: YearMonth,
    val minorValue: Long,
    val formattedValue: String,
) {
    val monthOfYear: Month get() = month.month
}

data class CategorySubcategoryUi(
    val id: CategoryId,
    val name: String,
    val iconKey: String,
    val hue: Int,
    val formattedAmount: String,
    val share: Float,
)

data class CategoryTransactionUi(
    val id: TransactionId,
    val title: String,
    val formattedAmount: String,
    val isExpense: Boolean,
    val categoryHueSeed: String,
)

sealed interface CategoryDetailsEvent {
    data object OnBackClick : CategoryDetailsEvent
    data object OnEditClick : CategoryDetailsEvent
    data object OnAddTransactionClick : CategoryDetailsEvent
    data object OnManageSubcategoriesClick : CategoryDetailsEvent
    data class OnSubcategoryClick(val categoryId: CategoryId) : CategoryDetailsEvent
    data class OnTransactionClick(val transactionId: TransactionId) : CategoryDetailsEvent
}

sealed interface CategoryDetailsEffect {
    data object NavigateBack : CategoryDetailsEffect
    data object NavigateToTransactionCreation : CategoryDetailsEffect
    data object NavigateToCategoriesManage : CategoryDetailsEffect
    data class NavigateToCategoryEdit(val categoryId: CategoryId) : CategoryDetailsEffect
    data class NavigateToCategoryDetails(val categoryId: CategoryId) : CategoryDetailsEffect
    data class NavigateToTransactionDetails(val transactionId: TransactionId) : CategoryDetailsEffect
}
