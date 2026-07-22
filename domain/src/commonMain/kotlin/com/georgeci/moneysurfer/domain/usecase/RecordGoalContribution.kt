package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.ensure
import com.georgeci.moneysurfer.domain.model.GoalContribution
import com.georgeci.moneysurfer.domain.model.GoalStatus
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.repositories.GoalContributionRepository
import com.georgeci.moneysurfer.domain.repositories.SavingsGoalRepository
import kotlinx.datetime.LocalDate

/**
 * The one write path for both [AddContributionUseCase] and [WithdrawFromGoalUseCase] —
 * a withdrawal is a contribution with a negative amount (decision 3 in md/goals.md), so
 * the invariants, the insert and the ACTIVE ↔ COMPLETED flip are checked in one place.
 */
@Suppress("LongParameterList")
internal suspend fun Raise<GoalActionError>.recordContribution(
    goalRepository: SavingsGoalRepository,
    contributionRepository: GoalContributionRepository,
    clock: ClockUseCase,
    goalId: GoalId,
    signedAmount: Money,
    occurredOn: LocalDate,
    note: String,
): GoalContribution {
    ensure(!signedAmount.isZero()) { GoalActionError.InvalidAmount }
    val goal = goalRepository.getById(goalId) ?: raise(GoalActionError.GoalNotFound)
    val saved = contributionRepository.savedAmount(goalId)
    if (signedAmount.isPositive()) {
        ensure(goal.status.acceptsContributions) { GoalActionError.GoalNotAcceptingContributions }
    } else {
        ensure(saved + signedAmount >= Money.zero()) { GoalActionError.WithdrawalBelowZero }
    }

    val now = clock.now()
    val contribution = GoalContribution(
        workspaceId = goal.workspaceId,
        goalId = goalId,
        amount = signedAmount,
        occurredOn = occurredOn,
        note = note,
        createdAt = now,
    )
    Either.catch { contributionRepository.insert(contribution) }
        .mapLeft { GoalActionError.LocalWriteFailed(it) }
        .bind()

    val resolved = goal.status.afterProgressChange((saved + signedAmount) >= goal.target)
    if (resolved != goal.status) {
        Either.catch { goalRepository.update(goal.copy(status = resolved)) }
            .mapLeft { GoalActionError.LocalWriteFailed(it) }
            .bind()
    }
    return contribution
}

/** PAUSED and ARCHIVED goals take no new money; a withdrawal is still allowed. */
internal val GoalStatus.acceptsContributions: Boolean
    get() = this == GoalStatus.ACTIVE || this == GoalStatus.COMPLETED
