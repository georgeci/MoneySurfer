package com.georgeci.moneysurfer.feature.workspace.selector

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.config.HostCapabilities
import com.georgeci.moneysurfer.domain.model.Workspace
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.usecase.GetWorkspacesForUserUseCase
import com.georgeci.moneysurfer.domain.usecase.LogoutUseCase
import com.georgeci.moneysurfer.domain.usecase.SelectWorkspaceUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
@Suppress("LongParameterList")
class WorkspaceSelectorViewModel(
    private val showActions: Boolean,
    private val session: SessionPointers,
    private val getWorkspacesForUserUseCase: GetWorkspacesForUserUseCase,
    private val selectWorkspaceUseCase: SelectWorkspaceUseCase,
    private val logoutUseCase: LogoutUseCase,
    hostCapabilities: HostCapabilities,
) : MviViewModel<WorkspaceSelectorState, WorkspaceSelectorEvent, WorkspaceSelectorEffect>(
    initialState = WorkspaceSelectorState.Loading(
        showActions = showActions,
        isOffline = hostCapabilities.isOffline,
        canSignOut = !showActions && !hostCapabilities.isOffline,
    ),
) {

    private val isOffline: Boolean = hostCapabilities.isOffline

    /**
     * The selector is a dead end exactly when it was *not* opened from Settings: sign-in and the
     * cold-start "signed in but no workspace" route both `replaceTop`/`resetTo` onto it, so there
     * is no back entry, and Settings itself sits behind Dashboard, which needs a workspace. Without
     * a way out the only live control is "Create workspace" (issue #342).
     */
    private val canSignOut: Boolean = !showActions && !isOffline

    init {
        loadWorkspaces()
        loadSelectedWorkspace()
    }

    override fun onEvent(event: WorkspaceSelectorEvent) {
        when (event) {
            is WorkspaceSelectorEvent.OnWorkspaceClick ->
                updateState {
                    WorkspaceSelectorState.content.modify(this) {
                        it.copy(pendingWorkspaceId = event.workspace.id)
                    }
                }
            is WorkspaceSelectorEvent.OnEditWorkspaceClick ->
                postSideEffect(WorkspaceSelectorEffect.NavigateToWorkspaceEdit(event.workspace.id))
            is WorkspaceSelectorEvent.OnMembersClick ->
                postSideEffect(WorkspaceSelectorEffect.NavigateToWorkspaceMembers(event.workspace.id))
            WorkspaceSelectorEvent.OnCreateWorkspaceClick ->
                postSideEffect(WorkspaceSelectorEffect.NavigateToWorkspaceCreation)
            WorkspaceSelectorEvent.OnConfirmClick -> confirmSelection()
            WorkspaceSelectorEvent.OnSignOutClick -> signOut()
            WorkspaceSelectorEvent.OnBackClick -> postSideEffect(WorkspaceSelectorEffect.NavigateBack)
        }
    }

    private fun signOut() {
        launch {
            logoutUseCase()
            postSideEffect(WorkspaceSelectorEffect.NavigateToSignIn)
        }
    }

    private fun loadWorkspaces() {
        launch {
            val userId = session.currentUserId.first() ?: run {
                promoteToContent(workspaces = emptyList())
                return@launch
            }

            getWorkspacesForUserUseCase(userId).collect { workspaces ->
                promoteToContent(workspaces = workspaces)
            }
        }
    }

    private fun loadSelectedWorkspace() {
        launch {
            session.currentWorkspaceId.collect { workspaceId ->
                updateState {
                    when (this) {
                        is WorkspaceSelectorState.Loading -> WorkspaceSelectorState.Content(
                            workspaces = emptyList(),
                            selectedWorkspaceId = workspaceId,
                            pendingWorkspaceId = workspaceId,
                            showActions = showActions,
                            isSelecting = false,
                            isOffline = isOffline,
                            canSignOut = canSignOut,
                        )
                        is WorkspaceSelectorState.Content -> copy(
                            selectedWorkspaceId = workspaceId,
                            pendingWorkspaceId = pendingWorkspaceId ?: workspaceId,
                        )
                    }
                }
            }
        }
    }

    private fun confirmSelection() {
        val content = currentState as? WorkspaceSelectorState.Content ?: return
        if (content.isSelecting) return
        val target = content.activeWorkspaceId ?: return
        launch {
            updateState {
                WorkspaceSelectorState.content.modify(this) {
                    it.copy(isSelecting = true)
                }
            }
            runCatching { selectWorkspaceUseCase(target) }
                .onSuccess {
                    postSideEffect(WorkspaceSelectorEffect.NavigateToDashboard)
                }
                .onFailure {
                    updateState {
                        WorkspaceSelectorState.content.modify(this) {
                            it.copy(isSelecting = false)
                        }
                    }
                }
        }
    }

    private fun promoteToContent(workspaces: List<Workspace>) {
        updateState {
            when (this) {
                is WorkspaceSelectorState.Loading -> WorkspaceSelectorState.Content(
                    workspaces = workspaces,
                    selectedWorkspaceId = null,
                    pendingWorkspaceId = null,
                    showActions = showActions,
                    isSelecting = false,
                    isOffline = isOffline,
                    canSignOut = canSignOut,
                )
                is WorkspaceSelectorState.Content -> copy(workspaces = workspaces)
            }
        }
    }
}

