package com.georgeci.moneysurfer.feature.goal.contribution

import androidx.lifecycle.viewModelScope
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalContributionRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeSavingsGoalRepository
import com.georgeci.moneysurfer.domain.fixtures.aGoal
import com.georgeci.moneysurfer.domain.fixtures.aGoalContribution
import com.georgeci.moneysurfer.domain.fixtures.dollars
import com.georgeci.moneysurfer.domain.model.GoalStatus
import com.georgeci.moneysurfer.domain.model.SavingsGoal
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.usecase.AddContributionUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.domain.usecase.WithdrawFromGoalUseCase
import com.georgeci.moneysurfer.navigation.GoalContributionMode
import com.georgeci.moneysurfer.navigation.SnackbarController
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import moneysurfer.feature.goal.generated.resources.Res
import moneysurfer.feature.goal.generated.resources.goal_contribution_error_below_zero
import moneysurfer.feature.goal.generated.resources.goal_contribution_error_paused

@OptIn(ExperimentalCoroutinesApi::class)
class GoalContributionViewModelTest : StringSpec({

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "adding money records a positive contribution" {
        runTest {
            val fixture = Fixture()
            val vm = fixture.viewModel(GoalContributionMode.ADD)
            try {
                vm.onEvent(GoalContributionEvent.OnAmountChanged("250"))
                vm.currentState.canSave shouldBe true
                vm.onEvent(GoalContributionEvent.OnSaveClick)

                fixture.contributions.rows.single().amount shouldBe 250.dollars
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "withdrawing more than the goal holds surfaces the below-zero error" {
        runTest {
            val fixture = Fixture()
            fixture.contributions.insert(aGoalContribution(goalId = fixture.goal.id, amount = 100.dollars))
            val vm = fixture.viewModel(GoalContributionMode.WITHDRAW)
            try {
                vm.onEvent(GoalContributionEvent.OnAmountChanged("500"))
                vm.onEvent(GoalContributionEvent.OnSaveClick)

                vm.currentState.error shouldBe Res.string.goal_contribution_error_below_zero
                fixture.contributions.rows.size shouldBe 1
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "adding to a paused goal surfaces the paused error" {
        runTest {
            val fixture = Fixture(goal = aGoal(status = GoalStatus.PAUSED))
            val vm = fixture.viewModel(GoalContributionMode.ADD)
            try {
                vm.onEvent(GoalContributionEvent.OnAmountChanged("50"))
                vm.onEvent(GoalContributionEvent.OnSaveClick)

                vm.currentState.error shouldBe Res.string.goal_contribution_error_paused
                fixture.contributions.rows shouldBe emptyList()
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "save is disabled while the amount is empty" {
        runTest {
            val fixture = Fixture()
            val vm = fixture.viewModel(GoalContributionMode.ADD)
            try {
                vm.currentState.canSave shouldBe false
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }
})

private class Fixture(val goal: SavingsGoal = aGoal()) {
    val goals = FakeSavingsGoalRepository(listOf(goal))
    val contributions = FakeGoalContributionRepository()

    fun viewModel(mode: GoalContributionMode) = GoalContributionViewModel(
        goalId = goal.id,
        mode = mode,
        addContribution = AddContributionUseCase(goals, contributions, ClockUseCase()),
        withdrawFromGoal = WithdrawFromGoalUseCase(goals, contributions, ClockUseCase()),
        goalRepository = goals,
        contributionRepository = contributions,
        getCurrentTime = GetCurrentTimeUseCase(ClockUseCase()),
        snackbar = SnackbarController(),
    )
}
