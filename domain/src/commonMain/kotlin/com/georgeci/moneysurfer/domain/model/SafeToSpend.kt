package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.util.BudgetPeriodWindow
import kotlinx.datetime.daysUntil

/**
 * The dashboard's one actionable number: how much is still safe to spend this period, plus the
 * pace it has to be spent at to last.
 *
 * Everything here is a projection of one [BudgetProgress] — nothing is recomputed from
 * transactions, so the widget and the budget screens can never disagree about the same budget.
 */
data class SafeToSpend(
    val budgetName: String,
    /** Negative once the budget is overspent. */
    val remaining: Money,
    /** What is left to spend per remaining day to land on the limit. Zero when nothing is left. */
    val perDay: Money,
    /** Days from today to the end of the window, counting today. Zero once the window is over. */
    val daysLeft: Int,
    val spent: Money,
    /** The budget amount plus any rollover carry — what [remaining] is measured against. */
    val limit: Money,
    val window: BudgetPeriodWindow,
    val status: BudgetStatus,
    /** Workspace base currency, or null when the workspace has none yet. */
    val currency: CurrencyCode?,
) {

    val isOver: Boolean get() = status == BudgetStatus.OVER

    /** Spend as a fraction of [limit]; can exceed 1. A zero limit reads as 0, never as a NaN. */
    val spentFraction: Float
        get() = if (limit.minor <= 0L) 0f else spent.minor.toFloat() / limit.minor.toFloat()

    /**
     * How much of the window is already behind us, today excluded. This is the tick the spend bar
     * is read against: a fill left of the tick means the money is outlasting the days, a fill right
     * of it means it is not — which is the whole point of a pace bar rather than a progress bar.
     *
     * Today is excluded because today's spending is not done yet: counting it would put the tick
     * ahead of the spend on the morning of every single day.
     */
    val elapsedFraction: Float
        get() {
            val windowDays = window.startInclusive.daysUntil(window.endExclusive)
            if (windowDays <= 0) return 1f
            return ((windowDays - daysLeft).toFloat() / windowDays).coerceIn(0f, 1f)
        }
}

/**
 * The safe-to-spend figure for a workspace, or null when no active budget backs one — the widget's
 * "set a budget" state.
 *
 * One budget speaks for the number rather than a sum across all of them. Budgets overlap by design
 * (a general cap plus a per-category one both count the same coffee), so adding their remainders
 * would double-count the spend; and they can run on different periods, which would leave "days
 * left" with nothing to mean. The pick is therefore:
 *
 * 1. general budgets — no category filter, so they cover every expense — before category ones;
 * 2. within that tier, the largest limit, which is the cap the user framed the period with;
 * 3. ties broken by budget id, so the headline does not jump between two equal budgets on
 *    recomposition.
 */
fun List<BudgetProgress>.safeToSpend(): SafeToSpend? = primaryProgress()?.toSafeToSpend()

private fun List<BudgetProgress>.primaryProgress(): BudgetProgress? {
    val active = filter { it.budget.isActive }
    val general = active.filter { it.budget.categoryIds.isEmpty() }
    return general.ifEmpty { active }
        .sortedWith(compareByDescending<BudgetProgress> { it.effectiveLimit.minor }.thenBy { it.budget.id.value })
        .firstOrNull()
}

private fun BudgetProgress.toSafeToSpend(): SafeToSpend = SafeToSpend(
    budgetName = budget.name,
    remaining = remaining,
    perDay = perDayRemaining,
    daysLeft = daysLeft,
    spent = spent,
    limit = effectiveLimit,
    window = window,
    status = status,
    currency = currency,
)