@optics
sealed interface WorkspaceSelectorState {
    val showActions: Boolean
    val isOffline: Boolean

    /** Offer a way back to sign-in — set when this screen has no other exit. */
    val canSignOut: Boolean

    @optics
    data class Loading(
        override val showActions: Boolean,
        override val isOffline: Boolean = false,
        override val canSignOut: Boolean = false,
    ) : WorkspaceSelectorState {
        companion object
    }

    @optics
    data class Content(
        val workspaces: List<Workspace>,
        val selectedWorkspaceId: WorkspaceId?,
        val pendingWorkspaceId: WorkspaceId?,
        override val showActions: Boolean,
        val isSelecting: Boolean,
        override val isOffline: Boolean = false,
        override val canSignOut: Boolean = false,
    ) : WorkspaceSelectorState {
        /**
         * The "Members" row action is remote-only — offline workspaces are always single-user,
         * so showing it would imply collaboration the offline build doesn't support.
         */
        val showMemberActions: Boolean
            get() = !isOffline

        val activeWorkspaceId: WorkspaceId?
            get() = pendingWorkspaceId ?: selectedWorkspaceId

        val activeWorkspace: Workspace?
            get() = activeWorkspaceId?.let { id -> workspaces.firstOrNull { it.id == id } }

        companion object
    }

    companion object
}

sealed interface WorkspaceSelectorEvent {
    data class OnWorkspaceClick(val workspace: Workspace) : WorkspaceSelectorEvent
    data class OnEditWorkspaceClick(val workspace: Workspace) : WorkspaceSelectorEvent
    data class OnMembersClick(val workspace: Workspace) : WorkspaceSelectorEvent
    data object OnCreateWorkspaceClick : WorkspaceSelectorEvent
    data object OnConfirmClick : WorkspaceSelectorEvent
    data object OnSignOutClick : WorkspaceSelectorEvent

    /** Toolbar back — only reachable when the selector was pushed onto a stack, i.e. from Settings. */
    data object OnBackClick : WorkspaceSelectorEvent
}

sealed interface WorkspaceSelectorEffect {
    data object NavigateBack : WorkspaceSelectorEffect
    data object NavigateToDashboard : WorkspaceSelectorEffect
    data object NavigateToSignIn : WorkspaceSelectorEffect
    data object NavigateToWorkspaceCreation : WorkspaceSelectorEffect
    data class NavigateToWorkspaceEdit(val workspaceId: WorkspaceId) : WorkspaceSelectorEffect
    data class NavigateToWorkspaceMembers(val workspaceId: WorkspaceId) : WorkspaceSelectorEffect
}
