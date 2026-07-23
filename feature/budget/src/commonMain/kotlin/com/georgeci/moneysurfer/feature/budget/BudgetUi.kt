package com.georgeci.moneysurfer.feature.budget

import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.BudgetPeriod
import com.georgeci.moneysurfer.domain.model.BudgetProgress
import com.georgeci.moneysurfer.domain.model.BudgetStatus
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.BudgetId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetStatus
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

/** A category as a budget screen needs it: enough to draw a bubble and name it. */
data class BudgetCategoryUi(
    val id: String,
    val name: String,
    val iconKey: String,
    val hue: Int,
    val systemKind: String?,
)

/** One budget, pre-formatted for the list and the details header. */
data class BudgetUi(
    val id: BudgetId,
    val name: String,
    val status: BudgetStatus,
    val spentFormatted: String,
    val limitFormatted: String,
    /** Always positive — the sign lives in [isOver], which picks the wording. */
    val remainderFormatted: String,
    val isOver: Boolean,
    val progress: Float,
    val alertFraction: Float,
    val alertPercent: Int,
    val percent: Int,
    val period: BudgetPeriod,
    val windowLabel: String,
    val daysLeft: Int,
    val elapsedDays: Int,
    val categories: List<BudgetCategoryUi>,
    val isActive: Boolean,
    val hasMixedCurrency: Boolean,
    val rolloverCarryFormatted: String?,
    val dailyAverageFormatted: String,
    val projectedTotalFormatted: String,
    val perDayRemainingFormatted: String,
    val overspendFormatted: String,
)

fun BudgetStatus.toUi(): SurferBudgetStatus = when (this) {
    BudgetStatus.OK -> SurferBudgetStatus.Ok
    BudgetStatus.WARN -> SurferBudgetStatus.Warn
    BudgetStatus.OVER -> SurferBudgetStatus.Over
}

private const val PERCENT = 100f

/**
 * Turns domain progress into the strings the screens render.
 *
 * [categoriesById] is the whole workspace map; a budget naming a category that has since been
 * deleted simply drops that bubble instead of failing to render.
 */
fun BudgetProgress.toUi(categoriesById: Map<String, Category>): BudgetUi {
    val currency = currency ?: CurrencyCode("")
    val limit = effectiveLimit
    return BudgetUi(
        id = budget.id,
        name = budget.name,
        status = status,
        spentFormatted = MoneyFormatter.format(spent, currency),
        limitFormatted = MoneyFormatter.format(limit, currency),
        remainderFormatted = MoneyFormatter.format(remaining.abs(), currency),
        isOver = status == BudgetStatus.OVER,
        progress = if (limit.minor <= 0L) 0f else spent.minor.toFloat() / limit.minor.toFloat(),
        alertFraction = budget.alertPercent / PERCENT,
        alertPercent = budget.alertPercent,
        percent = percent,
        period = budget.period,
        windowLabel = formatWindow(window.startInclusive, window.endExclusive),
        daysLeft = daysLeft,
        elapsedDays = elapsedDaysOf(this),
        categories = budget.categoryIds.mapNotNull { categoriesById[it.value]?.toUi() },
        isActive = budget.isActive,
        hasMixedCurrency = hasMixedCurrency,
        rolloverCarryFormatted = rolloverCarry
            .takeIf { budget.rollover && it.isPositive() }
            ?.let { MoneyFormatter.format(it, currency) },
        dailyAverageFormatted = MoneyFormatter.format(dailyAverage, currency),
        projectedTotalFormatted = MoneyFormatter.format(projectedTotal, currency),
        perDayRemainingFormatted = MoneyFormatter.format(perDayRemaining, currency),
        overspendFormatted = MoneyFormatter.format(
            if (remaining.isNegative()) remaining.abs() else Money.zero(),
            currency,
        ),
    )
}

private fun Category.toUi() = BudgetCategoryUi(
    id = id.value,
    name = name,
    iconKey = iconKey,
    hue = hue,
    systemKind = systemKind?.name,
)

/** Days of the window already behind us, counting today — the denominator of the daily average. */
private fun elapsedDaysOf(progress: BudgetProgress): Int {
    val windowDays = progress.window.startInclusive.daysUntil(progress.window.endExclusive)
    return (windowDays - progress.daysLeft + 1).coerceIn(1, windowDays)
}

private const val MONTH_ABBREVIATION_LENGTH = 3

/**
 * "1 Apr – 30 Apr". The end is exclusive in the model but inclusive to a reader, so the label
 * shows the last day that still counts.
 */
fun formatWindow(startInclusive: LocalDate, endExclusive: LocalDate): String {
    val lastDay = endExclusive.minus(1, DateTimeUnit.DAY)
    return "${formatShortDate(startInclusive)} – ${formatShortDate(lastDay)}"
}

fun formatShortDate(date: LocalDate): String {
    val month = date.month.name.lowercase()
        .replaceFirstChar { it.uppercase() }
        .take(MONTH_ABBREVIATION_LENGTH)
    return "${date.day} $month"
}
