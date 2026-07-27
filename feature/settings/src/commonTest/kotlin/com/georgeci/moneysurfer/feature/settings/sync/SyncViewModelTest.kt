package com.georgeci.moneysurfer.feature.settings.sync

import app.cash.turbine.test
import arrow.core.right
import com.georgeci.moneysurfer.domain.auth.InMemorySessionPointers
import com.georgeci.moneysurfer.domain.fixtures.FakeSyncSettings
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
import com.georgeci.moneysurfer.sync.api.LastSyncOutcome
import com.georgeci.moneysurfer.sync.api.SyncError
import com.georgeci.moneysurfer.sync.api.SyncHandleStatus
import com.georgeci.moneysurfer.sync.api.SyncMode
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncResult
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.api.SyncStep
import com.georgeci.moneysurfer.sync.api.SyncSummary
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import com.georgeci.moneysurfer.sync.coordinator.SyncHandle
import com.georgeci.moneysurfer.sync.repository.MutationOperation
import com.georgeci.moneysurfer.sync.repository.PendingMutation
import com.georgeci.moneysurfer.sync.repository.PendingMutationQueue
import com.georgeci.moneysurfer.sync.repository.SyncMeta
import com.georgeci.moneysurfer.sync.repository.SyncMetaRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import com.georgeci.moneysurfer.sync.api.SyncState as LiveSyncState

private val WORKSPACE = WorkspaceId("ws-1")
private val AT = Instant.fromEpochMilliseconds(1_700_000_000_000)

