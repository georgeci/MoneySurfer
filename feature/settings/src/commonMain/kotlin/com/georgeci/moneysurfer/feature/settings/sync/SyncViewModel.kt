package com.georgeci.moneysurfer.feature.settings.sync

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.domain.auth.SessionPointers
import com.georgeci.moneysurfer.domain.config.SyncSettings
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.sync.api.LastSyncOutcome
import com.georgeci.moneysurfer.sync.api.SyncError
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncStep
import com.georgeci.moneysurfer.sync.api.SyncSummary
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import com.georgeci.moneysurfer.sync.repository.PendingMutationQueue
import com.georgeci.moneysurfer.sync.repository.SyncMeta
import com.georgeci.moneysurfer.sync.repository.SyncMetaRepository
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.KoinViewModel
import kotlin.time.Instant
import com.georgeci.moneysurfer.sync.api.SyncState as LiveSyncState

/**
 * Read model for the sync diagnostics screen.
 *
 * Every piece was already exposed as a flow somewhere — live coordinator state, the persisted last
 * outcome, the outbox, the per-collection cursors — but nothing put them on one screen, so the
 * question "why did the pull bring nothing back" had to be answered from logs. The cursors answer
 * it directly (see docs/architecture/cloud-login-hydration.md and issue #356), which is why they
 * are here rather than in a debug-only panel.
 *
 * The screen only reads and re-renders; the two write actions are a manual sync and a cursor reset.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@KoinViewModel
class SyncViewModel(
    private val syncCoordinator: SyncCoordinator,
    private val syncSettings: SyncSettings,
    private val pendingMutationQueue: PendingMutationQueue,
    private val syncMetaRepository: SyncMetaRepository,
    private val session: SessionPointers,
) : MviViewModel<SyncState, SyncEvent, SyncEffect>(
    initialState = SyncState(),
) {

    private val log = Logger.withTag(TAG)

    init {
        observeCoordinator()
        observeOutbox()
        observeCursors()
    }

    override fun onEvent(event: SyncEvent) {
        when (event) {
            SyncEvent.OnBackClick -> postSideEffect(SyncEffect.NavigateBack)
            SyncEvent.OnSyncClick -> sync()
            SyncEvent.OnResetCursorsClick -> updateState { copy(showResetCursorsConfirm = true) }
            SyncEvent.OnResetCursorsDismissed -> updateState { copy(showResetCursorsConfirm = false) }
            SyncEvent.OnResetCursorsConfirmed -> {
                updateState { copy(showResetCursorsConfirm = false) }
                resetCursors()
            }
        }
    }

    private fun observeCoordinator() {
        launch {
            combine(syncCoordinator.state, syncCoordinator.lastOutcome, ::Pair)
                .onEach { (live, outcome) -> updateState { copy(live = live, lastOutcome = outcome) } }
                .collect()
        }
    }

    private fun observeOutbox() {
        // Sync off → the outbox can't drain and nothing on this screen can act on it, so don't hold
        // the Flow open. `flatMapLatest` rather than an early return because the server kill switch
        // can retract mid-session — same shape as SettingsViewModel's badge. The screen renders the
        // resulting empty list as "not read" rather than "nothing queued": rows survive the switch
        // flipping, so reporting an empty outbox here would be reporting a fact nobody checked.
        launch {
            syncSettings.isEnabled
                .onEach { enabled -> updateState { copy(syncEnabled = enabled) } }
                .flatMapLatest { enabled ->
                    // One row past the render limit, so "there are more" is something the screen
                    // observes rather than infers from a full page.
                    if (enabled) pendingMutationQueue.observeOutbox(OUTBOX_QUERY_LIMIT) else flowOf(emptyList())
                }
                .onEach { rows -> updateState { copy(outbox = rows) } }
                .collect()
        }
    }

    private fun observeCursors() {
        launch {
            session.currentWorkspaceId
                .onEach { workspaceId -> updateState { copy(workspaceId = workspaceId) } }
                .flatMapLatest { workspaceId ->
                    if (workspaceId == null) flowOf(emptyList()) else syncMetaRepository.observe(workspaceId.value)
                }
                .onEach { rows -> updateState { copy(cursors = rows.sortedBy { it.collection }) } }
                .collect()
        }
    }

    private fun sync() {
        launch {
            // Defensive: SettingsScreen no longer surfaces a way here when sync is off, but the
            // navigation entry stays registered — bail out so a deep link or a stale back stack
            // can't trigger an actual sync against Firestore.
            if (!syncSettings.isEnabled.first()) {
                log.i { "[sync] disabled — ignoring manual sync request" }
                return@launch
            }
            log.i { "[sync] requesting MANUAL sync via coordinator" }
            // Fire and forget: the coordinator's own `state` / `lastOutcome` flows are what this
            // screen renders, so awaiting the handle here would only duplicate them — and would
            // drop the result if the screen left composition mid-sync.
            syncCoordinator.requestSync(reason = SyncReason.MANUAL)
        }
    }

    /**
     * Drops every `lastPulledAt` for the active workspace and immediately re-syncs. Without the
     * sync the reset looks like a no-op — the cursors are only re-read on the next pull, and it is
     * that full re-pull, not the empty cursor table, that the user came here for.
     *
     * The wipe has to wait for the coordinator to go idle first. A pull in flight writes
     * `setCursor(now)` per collection as it finishes each one, and a request made now would queue
     * *behind* that pull rather than merge into it — so clearing immediately would have the running
     * sync restore fresh cursors and the queued "full" re-pull would then filter on them and bring
     * nothing back. Cancelling and waiting is what makes the action mean what it says.
     */
    private fun resetCursors() {
        val workspaceId = currentState.workspaceId
        if (workspaceId == null) {
            log.w { "[sync] no active workspace — ignoring cursor reset" }
            return
        }
        launch {
            syncCoordinator.cancelAll()
            val idle = withTimeoutOrNull(IDLE_WAIT_MS) {
                syncCoordinator.state.first { it is LiveSyncState.Idle }
            }
            if (idle == null) {
                // Cancellation is cooperative: an SDK call already in flight finishes first. Going
                // ahead is still better than dropping the user's action — worst case the reset is
                // partly overwritten, which is exactly the state they will retry from.
                log.w { "[sync] coordinator still busy after ${IDLE_WAIT_MS}ms — clearing cursors anyway" }
            }
            log.i { "[sync] clearing pull cursors for workspace ${workspaceId.value}" }
            syncMetaRepository.clearScope(workspaceId.value)
            if (syncSettings.isEnabled.first()) {
                syncCoordinator.requestSync(reason = SyncReason.MANUAL)
            }
        }
    }

    private companion object {
        const val TAG = "SyncVM"

        /** How long a cursor reset waits for a cancelled sync to actually stop. */
        const val IDLE_WAIT_MS = 5_000L

        /** One past what the screen renders — the extra row is how truncation is detected. */
        const val OUTBOX_QUERY_LIMIT = PendingMutationQueue.DEFAULT_OUTBOX_LIMIT + 1
    }
}

