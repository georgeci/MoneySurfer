package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.fixtures.FakeGoalContributionRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeSavingsGoalRepository
import com.georgeci.moneysurfer.domain.fixtures.aGoal
import com.georgeci.moneysurfer.domain.fixtures.aGoalContribution
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.goalId
import com.georgeci.moneysurfer.domain.fixtures.testDate
import com.georgeci.moneysurfer.domain.model.GoalStatus
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class GoalContributionUseCaseTest : StringSpec({

    "adding money records a contribution" {
        val goal = aGoal(target = 1_000.dollars)
        val goals = FakeSavingsGoalRepository(listOf(goal))
        val contributions = FakeGoalContributionRepository()

        val result = addContribution(goals, contributions).invoke(goal.id, 250.dollars, testDate, "salary")

        result.isRight() shouldBe true
        contributions.rows.single().amount shouldBe 250.dollars
        contributions.rows.single().note shouldBe "salary"
        goals.rows.single().status shouldBe GoalStatus.ACTIVE
    }

    "a zero contribution is rejected" {
        val goal = aGoal()
        val goals = FakeSavingsGoalRepository(listOf(goal))
        val contributions = FakeGoalContributionRepository()

        val result = addContribution(goals, contributions).invoke(goal.id, Money.zero(), testDate)

        result.leftOrNull() shouldBe GoalActionError.InvalidAmount
        contributions.rows shouldBe emptyList()
    }

    "a contribution to a missing goal is rejected" {
        val goals = FakeSavingsGoalRepository()
        val contributions = FakeGoalContributionRepository()

        val result = addContribution(goals, contributions).invoke(goalId("missing"), 10.dollars, testDate)

        result.leftOrNull() shouldBe GoalActionError.GoalNotFound
    }

    "reaching the target flips ACTIVE to COMPLETED" {
        val goal = aGoal(target = 1_000.dollars)
        val goals = FakeSavingsGoalRepository(listOf(goal))
        val contributions = FakeGoalContributionRepository(
            listOf(aGoalContribution(goalId = goal.id, amount = 900.dollars)),
        )

        addContribution(goals, contributions).invoke(goal.id, 100.dollars, testDate)

        goals.rows.single().status shouldBe GoalStatus.COMPLETED
    }

    "a withdrawal that drops below the target flips COMPLETED back to ACTIVE" {
        val goal = aGoal(target = 1_000.dollars, status = GoalStatus.COMPLETED)
        val goals = FakeSavingsGoalRepository(listOf(goal))
        val contributions = FakeGoalContributionRepository(
            listOf(aGoalContribution(goalId = goal.id, amount = 1_000.dollars)),
        )

        val result = withdraw(goals, contributions).invoke(goal.id, 1.dollars, testDate)

        result.isRight() shouldBe true
        goals.rows.single().status shouldBe GoalStatus.ACTIVE
    }

    "a withdrawal is stored as a negative contribution" {
        val goal = aGoal(target = 1_000.dollars)
        val goals = FakeSavingsGoalRepository(listOf(goal))
        val contributions = FakeGoalContributionRepository(
            listOf(aGoalContribution(goalId = goal.id, amount = 500.dollars)),
        )

        withdraw(goals, contributions).invoke(goal.id, 200.dollars, testDate)

        contributions.savedAmount(goal.id) shouldBe 300.dollars
        contributions.rows.last().amount shouldBe -(200.dollars)
    }

    "a withdrawal cannot take the saved amount below zero" {
        val goal = aGoal(target = 1_000.dollars)
        val goals = FakeSavingsGoalRepository(listOf(goal))
        val contributions = FakeGoalContributionRepository(
            listOf(aGoalContribution(goalId = goal.id, amount = 100.dollars)),
        )

        val result = withdraw(goals, contributions).invoke(goal.id, 101.dollars, testDate)

        result.leftOrNull() shouldBe GoalActionError.WithdrawalBelowZero
        contributions.rows.size shouldBe 1
    }

    "a withdrawal down to exactly zero is allowed" {
        val goal = aGoal(target = 1_000.dollars)
        val goals = FakeSavingsGoalRepository(listOf(goal))
        val contributions = FakeGoalContributionRepository(
            listOf(aGoalContribution(goalId = goal.id, amount = 100.dollars)),
        )

        val result = withdraw(goals, contributions).invoke(goal.id, 100.dollars, testDate)

        result.isRight() shouldBe true
        contributions.savedAmount(goal.id) shouldBe Money.zero()
    }

    "a PAUSED goal rejects new contributions" {
        val goal = aGoal(status = GoalStatus.PAUSED)
        val goals = FakeSavingsGoalRepository(listOf(goal))
        val contributions = FakeGoalContributionRepository()

        val result = addContribution(goals, contributions).invoke(goal.id, 10.dollars, testDate)

        result.leftOrNull() shouldBe GoalActionError.GoalNotAcceptingContributions
        contributions.rows shouldBe emptyList()
    }

    "an ARCHIVED goal rejects new contributions" {
        val goal = aGoal(status = GoalStatus.ARCHIVED)
        val goals = FakeSavingsGoalRepository(listOf(goal))
        val contributions = FakeGoalContributionRepository()

        val result = addContribution(goals, contributions).invoke(goal.id, 10.dollars, testDate)

        result.leftOrNull() shouldBe GoalActionError.GoalNotAcceptingContributions
    }

    "a PAUSED goal still allows a withdrawal" {
        val goal = aGoal(status = GoalStatus.PAUSED)
        val goals = FakeSavingsGoalRepository(listOf(goal))
        val contributions = FakeGoalContributionRepository(
            listOf(aGoalContribution(goalId = goal.id, amount = 50.dollars)),
        )

        val result = withdraw(goals, contributions).invoke(goal.id, 50.dollars, testDate)

        result.isRight() shouldBe true
        goals.rows.single().status shouldBe GoalStatus.PAUSED
    }

    "a negative amount passed to add is rejected instead of read as a withdrawal" {
        val goal = aGoal()
        val goals = FakeSavingsGoalRepository(listOf(goal))
        val contributions = FakeGoalContributionRepository()

        val result = addContribution(goals, contributions).invoke(goal.id, -(10.dollars), testDate)

        result.leftOrNull() shouldBe GoalActionError.InvalidAmount
    }
})

private fun addContribution(
    goals: FakeSavingsGoalRepository,
    contributions: FakeGoalContributionRepository,
) = AddContributionUseCase(goals, contributions, ClockUseCase())

private fun withdraw(
    goals: FakeSavingsGoalRepository,
    contributions: FakeGoalContributionRepository,
) = WithdrawFromGoalUseCase(goals, contributions, ClockUseCase())
