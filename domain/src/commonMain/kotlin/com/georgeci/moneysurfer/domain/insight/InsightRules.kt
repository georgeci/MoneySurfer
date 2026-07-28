package com.georgeci.moneysurfer.domain.insight

import com.georgeci.moneysurfer.domain.model.CategorySpendSlice
import com.georgeci.moneysurfer.domain.model.RecurringFrequency
import com.georgeci.moneysurfer.domain.model.RecurringRule
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Everything the rules read, gathered by
 * [com.georgeci.moneysurfer.domain.usecase.GenerateInsightsUseCase].
 *
 * A parameter object rather than six arguments so [generateInsights] stays a pure function of one
 * value: a rule spec builds the input it wants and asserts on the output, with no fakes and no
 * coroutines in between.
 *
 * The two spend lists must cover comparable stretches of the calendar — month-to-date against the
 * same stretch of the previous month, not against a whole month. Nothing here can check that; see
 * the use case for why it matters.
 */
data class InsightInput(
    /** Names the period the ids belong to, e.g. `2026-07`. See [Insight.id]. */
    val periodKey: String,
    /**
     * How many days [currentSpend] covers — and, by construction, how many [previousSpend] covers
     * too. Below [MIN_COMPARISON_DAYS] the comparison rules stand down; see [generateInsights].
     */
    val elapsedDays: Int,
    val currency: CurrencyCode,
    val currentSpend: List<CategorySpendSlice>,
    val previousSpend: List<CategorySpendSlice>,
    /** Display names for the categories the slices reference. A missing id reads as uncategorized. */
    val categoryNames: Map<CategoryId, String>,
    /**
     * Recurring rules that book an expense. Telling an expense schedule from an income one needs
     * the category tree, so the caller does that; whether a rule is live is a property of the rule
     * itself, so [generateInsights] does that.
     */
    val expenseSchedules: List<RecurringRule>,
)

/**
 * Every rule, run once, ordered for a card that may only have room for the first one.
 *
 * Each rule returns at most one insight — the point of the widget is two or three sentences worth
 * reading, not a log. Warn comes before Good and Good before Neutral so the compact card, which
 * shows exactly one, shows the actionable one.
 *
 * The comparison rules wait for [MIN_COMPARISON_DAYS]; the subscription count does not, because it
 * reads the schedules rather than the window and is just as true on the 1st as on the 30th.
 */
fun generateInsights(input: InsightInput): List<Insight> {
    val comparisons =
        if (input.elapsedDays >= MIN_COMPARISON_DAYS) periodComparisons(input) else emptyList()
    return (comparisons + listOfNotNull(activeSubscriptions(input)))
        .sortedBy { TONE_PRIORITY.indexOf(it.tone) }
}

/** The rules that need a baseline: the two category movers, and the period total. */
private fun periodComparisons(input: InsightInput): List<Insight> {
    val previousTotal = input.previousSpend.total()
    val changes = categoryChanges(input, floor = previousTotal.share(MIN_DELTA_SHARE))
    return listOfNotNull(
        changes.firstOrNull { it.isIncrease },
        changes.firstOrNull { !it.isIncrease },
        periodSpend(input, current = input.currentSpend.total(), previous = previousTotal),
    )
}

/** Every category that moved enough to be worth a sentence, biggest move in money first. */
private fun categoryChanges(input: InsightInput, floor: Money): List<Insight.CategoryChange> {
    val current = input.currentSpend.associate { it.categoryId to it.total }
    val previous = input.previousSpend.associate { it.categoryId to it.total }
    return (current.keys + previous.keys)
        .mapNotNull { categoryId ->
            categoryChange(
                input = input,
                categoryId = categoryId,
                current = current[categoryId] ?: Money.zero(),
                previous = previous[categoryId] ?: Money.zero(),
                floor = floor,
            )
        }
        .sortedByDescending { (it.current - it.previous).abs().minor }
}

private fun categoryChange(
    input: InsightInput,
    categoryId: CategoryId?,
    current: Money,
    previous: Money,
    floor: Money,
): Insight.CategoryChange? {
    // No baseline, no percentage. A category that booked nothing last period cannot be "up 40%",
    // and reading it as an infinite rise would push every real finding off the card.
    if (!previous.isPositive()) return null
    val delta = current - previous
    val ratio = delta.minor.toDouble() / previous.minor.toDouble()
    // Both terms have to clear. A large percentage of pocket change is noise, and a large amount
    // that barely shifted a large category is not news either.
    if (delta.abs() < floor || abs(ratio) < CATEGORY_CHANGE_RATIO) return null
    return Insight.CategoryChange(
        id = "category-change:${categoryId?.value ?: UNCATEGORIZED_KEY}:${input.periodKey}",
        categoryId = categoryId,
        categoryName = categoryId?.let(input.categoryNames::get),
        current = current,
        previous = previous,
        changePercent = ratio.toWholePercent(),
        currency = input.currency,
    )
}

