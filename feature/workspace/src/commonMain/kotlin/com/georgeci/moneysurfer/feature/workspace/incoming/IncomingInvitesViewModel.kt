package com.georgeci.moneysurfer.feature.workspace.incoming

import arrow.optics.optics
import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.model.WorkspaceRole
import com.georgeci.moneysurfer.domain.primitives.WorkspaceInviteId
import com.georgeci.moneysurfer.domain.repositories.WorkspaceRepository
import com.georgeci.moneysurfer.domain.usecase.AcceptInviteUseCase
import com.georgeci.moneysurfer.domain.usecase.DeclineInviteUseCase
import com.georgeci.moneysurfer.domain.usecase.InviteError
import com.georgeci.moneysurfer.domain.usecase.ListPendingInvitesForCurrentUserUseCase
import com.georgeci.moneysurfer.domain.usecase.RefreshIncomingInvitesUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Clock

@KoinViewModel
class IncomingInvitesViewModel(
    private val listPending: ListPendingInvitesForCurrentUserUseCase,
    private val refreshIncomingInvites: RefreshIncomingInvitesUseCase,
    private val acceptInvite: AcceptInviteUseCase,
    private val declineInvite: DeclineInviteUseCase,
    private val workspaceRepository: WorkspaceRepository,
) : MviViewModel<IncomingInvitesState, IncomingInvitesEvent, IncomingInvitesEffect>(
    initialState = IncomingInvitesState(),
) {

    private val log = Logger.withTag(TAG)

    init {
        log.i { "[init] starting observe + initial refresh" }
        observe()
        refresh()
    }

    private fun refresh() {
        if (currentState.refreshing) {
            log.d { "[refresh:skip] already refreshing" }
            return
        }
        log.i { "[refresh:start]" }
        val startedAt = Clock.System.now().toEpochMilliseconds()
        updateState { copy(refreshing = true, refreshError = null) }
        launch(
            onError = {
                log.e(it) { "[refresh:throw] ${it.message}" }
                updateState { copy(refreshing = false, refreshError = InviteError.LocalWriteFailed(it)) }
            },
        ) {
            refreshIncomingInvites().fold(
                ifLeft = { error ->
                    val took = Clock.System.now().toEpochMilliseconds() - startedAt
                    log.w { "[refresh:left] error=${error::class.simpleName} took=${took}ms" }
                    // Surface as state, not a snackbar — refresh runs on resume too,
                    // a snackbar per resume on a flaky network would be spammy. The
                    // banner stays until the next successful refresh clears it.
                    updateState { copy(refreshing = false, refreshError = error) }
                },
                ifRight = { count ->
                    val took = Clock.System.now().toEpochMilliseconds() - startedAt
                    log.i { "[refresh:done] pulled=$count took=${took}ms" }
                    updateState { copy(refreshing = false, refreshError = null) }
                },
            )
        }
    }

    override fun onEvent(event: IncomingInvitesEvent) {
        log.d { "[event] $event" }
        when (event) {
            IncomingInvitesEvent.OnBackClick -> postSideEffect(IncomingInvitesEffect.NavigateBack)
            IncomingInvitesEvent.OnRefresh -> refresh()
            is IncomingInvitesEvent.OnAccept -> accept(event.id)
            is IncomingInvitesEvent.OnDecline -> decline(event.id)
        }
    }

    private fun observe() {
        log.d { "[observe:start] subscribing to listPending" }
        launch {
            listPending().collect { invites ->
                val now = Clock.System.now().toEpochMilliseconds()
                val items = invites.map { invite ->
                    // workspaceName resolves only for workspaces the user is already a member of —
                    // i.e. nearly never for incoming invites, since the recipient becomes a member
                    // only after accept. The UI falls back to a short workspace-id pill (see
                    // IncomingInvitesScreen.InviteCard). Proper fix: denormalize a workspace-name
                    // snapshot into the invite document at send-time so recipients see something
                    // human-readable. Tracked separately — left as-is for now.
                    val workspaceName = workspaceRepository.getById(invite.workspaceId)?.name
                    IncomingInviteUi(
                        id = invite.id,
                        workspaceName = workspaceName.orEmpty(),
                        workspaceIdShort = invite.workspaceId.value.take(WORKSPACE_ID_PREFIX_LEN),
                        role = invite.role,
                        invitedByUserId = invite.invitedByUserId.value.take(USER_ID_PREFIX_LEN),
                        expired = now > invite.expiresAt,
                        expiresAt = invite.expiresAt,
                    )
                }.sortedBy { it.expired }
                val active = items.count { !it.expired }
                val expired = items.size - active
                val resolvedNames = items.count { it.workspaceName.isNotEmpty() }
                log.i {
                    "[observe:emit] total=${items.size} active=$active expired=$expired " +
                        "resolvedWorkspaceNames=$resolvedNames/${items.size}"
                }
                updateState { copy(invites = items, loading = false) }
            }
        }
    }

    private fun accept(id: WorkspaceInviteId) {
        if (currentState.busyId != null) {
            log.d { "[accept:skip] busy id=${currentState.busyId?.value}, ignoring tap on ${id.value}" }
            return
        }
        log.i { "[accept:start] id=${id.value}" }
        val startedAt = Clock.System.now().toEpochMilliseconds()
        updateState { copy(busyId = id) }
        launch(
            onError = {
                log.e(it) { "[accept:throw] id=${id.value} ${it.message}" }
                reportFailure(InviteError.LocalWriteFailed(it))
            },
        ) {
            acceptInvite(id).fold(
                ifLeft = { error ->
                    val took = Clock.System.now().toEpochMilliseconds() - startedAt
                    log.w { "[accept:left] id=${id.value} error=${error::class.simpleName} took=${took}ms" }
                    reportFailure(error)
                },
                ifRight = {
                    val took = Clock.System.now().toEpochMilliseconds() - startedAt
                    log.i { "[accept:done] id=${id.value} took=${took}ms" }
                    updateState { copy(busyId = null) }
                },
            )
        }
    }

    private fun decline(id: WorkspaceInviteId) {
        if (currentState.busyId != null) {
            log.d { "[decline:skip] busy id=${currentState.busyId?.value}, ignoring tap on ${id.value}" }
            return
        }
        log.i { "[decline:start] id=${id.value}" }
        val startedAt = Clock.System.now().toEpochMilliseconds()
        updateState { copy(busyId = id) }
        launch(
            onError = {
                log.e(it) { "[decline:throw] id=${id.value} ${it.message}" }
                reportFailure(InviteError.LocalWriteFailed(it))
            },
        ) {
            declineInvite(id).fold(
                ifLeft = { error ->
                    val took = Clock.System.now().toEpochMilliseconds() - startedAt
                    log.w { "[decline:left] id=${id.value} error=${error::class.simpleName} took=${took}ms" }
                    reportFailure(error)
                },
                ifRight = {
                    val took = Clock.System.now().toEpochMilliseconds() - startedAt
                    log.i { "[decline:done] id=${id.value} took=${took}ms" }
                    updateState { copy(busyId = null) }
                },
            )
        }
    }

    private fun reportFailure(error: InviteError) {
        log.w { "[failure:report] ${error::class.simpleName}" }
        updateState { copy(busyId = null) }
        postSideEffect(IncomingInvitesEffect.ShowError(error))
    }

    private companion object {
        const val TAG = "IncomingInvitesVM"
        const val WORKSPACE_ID_PREFIX_LEN = 8
        const val USER_ID_PREFIX_LEN = 6
    }
}

@optics
data class IncomingInvitesState(
    val loading: Boolean = true,
    val invites: List<IncomingInviteUi> = emptyList(),
    val busyId: WorkspaceInviteId? = null,
    val refreshing: Boolean = false,
    val refreshError: InviteError? = null,
) {
    companion object
}

data class IncomingInviteUi(
    val id: WorkspaceInviteId,
    val workspaceName: String,
    val workspaceIdShort: String,
    val role: WorkspaceRole,
    val invitedByUserId: String,
    val expired: Boolean,
    val expiresAt: Long,
)

sealed interface IncomingInvitesEvent {
    data object OnBackClick : IncomingInvitesEvent
    data object OnRefresh : IncomingInvitesEvent
    data class OnAccept(val id: WorkspaceInviteId) : IncomingInvitesEvent
    data class OnDecline(val id: WorkspaceInviteId) : IncomingInvitesEvent
}

sealed interface IncomingInvitesEffect {
    data object NavigateBack : IncomingInvitesEffect
    data class ShowError(val error: InviteError) : IncomingInvitesEffect
}