/**
 * The sync panel is a read model over four independent sources, so what is worth pinning is which
 * source wins where: live activity outranks the last outcome in the hero, the outbox is not observed
 * while sync is off, and both write actions are gated — the manual sync on the sync setting, the
 * cursor reset on there being a workspace to scope it to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncViewModelTest : StringSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    "live coordinator state and the last outcome both reach the screen" {
        val env = VmEnv()

        env.coordinator.live.value = LiveSyncState.Queued(count = 2)
        env.coordinator.outcome.value = LastSyncOutcome.Success(SyncSummary(uploadedCount = 3), AT)

        env.viewModel.currentState.live shouldBe LiveSyncState.Queued(count = 2)
        env.viewModel.currentState.lastOutcome shouldBe
            LastSyncOutcome.Success(SyncSummary(uploadedCount = 3), AT)
    }

    "a running sync outranks the last outcome in the hero" {
        // Otherwise the hero would report the previous result while a sync is visibly in flight.
        val state = SyncState(
            live = LiveSyncState.Running(
                requestId = SyncRequestId("req-1"),
                reasons = setOf(SyncReason.MANUAL),
                scope = SyncScope.AllUserData,
                currentStep = SyncStep.PullingRemoteChanges,
            ),
            lastOutcome = LastSyncOutcome.Failed(SyncError.NetworkUnavailable, AT),
            workspaceId = WORKSPACE,
        )

        state.syncStatus shouldBe SyncStatus.Running(SyncStep.PullingRemoteChanges)
    }

    "an idle coordinator reports the last outcome, and a cancelled one reports nothing" {
        val base = SyncState(workspaceId = WORKSPACE)

        base.copy(lastOutcome = LastSyncOutcome.Success(SyncSummary(downloadedCount = 4), AT))
            .syncStatus shouldBe SyncStatus.Done(SyncSummary(downloadedCount = 4), AT)
        base.copy(lastOutcome = LastSyncOutcome.Failed(SyncError.PermissionDenied, AT))
            .syncStatus shouldBe SyncStatus.Failed(SyncError.PermissionDenied, AT)
        base.copy(lastOutcome = LastSyncOutcome.Cancelled(AT)).syncStatus shouldBe SyncStatus.Idle
        base.syncStatus shouldBe SyncStatus.Idle
    }

    "a missing workspace outranks any earlier outcome once nothing is running" {
        // Otherwise the hero keeps reporting "Up to date" from a sync that ran before the workspace
        // went away, while the actions below it are silently inert.
        SyncState(workspaceId = null).syncStatus shouldBe SyncStatus.NoWorkspace
        SyncState(workspaceId = null).canResetCursors shouldBe false

        SyncState(
            workspaceId = null,
            lastOutcome = LastSyncOutcome.Success(SyncSummary(uploadedCount = 3), AT),
        ).syncStatus shouldBe SyncStatus.NoWorkspace

        SyncState(
            workspaceId = null,
            lastOutcome = LastSyncOutcome.Failed(SyncError.AuthRequired, AT),
        ).syncStatus shouldBe SyncStatus.NoWorkspace
    }

    "live activity still outranks a missing workspace — a running sync is the more useful answer" {
        SyncState(
            workspaceId = null,
            live = LiveSyncState.Queued(count = 1),
        ).syncStatus shouldBe SyncStatus.Queued(count = 1)
    }

    "the outbox is truncated only when the queue held more rows than the screen shows" {
        val limit = PendingMutationQueue.DEFAULT_OUTBOX_LIMIT
        val exactlyFull = SyncState(outbox = List(limit) { mutation("m-$it") })

        exactlyFull.outboxTruncated shouldBe false
        exactlyFull.visibleOutbox shouldHaveSize limit

        // The view model reads one row past the limit precisely so this case is observed, not
        // guessed from a full page.
        val overflowing = SyncState(outbox = List(limit + 1) { mutation("m-$it") })

        overflowing.outboxTruncated shouldBe true
        overflowing.visibleOutbox shouldHaveSize limit
    }

    "the outbox stays unobserved while sync is off, and fills in when the switch flips" {
        runTest {
            val env = VmEnv(syncEnabled = false)
            env.queue.rows.value = listOf(mutation(id = "m-1"))

            env.viewModel.currentState.outbox shouldBe emptyList()

            env.syncSettings.set(enabled = true)

            env.viewModel.currentState.outbox.map { it.id } shouldBe listOf("m-1")
        }
    }

    "cursors are scoped to the active workspace and ordered by collection" {
        val env = VmEnv()
        env.meta.rows.value = listOf(meta("transactions"), meta("accounts"))

        env.meta.observedScopes shouldBe listOf(WORKSPACE.value)
        env.viewModel.currentState.cursors.map { it.collection } shouldBe listOf("accounts", "transactions")
    }

    "a manual sync is dropped while sync is disabled" {
        // A deep link or a restored back stack can reach this screen with sync off; requesting one
        // anyway would hit Firestore behind a switch that says it must not.
        val env = VmEnv(syncEnabled = false)

        env.viewModel.onEvent(SyncEvent.OnSyncClick)

        env.coordinator.requests shouldBe emptyList()
    }

    "a manual sync reaches the coordinator as a MANUAL request" {
        val env = VmEnv(syncEnabled = true)

        env.viewModel.onEvent(SyncEvent.OnSyncClick)

        env.coordinator.requests shouldBe listOf(SyncReason.MANUAL)
    }

    "resetting cursors asks first" {
        val env = VmEnv(syncEnabled = true)

        env.viewModel.onEvent(SyncEvent.OnResetCursorsClick)

        env.viewModel.currentState.showResetCursorsConfirm shouldBe true
        env.meta.clearedScopes shouldBe emptyList()

        env.viewModel.onEvent(SyncEvent.OnResetCursorsDismissed)

        env.viewModel.currentState.showResetCursorsConfirm shouldBe false
        env.meta.clearedScopes shouldBe emptyList()
    }

    "a confirmed reset clears the scope and re-syncs, because the cursors alone change nothing" {
        val env = VmEnv(syncEnabled = true)

        env.viewModel.onEvent(SyncEvent.OnResetCursorsClick)
        env.viewModel.onEvent(SyncEvent.OnResetCursorsConfirmed)

        env.viewModel.currentState.showResetCursorsConfirm shouldBe false
        env.meta.clearedScopes shouldBe listOf(WORKSPACE.value)
        env.coordinator.requests shouldBe listOf(SyncReason.MANUAL)
    }

    "a reset cancels a running sync before wiping, so the wipe can't be overwritten" {
        // A pull in flight writes setCursor(now) per collection as it goes, and the re-pull request
        // queues behind it — clearing first would be undone before the re-pull ever ran.
        val coordinator = FakeSyncCoordinator().apply { running() }
        val env = VmEnv(syncEnabled = true, coordinator = coordinator)

        env.viewModel.onEvent(SyncEvent.OnResetCursorsConfirmed)

        coordinator.cancelAllCount shouldBe 1
        env.meta.clearedScopes shouldBe listOf(WORKSPACE.value)
        env.coordinator.requests shouldBe listOf(SyncReason.MANUAL)
    }

    "a reset whose cancel never lands still clears the cursors rather than dropping the action" {
        runTest {
            // viewModelScope has to run on this test's scheduler, otherwise the idle wait sits on a
            // clock `advanceTimeBy` below never touches.
            Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
            try {
                val coordinator = FakeSyncCoordinator(stopsOnCancel = false).apply { running() }
                val env = VmEnv(syncEnabled = true, coordinator = coordinator)

                env.viewModel.onEvent(SyncEvent.OnResetCursorsConfirmed)

                // Nothing happens until the wait times out — the reset is deferred, not skipped.
                env.meta.clearedScopes shouldBe emptyList()

                advanceTimeBy(6.seconds)

                coordinator.cancelAllCount shouldBe 1
                env.meta.clearedScopes shouldBe listOf(WORKSPACE.value)
                env.coordinator.requests shouldBe listOf(SyncReason.MANUAL)
            } finally {
                Dispatchers.setMain(UnconfinedTestDispatcher())
            }
        }
    }

    "a confirmed reset with sync off still clears the cursors but requests no sync" {
        val env = VmEnv(syncEnabled = false)

        env.viewModel.onEvent(SyncEvent.OnResetCursorsConfirmed)

        env.meta.clearedScopes shouldBe listOf(WORKSPACE.value)
        env.coordinator.requests shouldBe emptyList()
    }

    "a reset without an active workspace touches nothing" {
        val env = VmEnv(syncEnabled = true, workspaceId = null)

        env.viewModel.onEvent(SyncEvent.OnResetCursorsConfirmed)

        env.meta.clearedScopes shouldBe emptyList()
        env.coordinator.requests shouldBe emptyList()
    }

    "back navigates away" {
        runTest {
            val env = VmEnv()

            env.viewModel.sideEffects.effectFlow.test {
                env.viewModel.onEvent(SyncEvent.OnBackClick)

                awaitItem().shouldBeInstanceOf<SyncEffect.NavigateBack>()
            }
        }
    }
})

private class VmEnv(
    syncEnabled: Boolean = false,
    workspaceId: WorkspaceId? = WORKSPACE,
    val coordinator: FakeSyncCoordinator = FakeSyncCoordinator(),
) {
    val queue = FakePendingMutationQueue()
    val meta = FakeSyncMetaRepository()
    val syncSettings = FakeSyncSettings(enabled = syncEnabled)

    val viewModel = SyncViewModel(
        syncCoordinator = coordinator,
        syncSettings = syncSettings,
        pendingMutationQueue = queue,
        syncMetaRepository = meta,
        session = InMemorySessionPointers(currentWorkspaceId = workspaceId),
    )
}

private class FakeSyncCoordinator(
    /** When false, [cancelAll] leaves the coordinator Running — the "cancel didn't take" branch. */
    private val stopsOnCancel: Boolean = true,
) : SyncCoordinator {
    val live = MutableStateFlow<LiveSyncState>(LiveSyncState.Idle)
    val outcome = MutableStateFlow<LastSyncOutcome>(LastSyncOutcome.None)
    val requests = mutableListOf<SyncReason>()
    var cancelAllCount: Int = 0
        private set

    override val state: StateFlow<LiveSyncState> = live
    override val lastOutcome: StateFlow<LastSyncOutcome> = outcome

    override fun requestSync(reason: SyncReason, mode: SyncMode): SyncHandle {
        requests += reason
        return CompletedSyncHandle
    }

    fun running() {
        live.value = LiveSyncState.Running(
            requestId = SyncRequestId("req-running"),
            reasons = setOf(SyncReason.FOREGROUND),
            scope = SyncScope.ActiveWorkspace,
            currentStep = SyncStep.PullingRemoteChanges,
        )
    }

    override fun cancelCurrent() = Unit
    override fun cancelAllQueued() = Unit

    override fun cancelAll() {
        cancelAllCount++
        if (stopsOnCancel) live.value = LiveSyncState.Idle
    }
}

