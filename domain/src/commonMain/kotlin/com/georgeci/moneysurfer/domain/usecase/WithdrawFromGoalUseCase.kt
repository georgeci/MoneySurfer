package com.georgeci.moneysurfer.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.georgeci.moneysurfer.domain.model.GoalContribution
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.GoalId
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.repositories.GoalContributionRepository
import com.georgeci.moneysurfer.domain.repositories.SavingsGoalRepository
import kotlinx.datetime.LocalDate
import org.koin.core.annotation.Single

/**
 * Takes money back out of a goal. [amount] is passed positive and stored negated —
 * a withdrawal is a contribution with a negative amount, not its own entity.
 */
@Single
class WithdrawFromGoalUseCase(
    private val goalRepository: SavingsGoalRepository,
    private val contributionRepository: GoalContributionRepository,
    private val clock: ClockUseCase,
) {

    suspend operator fun invoke(
        goalId: GoalId,
        amount: Money,
        occurredOn: LocalDate,
        note: String = "",
    ): Either<GoalActionError, GoalContribution> = either {
        ensure(!amount.isNegative()) { GoalActionError.InvalidAmount }
        recordContribution(
            goalRepository = goalRepository,
            contributionRepository = contributionRepository,
            clock = clock,
            goalId = goalId,
            signedAmount = -amount,
            occurredOn = occurredOn,
            note = note,
        )
    }
}
