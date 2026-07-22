package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.Money

/**
 * How far a [SavingsGoal] has come. [saved] is always `SUM(contributions.amount)` —
 * see the goal model for why it is never stored.
 *
 * [fraction] is uncapped so logic can tell "exactly on target" from "overshot";
 * [percent] is clamped to `0..1` because the ring and the bar cannot draw more.
 */
data class GoalProgress(
    val saved: Money,
    val target: Money,
) {
    /** Never negative: a goal past its target has nothing left to save. */
    val remaining: Money = if (saved >= target) Money.zero() else target - saved

    /** Uncapped ratio of saved to target. `0.0` when the target is zero. */
    val fraction: Double =
        if (target.minor <= 0L) 0.0 else saved.minor.toDouble() / target.minor.toDouble()

    /** [fraction] clamped to `0.0..1.0`, for progress bars and rings. */
    val percent: Double = fraction.coerceIn(0.0, 1.0)

    val isReached: Boolean = target.minor > 0L && saved >= target

    companion object {
        fun of(goal: SavingsGoal, saved: Money): GoalProgress =
            GoalProgress(saved = saved, target = goal.target)
    }
}

/** A goal plus its derived progress — what the goals list renders. */
data class SavingsGoalSummary(
    val goal: SavingsGoal,
    val progress: GoalProgress,
)

/** A goal, its progress and its full contribution history — the details screen. */
data class SavingsGoalDetails(
    val goal: SavingsGoal,
    val progress: GoalProgress,
    val contributions: List<GoalContribution>,
)
