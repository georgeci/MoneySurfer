package com.georgeci.moneysurfer.domain.insight

import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money

/**
 * How an insight reads. Maps 1:1 onto `uikit.widgets.SurferInsightTone`, and is decided here
 * rather than in the widget so the rule that produced a sentence is what decides its colour.
 */
enum class InsightTone {
    /** Something went the user's way. */
    Good,

    /** Something worth looking at. Never an error — no insight is a failure. */
    Warn,

    /** A fact, with no direction to it. */
    Neutral,
}

/**
 * Which way a spend figure moved against the period before it.
 *
 * The tone follows from the direction rather than from the rule, because everything the engine
 * measures is spend: more is always [InsightTone.Warn] and less is always [InsightTone.Good].
 * Nothing here judges income.
 */
enum class SpendTrend {
    Up,
    Down,

    /** Moved, but not by enough to be worth a sentence about the change. */
    Flat,

    ;

    val tone: InsightTone
        get() = when (this) {
            Up -> InsightTone.Warn
            Down -> InsightTone.Good
            Flat -> InsightTone.Neutral
        }
}

/**
 * One generated sentence about the workspace's spending, as data rather than as text: the rules
 * live in `domain` and the copy lives in a feature module's string resources, so a rule can be
 * tested without a locale and translated without a rule change.
 */
sealed interface Insight {

    /**
     * Stable across recomputation: the same finding keeps the same id for as long as the period
     * lasts. That is what lets a "N new" badge stop counting one insight forever, and what a
     * future "dismiss" would be persisted against. Ids therefore name the rule, its subject and
     * the period — never the numbers, which change with every transaction logged.
     */
    val id: String

    val tone: InsightTone

    /** The workspace base currency every [Money] on this insight is denominated in. */
    val currency: CurrencyCode

    /**
     * A category that moved against the previous period.
     *
     * Overspend and savings win are one finding read in two directions, so they are one type
     * rather than two near-identical ones; [isIncrease] is the direction.
     */
    data class CategoryChange(
        override val id: String,
        /**
         * `null` for the uncategorized bucket, which is a real slice rather than a missing one —
         * see [com.georgeci.moneysurfer.domain.model.CategorySpendSlice].
         */
        val categoryId: CategoryId?,
        /** `null` when the slice has no category, or names one this workspace no longer holds. */
        val categoryName: String?,
        val current: Money,
        val previous: Money,
        /** Magnitude of the move as whole percent of [previous]; always positive. */
        val changePercent: Int,
        override val currency: CurrencyCode,
    ) : Insight {

        /** A category is only worth a sentence for having moved, so there is no flat case here. */
        val isIncrease: Boolean get() = current > previous

        override val tone: InsightTone
            get() = if (isIncrease) InsightTone.Warn else InsightTone.Good
    }

    /** Total spend this period against the same stretch of the one before it. */
    data class PeriodSpend(
        override val id: String,
        val trend: SpendTrend,
        val current: Money,
        val previous: Money,
        /** Magnitude of the move as whole percent of [previous]; always positive. */
        val changePercent: Int,
        override val currency: CurrencyCode,
    ) : Insight {
        override val tone: InsightTone get() = trend.tone
    }

    /**
     * How many recurring charges are live, and what they come to in a month.
     *
     * Neutral on purpose: a subscription is not a problem, and the engine has no way to tell a
     * forgotten one from a wanted one. The number is the insight.
     */
    data class ActiveSubscriptions(
        override val id: String,
        val count: Int,
        val monthlyTotal: Money,
        override val currency: CurrencyCode,
    ) : Insight {
        override val tone: InsightTone get() = InsightTone.Neutral
    }
}
