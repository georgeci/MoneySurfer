package com.georgeci.moneysurfer.feature.goal.edit

import androidx.lifecycle.viewModelScope
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalContributionRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeGoalWorkspaceRepository
import com.georgeci.moneysurfer.domain.fixtures.FakeSavingsGoalRepository
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.usecase.CreateSavingsGoalUseCase
import com.georgeci.moneysurfer.domain.usecase.GetCurrentTimeUseCase
import com.georgeci.moneysurfer.domain.usecase.UpdateSavingsGoalUseCase
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
import moneysurfer.feature.goal.generated.resources.goal_edit_error_generic

@OptIn(ExperimentalCoroutinesApi::class)
class GoalEditViewModelTest : StringSpec({

    beforeEach { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterEach { Dispatchers.resetMain() }

    "save stays disabled until both a title and a positive target are entered" {
        runTest {
            val fixture = Fixture()
            val vm = fixture.viewModel()
            try {
                vm.currentState.canSave shouldBe false

                vm.onEvent(GoalEditEvent.OnTitleChanged("Lisbon trip"))
                vm.currentState.canSave shouldBe false

                vm.onEvent(GoalEditEvent.OnTargetChanged("0"))
                vm.currentState.canSave shouldBe false
                vm.currentState.targetMissing shouldBe true

                vm.onEvent(GoalEditEvent.OnTargetChanged("2400"))
                vm.currentState.canSave shouldBe true
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "letters are stripped from the target field" {
        runTest {
            val fixture = Fixture()
            val vm = fixture.viewModel()
            try {
                vm.onEvent(GoalEditEvent.OnTargetChanged("1a2,5x"))
                vm.currentState.target shouldBe "12.5"
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "saving creates a goal in the workspace base currency" {
        runTest {
            val fixture = Fixture()
            val vm = fixture.viewModel()
            try {
                vm.onEvent(GoalEditEvent.OnTitleChanged("Lisbon trip"))
                vm.onEvent(GoalEditEvent.OnTargetChanged("2400"))
                vm.onEvent(GoalEditEvent.OnSaveClick)

                val created = fixture.goals.rows.single()
                created.title shouldBe "Lisbon trip"
                created.target shouldBe Money.fromMajor(2_400)
                created.currencyCode shouldBe fixture.workspace.baseCurrency
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a rejected save keeps the user on the screen with their input" {
        runTest {
            // No workspace behind the session pointer — the save cannot go through.
            val fixture = Fixture(seedWorkspace = false)
            val vm = fixture.viewModel()
            try {
                vm.onEvent(GoalEditEvent.OnTitleChanged("Lisbon trip"))
                vm.onEvent(GoalEditEvent.OnTargetChanged("2400"))
                vm.onEvent(GoalEditEvent.OnSaveClick)

                fixture.goals.rows shouldBe emptyList()
                vm.currentState.error shouldBe Res.string.goal_edit_error_generic
                vm.currentState.inFlight shouldBe false
                vm.currentState.title shouldBe "Lisbon trip"
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }

    "a blank title never reaches the repository" {
        runTest {
            val fixture = Fixture()
            val vm = fixture.viewModel()
            try {
                vm.onEvent(GoalEditEvent.OnTitleChanged("   "))
                vm.onEvent(GoalEditEvent.OnTargetChanged("100"))
                vm.onEvent(GoalEditEvent.OnSaveClick)

                fixture.goals.rows shouldBe emptyList()
                vm.currentState.titleMissing shouldBe true
            } finally {
                vm.viewModelScope.cancel()
            }
        }
    }
})

private class Fixture(seedWorkspace: Boolean = true) {
    val workspace = aWorkspace(id = workspaceId())
    val goals = FakeSavingsGoalRepository()
    val contributions = FakeGoalContributionRepository()
    private val workspaces =
        FakeGoalWorkspaceRepository(if (seedWorkspace) listOf(workspace) else emptyList())
    private val session = InMemorySessionPointers(currentWorkspaceId = workspace.id)

    fun viewModel() = GoalEditViewModel(
        goalId = null,
        createGoal = CreateSavingsGoalUseCase(goals, workspaces),
        updateGoal = UpdateSavingsGoalUseCase(goals, contributions, workspaces),
        goalRepository = goals,
        workspaceRepository = workspaces,
        session = session,
        getCurrentTime = GetCurrentTimeUseCase(ClockUseCase()),
        snackbar = SnackbarController(),
    )
}