data class SyncState(
    val live: LiveSyncState = LiveSyncState.Idle,
    val lastOutcome: LastSyncOutcome = LastSyncOutcome.None,
    val outbox: List<PendingMutation> = emptyList(),
    val cursors: List<SyncMeta> = emptyList(),
    val workspaceId: WorkspaceId? = null,
    val syncEnabled: Boolean = false,
    val showResetCursorsConfirm: Boolean = false,
) {

    /**
     * Hero summary, derived rather than stored. Precedence is live activity, then a missing
     * workspace, then the last terminal outcome: a sync in flight is the most useful thing to say,
     * but once nothing is running, "no active workspace" outranks any earlier result — the actions
     * below are inert without one, and a hero reading "Up to date" from a sync that happened before
     * the workspace went away would leave that unexplained.
     *
     * Cancelled reads as [SyncStatus.Idle] on purpose: the user cancelled, so there is no result to
     * report back to them.
     */
    val syncStatus: SyncStatus
        get() = when (val current = live) {
            is LiveSyncState.Running -> SyncStatus.Running(current.currentStep)
            is LiveSyncState.Queued -> SyncStatus.Queued(current.count)
            LiveSyncState.Idle -> when {
                workspaceId == null -> SyncStatus.NoWorkspace
                else -> when (val outcome = lastOutcome) {
                    LastSyncOutcome.None -> SyncStatus.Idle
                    is LastSyncOutcome.Success -> SyncStatus.Done(outcome.summary, outcome.at)
                    is LastSyncOutcome.Failed -> SyncStatus.Failed(outcome.error, outcome.at)
                    is LastSyncOutcome.Cancelled -> SyncStatus.Idle
                }
            }
        }

    /** The cursor reset needs a workspace to scope the wipe to; a manual sync does not. */
    val canResetCursors: Boolean get() = workspaceId != null

    /** Rows the outbox section renders; anything past this is reported as truncated. */
    val visibleOutbox: List<PendingMutation>
        get() = outbox.take(PendingMutationQueue.DEFAULT_OUTBOX_LIMIT)

    /** True only when the queue actually held more rows than [visibleOutbox] shows. */
    val outboxTruncated: Boolean
        get() = outbox.size > PendingMutationQueue.DEFAULT_OUTBOX_LIMIT
}

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object NoWorkspace : SyncStatus
    data class Queued(val count: Int) : SyncStatus
    data class Running(val step: SyncStep) : SyncStatus
    data class Done(val summary: SyncSummary, val at: Instant) : SyncStatus
    data class Failed(val error: SyncError, val at: Instant) : SyncStatus
}

sealed interface SyncEvent {
    data object OnBackClick : SyncEvent
    data object OnSyncClick : SyncEvent
    data object OnResetCursorsClick : SyncEvent
    data object OnResetCursorsDismissed : SyncEvent
    data object OnResetCursorsConfirmed : SyncEvent
}

sealed interface SyncEffect {
    data object NavigateBack : SyncEffect
}
