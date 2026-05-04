package com.georgeci.moneysurfer.feature.workspace.invite

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.usecase.InviteError
import com.georgeci.moneysurfer.domain.usecase.SendInviteUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class WorkspaceInviteViewModel(
    workspaceId: WorkspaceId,
    private val sendInvite: SendInviteUseCase,
) : MviViewModel<WorkspaceInviteState, WorkspaceInviteEvent, WorkspaceInviteEffect>(
    initialState = WorkspaceInviteState(workspaceId = workspaceId),
) {

    override fun onEvent(event: WorkspaceInviteEvent) {
        when (event) {
            WorkspaceInviteEvent.OnDismiss -> postSideEffect(WorkspaceInviteEffect.Dismiss)
            is WorkspaceInviteEvent.OnEmailChanged -> updateState {
                copy(email = event.email, emailError = null)
            }
            is WorkspaceInviteEvent.OnRoleSelected -> updateState { copy(role = event.role) }
            WorkspaceInviteEvent.OnSubmit -> submit()
        }
    }

    private fun submit() {
        val state = currentState
        if (state.isSubmitting) return
        updateState { copy(isSubmitting = true, emailError = null) }
        launch(onError = { handleFailure(InviteError.LocalWriteFailed(it)) }) {
            sendInvite(
                SendInviteUseCase.Params(
                    workspaceId = state.workspaceId,
                    email = state.email,
                    role = state.role,
                ),
            ).fold(
                ifLeft = { handleFailure(it) },
                ifRight = {
                    updateState { copy(isSubmitting = false) }
                    postSideEffect(WorkspaceInviteEffect.Sent(state.email.trim().lowercase()))
                },
            )
        }
    }

    private fun handleFailure(error: InviteError) {
        updateState { copy(isSubmitting = false, emailError = mapToFieldError(error)) }
        if (mapToFieldError(error) == null) {
            postSideEffect(WorkspaceInviteEffect.ShowError(error))
        }
    }

    private fun mapToFieldError(error: InviteError): InviteFieldError? = when (error) {
        InviteError.InvalidEmail -> InviteFieldError.Invalid
        is InviteError.AlreadyMember -> InviteFieldError.AlreadyMember
        is InviteError.AlreadyInvited -> InviteFieldError.AlreadyInvited
        is InviteError.UserNotFound -> InviteFieldError.UserNotFound
        else -> null
    }
}

@optics
data class WorkspaceInviteState(
    val workspaceId: WorkspaceId,
    val email: String = "",
    val role: WorkspaceRole = WorkspaceRole.EDITOR,
    val isSubmitting: Boolean = false,
    val emailError: InviteFieldError? = null,
) {
    companion object
}

enum class InviteFieldError { Invalid, AlreadyMember, AlreadyInvited, UserNotFound }

sealed interface WorkspaceInviteEvent {
    data object OnDismiss : WorkspaceInviteEvent
    data class OnEmailChanged(val email: String) : WorkspaceInviteEvent
    data class OnRoleSelected(val role: WorkspaceRole) : WorkspaceInviteEvent
    data object OnSubmit : WorkspaceInviteEvent
}

sealed interface WorkspaceInviteEffect {
    data object Dismiss : WorkspaceInviteEffect
    data class Sent(val email: String) : WorkspaceInviteEffect
    data class ShowError(val error: InviteError) : WorkspaceInviteEffect
}
