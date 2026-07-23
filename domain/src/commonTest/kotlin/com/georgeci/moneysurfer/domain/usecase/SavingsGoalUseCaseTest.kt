package com.georgeci.moneysurfer.domain.usecase

import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.EUR
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalContributionRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalWorkspaceRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeSavingsGoalRepository
import com.georgeci.moneysurfer.domain.fixtures.USD
import com.georgeci.moneysurfer.domain.fixtures.aGoal
import com.georgeci.moneysurfer.domain.fixtures.aGoalContribution
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.fixtures.goalId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.model.GoalStatus
import com.georgeci.moneysurfer.domain.primitives.Money
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first

class SavingsGoalUseCaseTest : StringSpec({

    "creating a goal in the workspace base currency succeeds" {
        val goals = FakeSavingsGoalRepository()
        val workspaces = FakeGoalWorkspaceRepository(listOf(aWorkspace(baseCurrency = USD)))
        val goal = aGoal(currencyCode = USD)

        val result = CreateSavingsGoalUseCase(goals, workspaces).invoke(goal)

        result.isRight() shouldBe true
        goals.rows.single().id shouldBe goal.id
    }

    "creating a goal in another currency is rejected" {
        val goals = FakeSavingsGoalRepository()
        val workspaces = FakeGoalWorkspaceRepository(listOf(aWorkspace(baseCurrency = USD)))

        val result = CreateSavingsGoalUseCase(goals, workspaces).invoke(aGoal(currencyCode = EUR))

        result.leftOrNull() shouldBe GoalActionError.CurrencyMismatch
        goals.rows shouldBe emptyList()
    }

    "creating a goal with a non-positive target is rejected" {
        val goals = FakeSavingsGoalRepository()
        val workspaces = FakeGoalWorkspaceRepository(listOf(aWorkspace()))

        val result = CreateSavingsGoalUseCase(goals, workspaces).invoke(aGoal(target = Money.zero()))

        result.leftOrNull() shouldBe GoalActionError.InvalidTarget
    }

    "creating a goal in an unknown workspace is rejected" {
        val goals = FakeSavingsGoalRepository()
        val workspaces = FakeGoalWorkspaceRepository()

        val result = CreateSavingsGoalUseCase(goals, workspaces).invoke(aGoal())

        result.leftOrNull() shouldBe GoalActionError.WorkspaceNotFound
    }

    "lowering the target below the saved amount completes the goal" {
        val goal = aGoal(target = 1_000.dollars)
        val goals = FakeSavingsGoalRepository(listOf(goal))
        val contributions = FakeGoalContributionRepository(
            listOf(aGoalContribution(goalId = goal.id, amount = 400.dollars)),
        )
        val workspaces = FakeGoalWorkspaceRepository(listOf(aWorkspace()))

        val result = UpdateSavingsGoalUseCase(goals, contributions, workspaces)
            .invoke(goal.copy(target = 300.dollars))

        result.isRight() shouldBe true
        goals.rows.single().status shouldBe GoalStatus.COMPLETED
    }

    "raising the target above the saved amount re-opens a completed goal" {
        val goal = aGoal(target = 300.dollars, status = GoalStatus.COMPLETED)
        val goals = FakeSavingsGoalRepository(listOf(goal))
        val contributions = FakeGoalContributionRepository(
            listOf(aGoalContribution(goalId = goal.id, amount = 400.dollars)),
        )
        val workspaces = FakeGoalWorkspaceRepository(listOf(aWorkspace()))

        UpdateSavingsGoalUseCase(goals, contributions, workspaces)
            .invoke(goal.copy(target = 900.dollars))

        goals.rows.single().status shouldBe GoalStatus.ACTIVE
    }

    "updating a missing goal is rejected" {
        val goals = FakeSavingsGoalRepository()
        val contributions = FakeGoalContributionRepository()
        val workspaces = FakeGoalWorkspaceRepository(listOf(aWorkspace()))

        val result = UpdateSavingsGoalUseCase(goals, contributions, workspaces).invoke(aGoal())

        result.leftOrNull() shouldBe GoalActionError.GoalNotFound
    }

    "pausing a goal keeps its progress" {
        val goal = aGoal()
        val goals = FakeSavingsGoalRepository(listOf(goal))
        val contributions = FakeGoalContributionRepository()

        val result = SetGoalStatusUseCase(goals, contributions).invoke(goal.id, GoalStatus.PAUSED)

        result.getOrNull() shouldBe GoalStatus.PAUSED
        goals.rows.single().status shouldBe GoalStatus.PAUSED
    }

    "resuming a goal that is already past its target lands on COMPLETED" {
        val goal = aGoal(target = 100.dollars, status = GoalStatus.PAUSED)
        val goals = FakeSavingsGoalRepository(listOf(goal))
        val contributions = FakeGoalContributionRepository(
            listOf(aGoalContribution(goalId = goal.id, amount = 150.dollars)),
        )

        val result = SetGoalStatusUseCase(goals, contributions).invoke(goal.id, GoalStatus.ACTIVE)

        result.getOrNull() shouldBe GoalStatus.COMPLETED
        goals.rows.single().status shouldBe GoalStatus.COMPLETED
    }

    "COMPLETED cannot be set by hand" {
        val goal = aGoal()
        val goals = FakeSavingsGoalRepository(listOf(goal))
        val contributions = FakeGoalContributionRepository()

        val result = SetGoalStatusUseCase(goals, contributions).invoke(goal.id, GoalStatus.COMPLETED)

        result.leftOrNull() shouldBe GoalActionError.StatusNotSettable
    }

    "setting the status of a missing goal is rejected" {
        val goals = FakeSavingsGoalRepository()
        val contributions = FakeGoalContributionRepository()

        val result = SetGoalStatusUseCase(goals, contributions)
            .invoke(goalId("missing"), GoalStatus.PAUSED)

        result.leftOrNull() shouldBe GoalActionError.GoalNotFound
    }

    "deleting returns the removed goal" {
        val goal = aGoal()
        val goals = FakeSavingsGoalRepository(listOf(goal))

        DeleteSavingsGoalUseCase(goals).invoke(goal.id) shouldBe goal
        goals.rows shouldBe emptyList()
    }

    "deleting a missing goal returns null" {
        DeleteSavingsGoalUseCase(FakeSavingsGoalRepository()).invoke(goalId("missing")) shouldBe null
    }

    "the goals list folds in saved amounts and hides archived goals" {
        val active = aGoal(id = goalId("g-active"), target = 1_000.dollars)
        val archived = aGoal(id = goalId("g-archived"), status = GoalStatus.ARCHIVED)
        val goals = FakeSavingsGoalRepository(listOf(active, archived))
        val contributions = FakeGoalContributionRepository(
            listOf(
                aGoalContribution(goalId = active.id, amount = 250.dollars),
                aGoalContribution(goalId = active.id, amount = -(50.dollars)),
            ),
        )
        val session = InMemorySessionPointers(currentWorkspaceId = workspaceId())

        val summaries = GetGoalsUseCase(goals, contributions, session).invoke().first()

        summaries.map { it.goal.id } shouldBe listOf(active.id)
        summaries.single().progress.saved shouldBe 200.dollars
        summaries.single().progress.percent shouldBe 0.2
    }

    "goal details carry progress and history" {
        val goal = aGoal(target = 500.dollars)
        val goals = FakeSavingsGoalRepository(listOf(goal))
        val contributions = FakeGoalContributionRepository(
            listOf(aGoalContribution(goalId = goal.id, amount = 500.dollars)),
        )

        val details = GetGoalDetailsUseCase(goals, contributions).invoke(goal.id).first()

        details?.progress?.isReached shouldBe true
        details?.contributions?.size shouldBe 1
    }

    "goal details emit null once the goal is gone" {
        val goals = FakeSavingsGoalRepository()
        val contributions = FakeGoalContributionRepository()

        GetGoalDetailsUseCase(goals, contributions).invoke(goalId("missing")).first() shouldBe null
    }
})