/**
 * The whole period against the whole previous one.
 *
 * Fires even when the total barely moved, as [SpendTrend.Flat]: "in line with last month" is an
 * answer to the question the widget exists to answer, and a workspace whose categories all sat
 * still would otherwise show an empty card.
 *
 * No floor here, unlike the category rules: this figure *is* the total the floor is a share of.
 */
private fun periodSpend(input: InsightInput, current: Money, previous: Money): Insight.PeriodSpend? {
    if (!previous.isPositive()) return null
    val ratio = (current - previous).minor.toDouble() / previous.minor.toDouble()
    return Insight.PeriodSpend(
        id = "period-spend:${input.periodKey}",
        trend = when {
            ratio >= PERIOD_CHANGE_RATIO -> SpendTrend.Up
            ratio <= -PERIOD_CHANGE_RATIO -> SpendTrend.Down
            else -> SpendTrend.Flat
        },
        current = current,
        previous = previous,
        changePercent = ratio.toWholePercent(),
        currency = input.currency,
    )
}

private fun activeSubscriptions(input: InsightInput): Insight.ActiveSubscriptions? {
    val active = input.expenseSchedules.filter { it.isActive }
    if (active.isEmpty()) return null
    return Insight.ActiveSubscriptions(
        id = "active-subscriptions:${input.periodKey}",
        count = active.size,
        monthlyTotal = active.fold(Money.zero()) { total, rule -> total + rule.monthlyCost() },
        currency = input.currency,
    )
}

/**
 * What one schedule costs in a month, to the nearest minor unit.
 *
 * An estimate by construction: a 30-day month and a 12-month year put daily, weekly, monthly and
 * yearly rules on one scale so they can be added together at all. It fills one sentence on the
 * dashboard — nothing is booked against it, and no balance is derived from it.
 *
 * [RecurringRule.amount] carries no currency of its own; a rule is written in the workspace base
 * currency, which is the currency the rest of the insight is already in. The magnitude is taken
 * because the aggregates this sits beside are magnitudes too.
 */
private fun RecurringRule.monthlyCost(): Money {
    val every = schedule.interval.coerceAtLeast(1)
    val amount = amount.abs()
    return when (schedule.frequency) {
        RecurringFrequency.DAILY -> amount * DAYS_PER_MONTH / every
        RecurringFrequency.WEEKLY -> amount * DAYS_PER_MONTH / (DAYS_PER_WEEK * every)
        RecurringFrequency.MONTHLY -> amount / every
        RecurringFrequency.YEARLY -> amount / (MONTHS_PER_YEAR * every)
    }
}

private fun List<CategorySpendSlice>.total(): Money =
    fold(Money.zero()) { running, slice -> running + slice.total }

/** [fraction] of this amount, rounded to the nearest minor unit. */
private fun Money.share(fraction: Double): Money = Money((minor.toDouble() * fraction).roundToLong())

private fun Double.toWholePercent(): Int = (abs(this) * PERCENT_SCALE).roundToInt()

/** Warn first: the compact card shows one insight, and it should be the one worth acting on. */
private val TONE_PRIORITY = listOf(InsightTone.Warn, InsightTone.Good, InsightTone.Neutral)

/**
 * Days of the period that must have passed before the comparison rules say anything.
 *
 * A month-to-date window against the same stretch of last month is symmetric by construction, but
 * on the 1st that stretch is a *single day*, and one bill landing a day either side of the
 * boundary swings the answer by 100%. A user who pays rent on the 1st and has not been billed yet
 * would be congratulated with "Rent is down 100%" every month. The relative floor cannot filter
 * that — the floor is a share of the very same one-day baseline. A week is the shortest span over
 * which a single charge's timing stops dominating the comparison.
 */
private const val MIN_COMPARISON_DAYS = 7

/** A single category has to move by a fifth before the move is worth a sentence. */
private const val CATEGORY_CHANGE_RATIO = 0.20

/** The whole period's spend is a steadier number, so a tenth of it is already notable. */
private const val PERIOD_CHANGE_RATIO = 0.10

/**
 * Floor under the category rules, as a share of the previous period's *total* spend.
 *
 * Relative rather than absolute: a fixed "20" floor means something different in every currency
 * the app supports, while "a twentieth of what you spent last month" reads the same everywhere.
 * It is what keeps a category that went from 2 to 5 — up 150% — from pushing the one that went
 * from 300 to 380 off the card.
 */
private const val MIN_DELTA_SHARE = 0.05

private const val UNCATEGORIZED_KEY = "uncategorized"
private const val PERCENT_SCALE = 100
private const val DAYS_PER_MONTH = 30
private const val DAYS_PER_WEEK = 7
private const val MONTHS_PER_YEAR = 12
