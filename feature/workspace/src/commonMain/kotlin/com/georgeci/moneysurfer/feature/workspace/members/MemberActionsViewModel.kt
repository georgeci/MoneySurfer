package com.georgeci.moneysurfer.feature.workspace.members

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import com.georgeci.moneysurfer.domain.usecase.ChangeMemberRoleUseCase
import com.georgeci.moneysurfer.domain.usecase.InviteError
import com.georgeci.moneysurfer.domain.usecase.RemoveMemberUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class MemberActionsViewModel(
    workspaceId: WorkspaceId,
    targetUserId: UserId,
    private val memberRepository: WorkspaceMemberRepository,
    private val changeRole: ChangeMemberRoleUseCase,
    private val removeMember: RemoveMemberUseCase,
) : MviViewModel<MemberActionsState, MemberActionsEvent, MemberActionsEffect>(
    initialState = MemberActionsState.Loading(workspaceId, targetUserId),
) {

    init { load() }

    override fun onEvent(event: MemberActionsEvent) {
        when (event) {
            MemberActionsEvent.OnDismiss -> postSideEffect(MemberActionsEffect.Dismiss)
            is MemberActionsEvent.OnRoleSelected -> changeRoleTo(event.role)
            MemberActionsEvent.OnRemoveClick -> updateState {
                if (this is MemberActionsState.Content) copy(showConfirmRemove = true) else this
            }
            MemberActionsEvent.OnRemoveCancel -> updateState {
                if (this is MemberActionsState.Content) copy(showConfirmRemove = false) else this
            }
            MemberActionsEvent.OnRemoveConfirm -> performRemove()
        }
    }

    private fun load() {
        launch(onError = { postSideEffect(MemberActionsEffect.Dismiss) }) {
            val members = memberRepository.getByWorkspaceId(currentState.workspaceId).first()
            val target = members.firstOrNull { it.userId == currentState.targetUserId }
                ?: run {
                    postSideEffect(MemberActionsEffect.Dismiss)
                    return@launch
                }
            updateState {
                MemberActionsState.Content(
                    workspaceId = currentState.workspaceId,
                    targetUserId = currentState.targetUserId,
                    name = target.displayName.ifBlank { target.email.orEmpty() },
                    email = target.email.orEmpty(),
                    role = target.role,
                    status = target.status,
                    isBusy = false,
                    showConfirmRemove = false,
                )
            }
        }
    }

    private fun changeRoleTo(newRole: WorkspaceRole) {
        val state = currentState as? MemberActionsState.Content ?: return
        if (state.isBusy || state.role == newRole) return
        updateState { if (this is MemberActionsState.Content) copy(isBusy = true) else this }
        launch(onError = { reportFailure(InviteError.LocalWriteFailed(it)) }) {
            changeRole(
                ChangeMemberRoleUseCase.Params(
                    workspaceId = state.workspaceId,
                    targetUserId = state.targetUserId,
                    newRole = newRole,
                ),
            ).fold(
                ifLeft = { reportFailure(it) },
                ifRight = {
                    updateState {
                        if (this is MemberActionsState.Content) copy(isBusy = false, role = newRole) else this
                    }
                },
            )
        }
    }

    private fun performRemove() {
        val state = currentState as? MemberActionsState.Content ?: return
        if (state.isBusy) return
        updateState { if (this is MemberActionsState.Content) copy(isBusy = true) else this }
        launch(onError = { reportFailure(InviteError.LocalWriteFailed(it)) }) {
            removeMember(
                RemoveMemberUseCase.Params(
                    workspaceId = state.workspaceId,
                    targetUserId = state.targetUserId,
                ),
            ).fold(
                ifLeft = { reportFailure(it) },
                ifRight = {
                    updateState {
                        if (this is MemberActionsState.Content) {
                            copy(isBusy = false, showConfirmRemove = false)
                        } else {
                            this
                        }
                    }
                    postSideEffect(MemberActionsEffect.Dismiss)
                },
            )
        }
    }

    private fun reportFailure(error: InviteError) {
        updateState {
            if (this is MemberActionsState.Content) copy(isBusy = false, showConfirmRemove = false) else this
        }
        postSideEffect(MemberActionsEffect.ShowError(error))
    }
}

@optics
sealed interface MemberActionsState {
    val workspaceId: WorkspaceId
    val targetUserId: UserId

    @optics
    data class Loading(
        override val workspaceId: WorkspaceId,
        override val targetUserId: UserId,
    ) : MemberActionsState {
        companion object
    }

    @optics
    data class Content(
        override val workspaceId: WorkspaceId,
        override val targetUserId: UserId,
        val name: String,
        val email: String,
        val role: WorkspaceRole,
        val status: WorkspaceMemberStatus,
        val isBusy: Boolean,
        val showConfirmRemove: Boolean,
    ) : MemberActionsState {
        val canRemove: Boolean get() = role != WorkspaceRole.OWNER
        companion object
    }

    companion object
}

sealed interface MemberActionsEvent {
    data object OnDismiss : MemberActionsEvent
    data class OnRoleSelected(val role: WorkspaceRole) : MemberActionsEvent
    data object OnRemoveClick : MemberActionsEvent
    data object OnRemoveCancel : MemberActionsEvent
    data object OnRemoveConfirm : MemberActionsEvent
}

sealed interface MemberActionsEffect {
    data object Dismiss : MemberActionsEffect
    data class ShowError(val error: InviteError) : MemberActionsEffect
}
