package com.georgeci.moneysurfer.feature.workspace.members

import arrow.optics.optics
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.model.InviteStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceInvite
import com.georgeci.moneysurfer.domain.model.WorkspaceMember
import com.georgeci.moneysurfer.domain.model.WorkspaceMemberStatus
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.ClockUseCase
import com.georgeci.moneysurfer.domain.primitives.UserId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId
import com.georgeci.moneysurfer.domain.repositories.WorkspaceInviteRepository
import com.georgeci.moneysurfer.domain.repositories.WorkspaceMemberRepository
import com.georgeci.moneysurfer.domain.usecase.CancelInviteUseCase
import com.georgeci.moneysurfer.domain.usecase.GetWorkspacesForUserUseCase
import com.georgeci.moneysurfer.domain.usecase.InviteError
import com.georgeci.moneysurfer.domain.usecase.LeaveWorkspaceUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class WorkspaceMembersViewModel(
    workspaceId: WorkspaceId,
    private val session: SessionPointers,
    private val getWorkspacesForUserUseCase: GetWorkspacesForUserUseCase,
    private val memberRepository: WorkspaceMemberRepository,
    private val inviteRepository: WorkspaceInviteRepository,
    private val leaveWorkspace: LeaveWorkspaceUseCase,
    private val cancelInvite: CancelInviteUseCase,
    private val clock: ClockUseCase,
) : MviViewModel<WorkspaceMembersState, WorkspaceMembersEvent, WorkspaceMembersEffect>(
    initialState = WorkspaceMembersState.Loading(workspaceId),
) {

    init { observeWorkspace() }

    override fun onEvent(event: WorkspaceMembersEvent) {
        when (event) {
            WorkspaceMembersEvent.OnDismiss -> postSideEffect(WorkspaceMembersEffect.Dismiss)
            WorkspaceMembersEvent.OnInviteClick -> postSideEffect(WorkspaceMembersEffect.OpenInvite)
            is WorkspaceMembersEvent.OnTabSelected -> selectTab(event.tab)
            is WorkspaceMembersEvent.OnMemberClick -> openMemberActions(event.userId)
            WorkspaceMembersEvent.OnLeaveClick -> updateContent { copy(showLeaveDialog = true) }
            WorkspaceMembersEvent.OnLeaveCancel -> updateContent { copy(showLeaveDialog = false) }
            WorkspaceMembersEvent.OnLeaveConfirm -> performLeave()
            is WorkspaceMembersEvent.OnInviteCancel -> performCancelInvite(event.id)
        }
    }

    private fun openMemberActions(userId: UserId) {
        val state = WorkspaceMembersState.content.getOrNull(currentState) ?: return
        val target = state.members.firstOrNull { it.userId == userId } ?: return

        if (state.canManage && !target.isYou && target.role != WorkspaceRole.OWNER) {
            postSideEffect(
                WorkspaceMembersEffect.OpenMemberActions(state.workspaceId, userId),
            )
        }
    }

    private fun performLeave() {
        val state = currentState as? WorkspaceMembersState.Content ?: return
        if (state.busy) return
        updateContent { copy(busy = true) }
        launch(onError = { reportFailure(InviteError.LocalWriteFailed(it)) }) {
            leaveWorkspace(state.workspaceId).fold(
                ifLeft = { reportFailure(it) },
                ifRight = {
                    updateContent { copy(busy = false, showLeaveDialog = false) }
                    postSideEffect(WorkspaceMembersEffect.LeftWorkspace)
                },
            )
        }
    }

    private fun performCancelInvite(id: WorkspaceInviteId) {
        val state = currentState as? WorkspaceMembersState.Content ?: return
        if (state.busy) return
        updateContent { copy(busy = true) }
        launch(onError = { reportFailure(InviteError.LocalWriteFailed(it)) }) {
            cancelInvite(id).fold(
                ifLeft = { reportFailure(it) },
                ifRight = { updateContent { copy(busy = false) } },
            )
        }
    }

    private fun reportFailure(error: InviteError) {
        updateContent { copy(busy = false, showLeaveDialog = false) }
        postSideEffect(WorkspaceMembersEffect.ShowError(error))
    }

    private fun selectTab(tab: MembersTab) {
        updateContent { if (this.tab == tab) this else copy(tab = tab) }
    }

    private inline fun updateContent(
        crossinline block: WorkspaceMembersState.Content.() -> WorkspaceMembersState.Content,
    ) {
        updateState { if (this is WorkspaceMembersState.Content) block() else this }
    }

    private fun observeWorkspace() {
        launch {
            val workspaceId = currentState.workspaceId
            val userId = session.currentUserId.flow.first()
            val workspace = userId
                ?.let { getWorkspacesForUserUseCase(it).first().firstOrNull { ws -> ws.id == workspaceId } }

            val membersFlow = memberRepository.getByWorkspaceId(workspaceId)
            val invitesFlow = inviteRepository.getByWorkspaceId(workspaceId)

            combine(membersFlow, invitesFlow) { members, invites -> members to invites }
                .collect { (members, invites) ->
                    val activeMembers = members.filter { it.status == WorkspaceMemberStatus.ACTIVE }
                    val pendingInvites = invites
                        .filter { it.status == InviteStatus.PENDING }
                        .sortedByDescending { it.createdAt }
                    val now = clock.now()
                    val viewerRole = activeMembers.firstOrNull { it.userId == userId }?.role

                    updateState {
                        val previous = this as? WorkspaceMembersState.Content
                        WorkspaceMembersState.Content(
                            workspaceId = workspaceId,
                            workspaceName = workspace?.name.orEmpty(),
                            currentUserId = userId,
                            viewerRole = viewerRole,
                            members = activeMembers.map { it.toUi(userId) },
                            pendingInvites = pendingInvites.map { it.toUi(now) },
                            tab = previous?.tab ?: MembersTab.Active,
                            busy = previous?.busy ?: false,
                            showLeaveDialog = previous?.showLeaveDialog ?: false,
                        )
                    }
                }
        }
    }
}

