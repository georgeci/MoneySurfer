package com.georgeci.moneysurfer.feature.workspace.selector

import app.cash.turbine.test
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.auth.SessionMutator
import com.georgeci.moneysurfer.domain.fixtures.FakeHostCapabilities
import com.georgeci.moneysurfer.domain.fixtures.aWorkspace
import com.georgeci.moneysurfer.domain.fixtures.userId
import com.georgeci.moneysurfer.domain.fixtures.workspaceId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.usecase.GetWorkspacesForUserUseCase
import com.georgeci.moneysurfer.domain.usecase.LogoutUseCase
import com.georgeci.moneysurfer.domain.usecase.SelectWorkspaceUseCase
import com.georgeci.moneysurfer.feature.workspace.FakeWorkspaceRepository
import com.georgeci.moneysurfer.feature.workspace.RecordingAuthRemoteRepository
import com.georgeci.moneysurfer.feature.workspace.RecordingLocalDataResetRepository
import com.georgeci.moneysurfer.feature.workspace.RecordingRemoteDataResetRepository
import com.georgeci.moneysurfer.feature.workspace.RecordingSessionShutdownGate
import com.georgeci.moneysurfer.feature.workspace.RecordingUserRemoteRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * The workspace selector. It is reached two ways and behaves differently in each: from Settings
 * (`showActions = true`) it is a normal, backed-out-of screen with per-row actions, and from
 * sign-in / cold start it is a dead end with no back entry — which is why "Sign out" only appears
 * in the second case, and only in the online build (issue #342).
 *
 * Tapping a row is deliberately *not* a commit: it moves the pending selection, and Confirm is what
 * actually pins the workspace.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceSelectorViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "the signed-in user's workspaces land in Content" {
        val personal = aWorkspace(id = workspaceId("ws-1"), name = "Personal")
        val family = aWorkspace(id = workspaceId("ws-2"), name = "Family")
        val viewModel = newViewModel(workspaces = FakeWorkspaceRepository(listOf(personal, family)))

        content(viewModel).workspaces shouldContainExactly listOf(personal, family)
    }

    "the pinned workspace is preselected so Confirm works without touching a row" {
        val personal = aWorkspace(id = workspaceId("ws-1"))
        val viewModel = newViewModel(
            session = InMemorySessionPointers(
                currentUserId = userId("u-1"),
                currentWorkspaceId = personal.id,
            ),
            workspaces = FakeWorkspaceRepository(listOf(personal)),
        )

        val content = content(viewModel)
        content.selectedWorkspaceId shouldBe personal.id
        content.activeWorkspaceId shouldBe personal.id
        content.activeWorkspace shouldBe personal
    }

    "a signed-out session promotes to an empty Content rather than spinning forever" {
        val viewModel = newViewModel(
            session = InMemorySessionPointers(currentUserId = null),
            workspaces = FakeWorkspaceRepository(listOf(aWorkspace())),
        )

        content(viewModel).workspaces.shouldBeEmpty()
    }

    "a workspace created elsewhere shows up without reopening the screen" {
        val repository = FakeWorkspaceRepository()
        val viewModel = newViewModel(workspaces = repository)

        content(viewModel).workspaces.shouldBeEmpty()

        val family = aWorkspace(id = workspaceId("ws-2"), name = "Family")
        repository.emit(listOf(family))

        content(viewModel).workspaces shouldContainExactly listOf(family)
    }

    "tapping a row moves the pending selection without committing it" {
        val personal = aWorkspace(id = workspaceId("ws-1"))
        val family = aWorkspace(id = workspaceId("ws-2"))
        val session = InMemorySessionPointers(currentUserId = userId("u-1"), currentWorkspaceId = personal.id)
        val viewModel = newViewModel(
            session = session,
            workspaces = FakeWorkspaceRepository(listOf(personal, family)),
        )

        viewModel.onEvent(WorkspaceSelectorEvent.OnWorkspaceClick(family))

        val content = content(viewModel)
        content.pendingWorkspaceId shouldBe family.id
        content.activeWorkspaceId shouldBe family.id
        // Still pinned to the old one — only Confirm switches the app over.
        content.selectedWorkspaceId shouldBe personal.id
        session.currentWorkspaceId.first() shouldBe personal.id
    }

    "Confirm pins the pending workspace and heads to the dashboard" {
        val personal = aWorkspace(id = workspaceId("ws-1"))
        val family = aWorkspace(id = workspaceId("ws-2"))
        val session = InMemorySessionPointers(currentUserId = userId("u-1"), currentWorkspaceId = personal.id)
        val viewModel = newViewModel(
            session = session,
            workspaces = FakeWorkspaceRepository(listOf(personal, family)),
        )

        viewModel.onEvent(WorkspaceSelectorEvent.OnWorkspaceClick(family))
        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(WorkspaceSelectorEvent.OnConfirmClick)
            awaitItem() shouldBe WorkspaceSelectorEffect.NavigateToDashboard
        }

        session.currentWorkspaceId.first() shouldBe family.id
    }

    "Confirm with nothing selected does nothing" {
        val session = InMemorySessionPointers(currentUserId = userId("u-1"))
        val viewModel = newViewModel(session = session, workspaces = FakeWorkspaceRepository())

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(WorkspaceSelectorEvent.OnConfirmClick)
            expectNoEvents()
        }

        session.currentWorkspaceId.first() shouldBe null
    }

    "a failed select releases the spinner so the user can try again" {
        val personal = aWorkspace(id = workspaceId("ws-1"))
        val session = InMemorySessionPointers(currentUserId = userId("u-1"))
        val viewModel = newViewModel(
            session = session,
            sessionMutator = FailingPinMutator(session),
            workspaces = FakeWorkspaceRepository(listOf(personal)),
        )

        viewModel.onEvent(WorkspaceSelectorEvent.OnWorkspaceClick(personal))
        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(WorkspaceSelectorEvent.OnConfirmClick)
            expectNoEvents()
        }

        content(viewModel).isSelecting shouldBe false
    }

    "signing out clears the session and returns to sign-in" {
        val session = InMemorySessionPointers(
            currentUserId = userId("u-1"),
            currentWorkspaceId = workspaceId("ws-1"),
        )
        val auth = RecordingAuthRemoteRepository()
        val localReset = RecordingLocalDataResetRepository()
        val viewModel = newViewModel(
            session = session,
            workspaces = FakeWorkspaceRepository(listOf(aWorkspace())),
            auth = auth,
            localReset = localReset,
        )

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(WorkspaceSelectorEvent.OnSignOutClick)
            awaitItem() shouldBe WorkspaceSelectorEffect.NavigateToSignIn
        }

        session.currentUserId.first() shouldBe null
        localReset.clears shouldBe 1
        auth.signOuts shouldBe 1
    }

    "as a dead end from sign-in the online build offers a way out" {
        val viewModel = newViewModel(showActions = false, hostCapabilities = FakeHostCapabilities(isOffline = false))

        content(viewModel).canSignOut shouldBe true
    }

    "opened from Settings the screen has a back entry, so it offers no sign-out" {
        val viewModel = newViewModel(showActions = true, hostCapabilities = FakeHostCapabilities(isOffline = false))

        content(viewModel).canSignOut shouldBe false
    }

    "the offline build never offers sign-out — there is no account to sign out of" {
        val viewModel = newViewModel(showActions = false, hostCapabilities = FakeHostCapabilities.offline())

        content(viewModel).canSignOut shouldBe false
    }

    "the row actions each navigate to their own screen" {
        val personal = aWorkspace(id = workspaceId("ws-1"))
        val viewModel = newViewModel(workspaces = FakeWorkspaceRepository(listOf(personal)))

        viewModel.sideEffects.effectFlow.test {
            viewModel.onEvent(WorkspaceSelectorEvent.OnEditWorkspaceClick(personal))
            awaitItem() shouldBe WorkspaceSelectorEffect.NavigateToWorkspaceEdit(personal.id)

            viewModel.onEvent(WorkspaceSelectorEvent.OnMembersClick(personal))
            awaitItem() shouldBe WorkspaceSelectorEffect.NavigateToWorkspaceMembers(personal.id)

            viewModel.onEvent(WorkspaceSelectorEvent.OnCreateWorkspaceClick)
            awaitItem() shouldBe WorkspaceSelectorEffect.NavigateToWorkspaceCreation
        }
    }

    "a pending id with no matching row resolves to no active workspace" {
        val viewModel = newViewModel(
            session = InMemorySessionPointers(
                currentUserId = userId("u-1"),
                currentWorkspaceId = workspaceId("gone"),
            ),
            workspaces = FakeWorkspaceRepository(listOf(aWorkspace(id = workspaceId("ws-1")))),
        )

        val content = content(viewModel)
        content.activeWorkspaceId shouldBe workspaceId("gone")
        content.activeWorkspace shouldBe null
    }
})

