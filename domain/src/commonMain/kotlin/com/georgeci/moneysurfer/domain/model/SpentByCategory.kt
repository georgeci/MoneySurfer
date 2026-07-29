package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.util.TransactionPeriodWindow

/**
 * The cap a single-category budget puts on one category, as the spend breakdown reads it.
 *
 * [limit] is the budget's nominal amount rather than a `BudgetProgress.effectiveLimit`: computing
 * the effective one means folding the whole workspace transaction list per budget, which is the
 * pattern [com.georgeci.moneysurfer.domain.repositories.SpendAnalyticsRepository] exists to
 * replace. A rollover carry is therefore not in [limit] — see [buildSpentByCategory] for
 * what else that costs.
 */
data class CategoryCap(
    val limit: Money,
    val status: BudgetStatus,
)

/**
 * One category's spend inside the breakdown's window.
 *
 * [category] is null for the uncategorized bucket — a real slice rather than a missing one, so the
 * entries still add up to what was spent. Naming it is the UI's job: `domain` owns no copy.
 */
data class CategorySpend(
    val category: Category?,
    val spent: Money,
    /** Share of the window's total spend, `0f..1f`. Zero when nothing was spent at all. */
    val share: Float,
    /** The cap on this category, or null while nothing caps it. */
    val cap: CategoryCap?,
) {

    /**
     * Spend against [CategoryCap.limit]; can exceed 1, which every meter caps rather than
     * overdrawing. Null when there is no cap — a meter then falls back to [share].
     */
    val capFraction: Float?
        get() = cap?.let { if (it.limit.minor <= 0L) 0f else spent.minor.toFloat() / it.limit.minor.toFloat() }
}

/**
 * Where a period's money went, category by category — the dashboard's spent-by-category widget.
 *
 * [currency] is non-null by construction: a figure nobody can format is not a figure, and
 * `MoneyFormatter` is backed by `java.util.Currency`, which throws on anything that is not a
 * three-letter ISO code. A workspace whose base currency cannot be read yields no breakdown at
 * all rather than one the UI has to invent a currency for — the same rule [SafeToSpend] follows.
 */
data class SpentByCategory(
    /** Largest spender first. */
    val entries: List<CategorySpend>,
    val total: Money,
    val currency: CurrencyCode,
    val window: TransactionPeriodWindow,
)

/**
 * Joins the window's per-category spend to the categories that name it and the budgets that cap it.
 *
 * Pure, so the maths is testable without a repository: [slices] is what
 * [com.georgeci.moneysurfer.domain.repositories.SpendAnalyticsRepository.byCategory] answered for
 * the same window, and [categories] and [budgets] are the workspace's own.
 *
 * **Which budget caps a category.** Only a budget naming *exactly* that one category does —
 * resolved through [resolveCategoryCapCoverage], the same rule the category form's monthly-cap
 * shortcut writes through, so the two can never disagree about which budget speaks for a category.
 * A budget spanning several categories caps the group, not any single member, and overlaying its
 * limit on one member's spend would read as headroom the group does not have. The general
 * (all-categories) budget is the spending envelope above per-category caps, and
 * [resolveCategoryCapCoverage] already excludes both it and archived budgets.
 *
 * **Only caps on [capPeriod]'s cadence count.** [slices] covers [window], so a budget of any other
 * cadence is not a figure for it: measuring a week of spend against a monthly cap reads as
 * comfortable headroom right up to the point the month is blown. Dividing one into a pro-rata limit
 * would invent a cap the user never set, so a category whose only budget runs on another cadence
 * simply shows no overlay — the spend is still drawn, without a state it cannot honestly claim.
 *
 * **The cap's own window is not this one.** Budget windows are anchored on `Budget.startDate`, so a
 * cap created mid-month runs 15th-to-15th while this breakdown runs a calendar span. Every number
 * *here* is measured over [window] — spend, limit and status alike, so the widget is internally
 * consistent — but a status shown here can differ from the same budget's status on the Budgets
 * screen, which reads the budget's own window and includes its rollover carry.
 */
fun buildSpentByCategory(
    slices: List<CategorySpendSlice>,
    categories: List<Category>,
    budgets: List<Budget>,
    currency: CurrencyCode,
    window: TransactionPeriodWindow,
    capPeriod: BudgetPeriod,
): SpentByCategory {
    val total = slices.fold(Money.zero()) { acc, slice -> acc + slice.total }
    val byId = categories.associateBy { it.id }
    // Sorted here rather than trusted from the query: `sortedByDescending` is stable, so the
    // repository's own largest-first order survives for ties, and a caller that forgot to sort
    // cannot leave the widget drawing its rows in an arbitrary order.
    val entries = slices
        .sortedByDescending { it.total.minor }
        .map { slice ->
            CategorySpend(
                category = slice.categoryId?.let(byId::get),
                spent = slice.total,
                share = shareOf(slice.total, total),
                cap = capFor(slice, budgets, capPeriod),
            )
        }
    return SpentByCategory(entries = entries, total = total, currency = currency, window = window)
}

/** A zero total reads as no share at all, never as a NaN. */
private fun shareOf(spent: Money, total: Money): Float =
    if (total.minor <= 0L) 0f else (spent.minor.toFloat() / total.minor.toFloat()).coerceIn(0f, 1f)

private fun capFor(
    slice: CategorySpendSlice,
    budgets: List<Budget>,
    capPeriod: BudgetPeriod,
): CategoryCap? {
    val coverage = resolveCategoryCapCoverage(slice.categoryId, budgets)
    val budget = (coverage as? CategoryCapCoverage.Editable)?.budget ?: return null
    if (budget.period != capPeriod) return null
    return CategoryCap(
        limit = budget.amount,
        status = budgetStatusOf(slice.total, budget.amount, budget.alertPercent),
    )
}
