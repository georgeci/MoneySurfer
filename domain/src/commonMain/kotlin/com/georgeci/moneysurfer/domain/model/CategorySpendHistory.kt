package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.Money
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** One column of the spend trend. [total] is zero for a month the category booked nothing in. */
data class CategorySpendMonth(
    val month: YearMonth,
    val total: Money,
)

/** One row of the subcategory breakdown — a direct child, with its own subtree rolled in. */
data class CategorySpendBreakdown(
    val categoryId: CategoryId,
    val total: Money,
    val transactionCount: Int,
)

/**
 * Everything the category detail screen needs in numbers, for one category over a fixed window.
 *
 * Totals here are *rolled up*: a parent's figure includes its whole subtree, matching the
 * promise the manage screen makes ("subcategories roll up into their parent"). [subcategories]
 * is empty for a leaf, which is what drives the leaf empty state — a leaf is not an error case,
 * it is the common shape.
 */
data class CategorySpendHistory(
    val months: List<CategorySpendMonth>,
    val currentMonth: Money,
    val transactionCount: Int,
    val subcategories: List<CategorySpendBreakdown>,
) {
    /** Mean over the whole window, including months with nothing in them. */
    val monthlyAverage: Money
        get() = if (months.isEmpty()) {
            Money.zero()
        } else {
            months.fold(Money.zero()) { acc, m ->
                acc + m.total
            } / months.size
        }

    /** Mean per transaction this month, or null when nothing was booked. */
    val perTransaction: Money?
        get() = if (transactionCount <= 0) null else currentMonth / transactionCount

    companion object {
        /** Columns the trend draws. Six fits the mockup's bar row without crowding on a phone. */
        const val TREND_MONTHS: Int = 6

        val Empty = CategorySpendHistory(
            months = emptyList(),
            currentMonth = Money.zero(),
            transactionCount = 0,
            subcategories = emptyList(),
        )
    }
}

/** [count] months ending at [anchor] inclusive, oldest first. */
fun trailingMonths(anchor: YearMonth, count: Int): List<YearMonth> =
    (count - 1 downTo 0).map { anchor.minus(it, DateTimeUnit.MONTH) }

/**
 * Folds raw per-category-per-month rows into the shape the detail screen renders.
 *
 * Pure, and deliberately tolerant of a sparse [totals]: the aggregate query only returns months
 * a category actually booked something in, so every month in [months] is emitted here whether or
 * not a row backs it. Without that the trend would silently shrink to however many months had
 * spend, and a quiet category would draw a two-bar chart labelled "last 6 months".
 *
 * Rows for categories outside [rootId]'s subtree are ignored rather than trusted, so a caller
 * passing a wider query result cannot inflate the total.
 */
fun buildCategorySpendHistory(
    categories: List<Category>,
    rootId: CategoryId,
    totals: List<CategoryMonthlyTotal>,
    months: List<YearMonth>,
): CategorySpendHistory {
    val subtree = CategoryTree.descendantsOf(categories, rootId) + rootId
    val currentMonth = months.lastOrNull()

    val byCategory = totals.groupBy { it.categoryId }
    fun rollUp(ids: Set<CategoryId>, month: YearMonth?): Pair<Money, Int> {
        var total = Money.zero()
        var count = 0
        ids.forEach { id ->
            byCategory[id].orEmpty().forEach { row ->
                if (month == null || row.month == month) {
                    total += row.total
                    count += row.transactionCount
                }
            }
        }
        return total to count
    }

    val trend = months.map { month ->
        CategorySpendMonth(month = month, total = rollUp(subtree, month).first)
    }
    val (thisMonth, thisMonthCount) = rollUp(subtree, currentMonth)

    val breakdown = CategoryTree.childrenOf(categories, rootId).map { child ->
        // A child may itself have children — the mockup shows one flat row per direct child, so
        // a grandchild's spend belongs on its parent's row rather than vanishing from the split.
        val childSubtree = CategoryTree.descendantsOf(categories, child.id) + child.id
        val (total, count) = rollUp(childSubtree, currentMonth)
        CategorySpendBreakdown(categoryId = child.id, total = total, transactionCount = count)
    }

    return CategorySpendHistory(
        months = trend,
        currentMonth = thisMonth,
        transactionCount = thisMonthCount,
        subcategories = breakdown,
    )
}

/** The month after this one — the exclusive upper bound of a window ending at it. */
fun YearMonth.next(): YearMonth = plus(1, DateTimeUnit.MONTH)