/** The screen never awaits the handle — it renders the coordinator's flows — so one stub suffices. */
private object CompletedSyncHandle : SyncHandle {
    private val summary = SyncSummary()
    override val id: SyncRequestId = SyncRequestId("req-stub")
    override val status: StateFlow<SyncHandleStatus> = MutableStateFlow(SyncHandleStatus.Completed(summary))
    override val steps: SharedFlow<SyncStep> = MutableSharedFlow()
    override val result: Deferred<SyncResult<SyncSummary>> =
        CompletableDeferred<SyncResult<SyncSummary>>().apply { complete(summary.right()) }

    override fun cancel() = Unit
}

private class FakePendingMutationQueue : PendingMutationQueue {
    val rows = MutableStateFlow<List<PendingMutation>>(emptyList())

    override suspend fun enqueue(mutation: PendingMutation) = error("unused")
    override suspend fun pending(scope: SyncScope, limit: Int): List<PendingMutation> = error("unused")
    override suspend fun markInFlight(ids: List<String>) = error("unused")
    override suspend fun markCompleted(ids: List<String>) = error("unused")
    override suspend fun markFailed(id: String, error: String) = error("unused")
    override val pendingCount: Flow<Int> = rows.map { it.size }
    override fun observeOutbox(limit: Int): Flow<List<PendingMutation>> = rows
}

private class FakeSyncMetaRepository : SyncMetaRepository {
    val rows = MutableStateFlow<List<SyncMeta>>(emptyList())
    val observedScopes = mutableListOf<String>()
    val clearedScopes = mutableListOf<String>()

    override suspend fun cursor(scopeKey: String, collection: String): Instant? = error("unused")
    override suspend fun setCursor(scopeKey: String, collection: String, cursor: Instant) = error("unused")
    override suspend fun markAttempt(scopeKey: String, collection: String, at: Instant) = error("unused")
    override suspend fun markSuccess(scopeKey: String, collection: String, at: Instant) = error("unused")

    override suspend fun clearScope(scopeKey: String) {
        clearedScopes += scopeKey
        rows.value = emptyList()
    }

    override fun observe(scopeKey: String): Flow<List<SyncMeta>> {
        observedScopes += scopeKey
        return rows
    }
}

private fun mutation(id: String) = PendingMutation(
    id = id,
    entityType = "TRANSACTION",
    entityId = "tx-1",
    operation = MutationOperation.INSERT,
    scopeKey = WORKSPACE.value,
    createdAt = AT,
    attempts = 0,
    lastError = null,
)

private fun meta(collection: String) = SyncMeta(
    scopeKey = WORKSPACE.value,
    collection = collection,
    lastPulledAt = AT,
    lastSyncSuccessAt = AT,
    lastSyncAttemptAt = AT,
)