private fun WorkspaceMember.toUi(currentUserId: UserId?): MemberUi = MemberUi(
    userId = userId,
    name = displayName.ifBlank { email.orEmpty() }.ifBlank { "Member" },
    email = email.orEmpty(),
    role = role,
    initial = (displayName.firstOrNull() ?: email?.firstOrNull() ?: '?').uppercaseChar().toString(),
    isYou = userId == currentUserId,
)

private fun WorkspaceInvite.toUi(now: kotlin.time.Instant): InviteUi = InviteUi(
    id = id,
    email = email,
    role = role,
    isExpired = expiresAt <= now,
    expiresAt = expiresAt,
)

@optics
sealed interface WorkspaceMembersState {
    val workspaceId: WorkspaceId

    @optics
    data class Loading(override val workspaceId: WorkspaceId) : WorkspaceMembersState {
        companion object
    }

    @optics
    data class Content(
        override val workspaceId: WorkspaceId,
        val workspaceName: String,
        val currentUserId: UserId?,
        val viewerRole: WorkspaceRole?,
        val members: List<MemberUi>,
        val pendingInvites: List<InviteUi>,
        val tab: MembersTab,
        val busy: Boolean,
        val showLeaveDialog: Boolean,
    ) : WorkspaceMembersState {
        val canManage: Boolean get() = viewerRole == WorkspaceRole.OWNER
        val canLeave: Boolean get() = viewerRole != null && viewerRole != WorkspaceRole.OWNER
        val activeCount: Int get() = members.size
        val invitedCount: Int get() = pendingInvites.count { !it.isExpired }
        companion object
    }

    companion object
}

data class MemberUi(
    val userId: UserId,
    val name: String,
    val email: String,
    val role: WorkspaceRole,
    val initial: String,
    val isYou: Boolean,
)

data class InviteUi(
    val id: WorkspaceInviteId,
    val email: String,
    val role: WorkspaceRole,
    val isExpired: Boolean,
    val expiresAt: kotlin.time.Instant,
)

enum class MembersTab { Active, Invited }

sealed interface WorkspaceMembersEvent {
    data object OnDismiss : WorkspaceMembersEvent
    data object OnInviteClick : WorkspaceMembersEvent
    data class OnTabSelected(val tab: MembersTab) : WorkspaceMembersEvent
    data class OnMemberClick(val userId: UserId) : WorkspaceMembersEvent
    data object OnLeaveClick : WorkspaceMembersEvent
    data object OnLeaveCancel : WorkspaceMembersEvent
    data object OnLeaveConfirm : WorkspaceMembersEvent
    data class OnInviteCancel(val id: WorkspaceInviteId) : WorkspaceMembersEvent
}

sealed interface WorkspaceMembersEffect {
    data object Dismiss : WorkspaceMembersEffect
    data object OpenInvite : WorkspaceMembersEffect
    data class OpenMemberActions(
        val workspaceId: WorkspaceId,
        val targetUserId: UserId,
    ) : WorkspaceMembersEffect
    data object LeftWorkspace : WorkspaceMembersEffect
    data class ShowError(val error: InviteError) : WorkspaceMembersEffect
}
