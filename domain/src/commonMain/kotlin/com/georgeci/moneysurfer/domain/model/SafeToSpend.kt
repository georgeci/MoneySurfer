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
    /**
     * Workspace base currency. Non-null by construction: a figure nobody can format is not a
     * figure, so [safeToSpend] withholds the whole projection rather than handing the UI an
     * amount it has to invent a currency for.
     */
    val currency: CurrencyCode,
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
 * 2. within that tier, a budget whose cadence is [preferredPeriod] — the span the screen is being
 *    read at — before one on any other;
 * 3. then the largest limit, which is the cap the user framed the period with;
 * 4. ties broken by budget id, so the headline does not jump between two equal budgets on
 *    recomposition.
 *
 * [preferredPeriod] is a tiebreak rather than a filter, and it sits *below* the general/category
 * tier on purpose. A budget owns its own window, so a screen set to Week cannot reshape a monthly
 * cap into a weekly one — all it can do is let a weekly budget speak where one exists. Demoting a
 * €2,000 general cap behind a €20 weekly coffee budget would answer "what is safe to spend" with
 * a number about coffee; passing null asks for the cap alone, which is what a caller with no
 * period on screen wants.
 *
 * Also null when the winning progress carries no base currency — the workspace row is missing
 * behind budgets that reference it, which a pull can produce for a moment. Money the app cannot
 * name is worse than no money at all: `MoneyFormatter` is backed by `java.util.Currency`, which
 * rejects anything that is not a three-letter ISO code.
 */
fun List<BudgetProgress>.safeToSpend(preferredPeriod: BudgetPeriod? = null): SafeToSpend? =
    primaryProgress(preferredPeriod)?.toSafeToSpend()

private fun List<BudgetProgress>.primaryProgress(preferredPeriod: BudgetPeriod?): BudgetProgress? {
    val active = filter { it.budget.isActive }
    val general = active.filter { it.budget.categoryIds.isEmpty() }
    return general.ifEmpty { active }
        .sortedWith(
            // `preferredPeriod == null` makes every term of this comparison false, leaving the
            // limit and the id to decide — the exact order this list had before periods existed.
            compareByDescending<BudgetProgress> { it.budget.period == preferredPeriod }
                .thenByDescending { it.effectiveLimit.minor }
                .thenBy { it.budget.id.value },
        )
        .firstOrNull()
}

private fun BudgetProgress.toSafeToSpend(): SafeToSpend? {
    val baseCurrency = currency ?: return null
    return SafeToSpend(
        budgetName = budget.name,
        remaining = remaining,
        perDay = perDayRemaining,
        daysLeft = daysLeft,
        spent = spent,
        limit = effectiveLimit,
        window = window,
        status = status,
        currency = baseCurrency,
    )
}