private fun content(viewModel: WorkspaceSelectorViewModel): WorkspaceSelectorState.Content =
    viewModel.currentState.shouldBeInstanceOf<WorkspaceSelectorState.Content>()

/** Fails the one write `SelectWorkspaceUseCase` has no recovery for. */
private class FailingPinMutator(private val delegate: SessionMutator) : SessionMutator by delegate {
    override suspend fun setCurrentWorkspace(id: WorkspaceId?) = error("simulated pointer write failure")
}

@Suppress("LongParameterList")
private fun newViewModel(
    showActions: Boolean = true,
    session: InMemorySessionPointers = InMemorySessionPointers(currentUserId = userId("u-1")),
    sessionMutator: SessionMutator = session,
    workspaces: WorkspaceRepository = FakeWorkspaceRepository(),
    auth: RecordingAuthRemoteRepository = RecordingAuthRemoteRepository(),
    localReset: RecordingLocalDataResetRepository = RecordingLocalDataResetRepository(),
    hostCapabilities: FakeHostCapabilities = FakeHostCapabilities(isOffline = false),
): WorkspaceSelectorViewModel = WorkspaceSelectorViewModel(
    showActions = showActions,
    session = session,
    getWorkspacesForUserUseCase = GetWorkspacesForUserUseCase(workspaces),
    selectWorkspaceUseCase = SelectWorkspaceUseCase(session, sessionMutator, RecordingUserRemoteRepository()),
    logoutUseCase = LogoutUseCase(
        sessionShutdownGate = RecordingSessionShutdownGate(),
        authRemoteRepository = auth,
        localDataResetRepository = localReset,
        remoteDataResetRepository = RecordingRemoteDataResetRepository(),
        sessionMutator = sessionMutator,
    ),
    hostCapabilities = hostCapabilities,
)
