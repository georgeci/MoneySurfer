package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionStatus
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.domain.util.BudgetPeriodWindow
import com.georgeci.moneysurfer.domain.util.previousWindow
import com.georgeci.moneysurfer.domain.util.windowContaining
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/** Traffic light for a budget, derived from spend against the effective limit. */
enum class BudgetStatus {
    OK,
    WARN,
    OVER,
}

/**
 * Everything the budget screens show for one budget in one period window. Derived on read —
 * nothing here is persisted, so a change to the maths never needs a migration.
 */
data class BudgetProgress(
    val budget: Budget,
    val window: BudgetPeriodWindow,
    val spent: Money,
    /** [Budget.amount] plus the rollover carry, when rollover is on and the last period ended under. */
    val effectiveLimit: Money,
    val rolloverCarry: Money,
    /** Negative once the budget is overspent. */
    val remaining: Money,
    /** Spend as a percentage of [effectiveLimit]; can exceed 100. */
    val percent: Int,
    val status: BudgetStatus,
    /** Days from `today` to the end of the window, counting today. Zero once the window is over. */
    val daysLeft: Int,
    val dailyAverage: Money,
    /** [dailyAverage] extrapolated across the whole period. */
    val projectedTotal: Money,
    /** What is left to spend per remaining day to land on the limit. Zero when nothing is left. */
    val perDayRemaining: Money,
    /**
     * Whether the window holds expenses in a currency other than the workspace base currency.
     * Those are skipped — v1 does no conversion — so the UI flags the number as incomplete.
     */
    val hasMixedCurrency: Boolean,
    /** Workspace base currency, carried along so callers can format without a second lookup. */
    val currency: CurrencyCode?,
)

private const val PERCENT = 100
private const val WEEKLY_DAYS = 7
private const val MONTHLY_DAYS = 30
private const val YEARLY_DAYS = 365

/** Nominal period length used for the forecast maths, per the mockup. */
val BudgetPeriod.forecastDays: Int
    get() = when (this) {
        BudgetPeriod.WEEKLY -> WEEKLY_DAYS
        BudgetPeriod.MONTHLY -> MONTHLY_DAYS
        BudgetPeriod.YEARLY -> YEARLY_DAYS
    }

/**
 * Whether [transaction] counts against this budget in [window].
 *
 * Expenses only, and never a leg of a transfer: moving money between the user's own accounts
 * is not spending. Only the workspace [baseCurrency] counts — v1 converts nothing, and a
 * skipped foreign-currency expense is surfaced through [BudgetProgress.hasMixedCurrency]
 * instead of being silently added at face value.
 */
fun Budget.counts(
    transaction: Transaction,
    window: BudgetPeriodWindow,
    baseCurrency: CurrencyCode?,
): Boolean = countsIgnoringCurrency(transaction, window) && transaction.currencyCode == baseCurrency

/** Same as [counts] but ignoring the currency — the input to the mixed-currency flag. */
private fun Budget.countsIgnoringCurrency(
    transaction: Transaction,
    window: BudgetPeriodWindow,
): Boolean =
    transaction.workspaceId == workspaceId &&
        transaction.type == TransactionType.EXPENSE &&
        transaction.status == TransactionStatus.ACTUAL &&
        transaction.transferId == null &&
        transaction.operationDate in window &&
        matchesCategory(transaction)

/** An empty [Budget.categoryIds] is a general budget: every expense category counts. */
private fun Budget.matchesCategory(transaction: Transaction): Boolean =
    categoryIds.isEmpty() || transaction.categoryId in categoryIds

/** The transactions behind [BudgetProgress.spent], newest business date first. */
fun Budget.transactionsIn(
    transactions: List<Transaction>,
    window: BudgetPeriodWindow,
    baseCurrency: CurrencyCode?,
): List<Transaction> =
    transactions.filter { counts(it, window, baseCurrency) }
        .sortedByDescending { it.operationDate }

/**
 * Pure progress maths for one budget. [transactions] is the whole workspace list — filtering
 * happens here so callers cannot forget the transfer or currency rules.
 *
 * A rollover carry is the previous window's leftover, computed by the same filter. It is one
 * level deep on purpose: chaining every window back to `startDate` would make the cost of
 * opening the list grow with the age of the budget, for a number the user stopped tracking
 * periods ago.
 */
fun calculateBudgetProgress(
    budget: Budget,
    transactions: List<Transaction>,
    baseCurrency: CurrencyCode?,
    today: LocalDate,
): BudgetProgress {
    val window = budget.windowContaining(today)
    val spent = budget.spendIn(transactions, window, baseCurrency)
    val carry = budget.rolloverCarry(transactions, window, baseCurrency)
    val effectiveLimit = budget.amount + carry
    val remaining = effectiveLimit - spent

    val daysLeft = today.daysUntil(window.endExclusive).coerceAtLeast(0)
    val elapsedDays = window.startInclusive.daysUntil(today).coerceAtLeast(0) + 1
    val dailyAverage = spent / elapsedDays

    return BudgetProgress(
        budget = budget,
        window = window,
        spent = spent,
        effectiveLimit = effectiveLimit,
        rolloverCarry = carry,
        remaining = remaining,
        percent = percentOf(spent, effectiveLimit),
        status = budgetStatusOf(spent, effectiveLimit, budget.alertPercent),
        daysLeft = daysLeft,
        dailyAverage = dailyAverage,
        projectedTotal = dailyAverage * budget.period.forecastDays,
        perDayRemaining = if (daysLeft > 0 && remaining.isPositive()) remaining / daysLeft else Money.zero(),
        hasMixedCurrency = transactions.any {
            budget.countsIgnoringCurrency(it, window) && it.currencyCode != baseCurrency
        },
        currency = baseCurrency,
    )
}

private fun Budget.spendIn(
    transactions: List<Transaction>,
    window: BudgetPeriodWindow,
    baseCurrency: CurrencyCode?,
): Money = transactions.fold(Money.zero()) { acc, transaction ->
    if (counts(transaction, window, baseCurrency)) acc + transaction.money.abs() else acc
}

private fun Budget.rolloverCarry(
    transactions: List<Transaction>,
    window: BudgetPeriodWindow,
    baseCurrency: CurrencyCode?,
): Money {
    if (!rollover) return Money.zero()
    val previous = previousWindow(window)
    val leftover = amount - spendIn(transactions, previous, baseCurrency)
    return if (leftover.isPositive()) leftover else Money.zero()
}

/** Percentage of [limit] that [spent] represents. A zero limit reads as 0 %, never as a crash. */
private fun percentOf(spent: Money, limit: Money): Int =
    if (limit.minor <= 0L) 0 else (spent.minor * PERCENT / limit.minor).toInt()

/**
 * The traffic light for [spent] against [limit], with [alertPercent] as the near-limit threshold.
 *
 * Public because [calculateBudgetProgress] is not the only place spend meets a cap: the dashboard's
 * per-category breakdown ([buildSpentByCategory]) measures a category against its own budget
 * without folding the transaction list, and a second set of thresholds there would let the same
 * budget read WARN on one screen and OK on another.
 */
fun budgetStatusOf(spent: Money, limit: Money, alertPercent: Int): BudgetStatus {
    val percent = percentOf(spent, limit)
    return when {
        spent > limit -> BudgetStatus.OVER
        percent >= alertPercent -> BudgetStatus.WARN
        else -> BudgetStatus.OK
    }
}
