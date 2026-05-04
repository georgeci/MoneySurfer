package com.georgeci.moneysurfer.sync.internal.coordinator

import arrow.core.left
import arrow.core.raise.either
import arrow.core.right
import com.georgeci.moneysurfer.sync.api.CompositeCancelToken
import com.georgeci.moneysurfer.sync.api.LastSyncOutcome
import com.georgeci.moneysurfer.sync.api.SyncCancelToken
import com.georgeci.moneysurfer.sync.api.SyncCancelledException
import com.georgeci.moneysurfer.sync.api.SyncError
import com.georgeci.moneysurfer.sync.api.SyncHandleStatus
import com.georgeci.moneysurfer.sync.api.SyncMode
import com.georgeci.moneysurfer.sync.api.SyncReason
import com.georgeci.moneysurfer.sync.api.SyncRequestId
import com.georgeci.moneysurfer.sync.api.SyncResult
import com.georgeci.moneysurfer.sync.api.SyncScope
import com.georgeci.moneysurfer.sync.api.SyncState
import com.georgeci.moneysurfer.sync.api.SyncStep
import com.georgeci.moneysurfer.sync.api.SyncSummary
import com.georgeci.moneysurfer.sync.coordinator.SyncCoordinator
import com.georgeci.moneysurfer.sync.coordinator.SyncHandle
import com.georgeci.moneysurfer.sync.internal.SyncSummaryBuilder
import com.georgeci.moneysurfer.sync.internal.network.NetworkWaitMode
import com.georgeci.moneysurfer.sync.internal.network.toNetworkWaitMode
import com.georgeci.moneysurfer.sync.network.NetworkMonitor
import com.georgeci.moneysurfer.domain.sync.PullProgress
import com.georgeci.moneysurfer.domain.sync.PullRemoteChangesUseCase
import com.georgeci.moneysurfer.domain.sync.ProjectionScope
import com.georgeci.moneysurfer.domain.sync.RecalculateLocalProjectionsUseCase
import com.georgeci.moneysurfer.domain.sync.UploadPendingChangesUseCase
import com.georgeci.moneysurfer.domain.sync.UploadProgress
import com.georgeci.moneysurfer.sync.runtime.ApplicationScope
import com.georgeci.moneysurfer.sync.telemetry.SyncTelemetry
import com.georgeci.moneysurfer.sync.version.SyncVersionGate
import com.georgeci.moneysurfer.sync.version.SyncVersionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import kotlin.time.Clock

/**
 * Single-actor implementation of [SyncCoordinator].
 *
 * All mutations of `pending` / `currentRequest` / `currentJob` happen inside
 * [actorLoop] — the only place that owns those fields. External callers
 * (`requestSync`, `cancelXxx`, `handle.cancel()`) interact with the actor only
 * via [commands].
 *
 * Cancel tokens are thread-safe ([SyncCancelToken] uses atomic state), so
 * callers may flip them synchronously from any thread before sending the
 * command. The actor then performs the visible state changes.
 *
 * See SyncCoordinatorFAQ.md (especially №1, №3, №4, №10, №19) for rationale.
 */
@Single(binds = [SyncCoordinator::class])
@Suppress("TooManyFunctions", "LongParameterList")
class SyncCoordinatorImpl(
    private val appScope: ApplicationScope,
    private val networkMonitor: NetworkMonitor,
    private val versionGate: SyncVersionGate,
    private val uploadPendingChangesUseCase: UploadPendingChangesUseCase,
    private val pullRemoteChangesUseCase: PullRemoteChangesUseCase,
    private val recalculateLocalProjectionsUseCase: RecalculateLocalProjectionsUseCase,
    private val telemetry: SyncTelemetry = SyncTelemetry.NoOp,
    private val newRequestId: () -> SyncRequestId = { SyncRequestId.uuid() },
) : SyncCoordinator {

    /** Wall-clock start times keyed by request id — used for [SyncTelemetry] durations. */
    private val startedAt = mutableMapOf<SyncRequestId, kotlin.time.Instant>()

    private val commands = Channel<SyncCommand>(capacity = Channel.UNLIMITED)

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    override val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _lastOutcome = MutableStateFlow<LastSyncOutcome>(LastSyncOutcome.None)
    override val lastOutcome: StateFlow<LastSyncOutcome> = _lastOutcome.asStateFlow()

    // Owned exclusively by actorLoop. Never read or mutated from outside the loop.
    private var pending: MergedSyncRequest? = null
    private var currentRequest: MergedSyncRequest? = null
    private var currentJob: Job? = null

    init {
        appScope.launch { actorLoop() }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    override fun requestSync(reason: SyncReason, mode: SyncMode): SyncHandle {
        val request = SyncRequest(id = newRequestId(), reason = reason)

        commands.trySend(SyncCommand.Enqueue(request, mode))

        return SyncHandleImpl(
            id = request.id,
            status = request.status.asStateFlow(),
            steps = request.steps,
            result = request.result,
            cancelAction = {
                request.cancelToken.cancel()
                commands.trySend(SyncCommand.CancelHandle(request.id))
            },
        )
    }

    override fun cancelCurrent() {
        commands.trySend(SyncCommand.CancelCurrent)
    }

    override fun cancelAllQueued() {
        commands.trySend(SyncCommand.CancelQueued)
    }

    override fun cancelAll() {
        commands.trySend(SyncCommand.CancelAll)
    }

    // -------------------------------------------------------------------------
    // Actor loop
    // -------------------------------------------------------------------------

    private suspend fun actorLoop() {
        for (command in commands) {
            when (command) {
                is SyncCommand.Enqueue -> handleEnqueue(command.request, command.mode)
                is SyncCommand.CancelHandle -> handleCancelHandle(command.requestId)
                SyncCommand.CancelCurrent -> handleCancelCurrent()
                SyncCommand.CancelQueued -> handleCancelQueued()
                SyncCommand.CancelAll -> handleCancelAll()
                SyncCommand.WorkerFinished -> handleWorkerFinished()
            }
        }
    }

    private suspend fun handleEnqueue(request: SyncRequest, mode: SyncMode) {
        when (mode) {
            SyncMode.Enqueue -> {
                pending = pending?.merge(request) ?: MergedSyncRequest.from(request)
            }
            SyncMode.ReplaceQueued -> {
                cancelPendingInternal()
                pending = MergedSyncRequest.from(request)
            }
        }
        publishQueueState()
        startWorkerIfNeeded()
    }

    private suspend fun handleCancelHandle(targetId: SyncRequestId) {
        // First check the currently running request.
        val current = currentRequest
        if (current != null) {
            val child = current.childRequests.firstOrNull { it.id == targetId }
            if (child != null) {
                finalizeCancelled(child)
                if (current.childRequests.all { it.cancelToken.isCancelled() }) {
                    currentJob?.cancel()
                }
                return
            }
        }

        // Otherwise look in the pending merged request.
        val queued = pending
        if (queued != null) {
            val child = queued.childRequests.firstOrNull { it.id == targetId }
            if (child != null) {
                finalizeCancelled(child)
                if (queued.childRequests.all { it.cancelToken.isCancelled() }) {
                    pending = null
                }
                publishQueueState()
            }
        }
    }

    private suspend fun handleCancelCurrent() {
        val current = currentRequest ?: return
        current.childRequests.forEach { finalizeCancelled(it) }
        currentJob?.cancel()
    }

    private suspend fun handleCancelQueued() {
        cancelPendingInternal()
        publishQueueState()
    }

    private suspend fun handleCancelAll() {
        cancelPendingInternal()
        val current = currentRequest
        if (current != null) {
            current.childRequests.forEach { finalizeCancelled(it) }
            currentJob?.cancel()
        }
        publishQueueState()
    }

    private fun handleWorkerFinished() {
        currentRequest = null
        currentJob = null
        startWorkerIfNeeded()
        publishQueueState()
    }

    // -------------------------------------------------------------------------
    // Worker
    // -------------------------------------------------------------------------

    private fun startWorkerIfNeeded() {
        if (currentJob?.isActive == true) return
        val next = pending ?: return

        // Skip already fully-cancelled merged requests.
        if (next.childRequests.all { it.cancelToken.isCancelled() }) {
            pending = null
            publishQueueState()
            return
        }

        pending = null
        currentRequest = next
        startedAt[next.id] = Clock.System.now()
        telemetry.onSyncStarted(next.id, next.reasons, next.scope)

        currentJob = appScope.launch {
            try {
                val outcome = runSyncRequest(next)
                outcome.fold(
                    ifLeft = { error ->
                        if (error is SyncError.Cancelled) {
                            completeCancelled(next)
                        } else {
                            completeFailure(next, error)
                        }
                    },
                    ifRight = { summary -> completeSuccess(next, summary) },
                )
            } catch (_: SyncCancelledException) {
                // suspend cleanup must run in NonCancellable so child.steps.emit(...)
                // does not re-throw on the already-cancelled coroutine.
                withContext(NonCancellable) { completeCancelled(next) }
            } catch (_: CancellationException) {
                withContext(NonCancellable) { completeCancelled(next) }
            } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
                // Catch-all for unexpected errors — coordinator must always finish
                // gracefully so WorkerFinished is emitted and the actor unblocks.
                withContext(NonCancellable) { completeFailure(next, SyncError.Unknown(t)) }
            } finally {
                commands.trySend(SyncCommand.WorkerFinished)
            }
        }
    }

    private suspend fun runSyncRequest(request: MergedSyncRequest): SyncResult<SyncSummary> {
        val cancelToken = mergedCancelToken(request)
        val summary = SyncSummaryBuilder()

        return either {
            emitStep(request, SyncStep.Started)
            cancelToken.throwIfCancelled()

            ensureAppVersionAllowed().bind()
            cancelToken.throwIfCancelled()

            awaitNetwork(request, cancelToken).bind()
            cancelToken.throwIfCancelled()

            emitStep(request, SyncStep.UploadingPendingChanges)
            val uploadSummary = uploadPendingChangesUseCase(
                scope = request.scope,
                onProgress = { progress -> emitUploadProgress(request, progress) },
                cancelToken = cancelToken,
            ).bind()
            summary.addUpload(uploadSummary.uploadedCount)
            cancelToken.throwIfCancelled()

            var downloadedCount = 0
            if (request.scope != SyncScope.UploadOnly) {
                emitStep(request, SyncStep.PullingRemoteChanges)
                val pullSummary = pullRemoteChangesUseCase(
                    scope = request.scope,
                    onProgress = { progress -> emitPullProgress(request, progress) },
                    cancelToken = cancelToken,
                ).bind()
                downloadedCount = pullSummary.downloadedCount
                summary.addDownload(downloadedCount)
                summary.addConflicts(pullSummary.conflictCount)
            }
            cancelToken.throwIfCancelled()

            if (downloadedCount > 0) {
                emitStep(request, SyncStep.RecalculatingProjections)
                val projectionSummary = recalculateLocalProjectionsUseCase(
                    scope = projectionScopeFor(request.scope),
                    cancelToken = cancelToken,
                ).bind()
                summary.addRecalculated(projectionSummary.recalculatedCount)
            }

            summary.build()
        }
    }

    private suspend fun awaitNetwork(
        request: MergedSyncRequest,
        cancelToken: SyncCancelToken,
    ): SyncResult<Unit> = either {
        if (networkMonitor.online.value) return@either
        emitStep(request, SyncStep.WaitingForNetwork)
        when (val mode = request.reasons.toNetworkWaitMode()) {
            NetworkWaitMode.Indefinite -> {
                networkMonitor.online.first { it }
            }
            is NetworkWaitMode.Bounded -> {
                val onlined = withTimeoutOrNull(mode.timeout) {
                    networkMonitor.online.first { it }
                }
                if (onlined == null) raise(SyncError.NetworkUnavailable)
            }
        }
        cancelToken.throwIfCancelled()
    }

    /**
     * Refreshes the version gate (so a force-update flipped on the server
     * blocks within one sync cycle, not just at app start) and short-circuits
     * the pipeline if the app build is unsupported.
     *
     * See block.md.
     */
    private suspend fun ensureAppVersionAllowed(): SyncResult<Unit> = either {
        val status = versionGate.refresh()
        if (status is SyncVersionStatus.Blocked) {
            raise(SyncError.UnsupportedAppVersion(status.message))
        }
    }

    // -------------------------------------------------------------------------
    // State emission
    // -------------------------------------------------------------------------

    private suspend fun emitStep(request: MergedSyncRequest, step: SyncStep) {
        request.childRequests
            .filter { !it.result.isCompleted }
            .forEach { child ->
                child.steps.emit(step)
                child.status.value = SyncHandleStatus.Running(step)
            }
        _state.value = SyncState.Running(
            requestId = request.id,
            reasons = request.reasons,
            scope = request.scope,
            currentStep = step,
        )
        telemetry.onStepEntered(request.id, step)
    }

    private suspend fun emitUploadProgress(request: MergedSyncRequest, progress: UploadProgress) {
        emitStep(
            request,
            SyncStep.UploadingEntity(
                entityType = progress.entityType,
                current = progress.current,
                total = progress.total,
            ),
        )
    }

    private suspend fun emitPullProgress(request: MergedSyncRequest, progress: PullProgress) {
        emitStep(
            request,
            SyncStep.PullingCollection(
                collection = progress.collection,
                current = progress.current,
                total = progress.total,
            ),
        )
    }

    // -------------------------------------------------------------------------
    // Terminal transitions
    // -------------------------------------------------------------------------

    private suspend fun completeSuccess(request: MergedSyncRequest, summary: SyncSummary) {
        request.childRequests
            .filter { !it.result.isCompleted }
            .forEach { child ->
                child.steps.emit(SyncStep.Completed(summary))
                child.status.value = SyncHandleStatus.Completed(summary)
                child.result.complete(summary.right())
            }
        val now = Clock.System.now()
        _lastOutcome.value = LastSyncOutcome.Success(summary, now)
        telemetry.onSyncCompleted(request.id, summary, durationSinceStart(request.id, now))
    }

    private suspend fun completeFailure(request: MergedSyncRequest, error: SyncError) {
        request.childRequests
            .filter { !it.result.isCompleted }
            .forEach { child ->
                child.steps.emit(SyncStep.Failed(error))
                child.status.value = SyncHandleStatus.Failed(error)
                child.result.complete(error.left())
            }
        val now = Clock.System.now()
        _lastOutcome.value = LastSyncOutcome.Failed(error, now)
        telemetry.onSyncFailed(request.id, error, durationSinceStart(request.id, now))
    }

    private suspend fun completeCancelled(request: MergedSyncRequest) {
        request.childRequests.forEach { child -> finalizeCancelled(child) }
        val now = Clock.System.now()
        _lastOutcome.value = LastSyncOutcome.Cancelled(now)
        telemetry.onSyncCancelled(request.id, durationSinceStart(request.id, now))
    }

    private fun durationSinceStart(id: SyncRequestId, now: kotlin.time.Instant): kotlin.time.Duration {
        val started = startedAt.remove(id) ?: return kotlin.time.Duration.ZERO
        return now - started
    }

    private suspend fun finalizeCancelled(child: SyncRequest) {
        if (child.result.isCompleted) return
        child.cancelToken.cancel()
        child.steps.emit(SyncStep.Cancelled())
        child.status.value = SyncHandleStatus.Cancelled
        child.result.complete(SyncError.Cancelled.left())
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private suspend fun cancelPendingInternal() {
        val queued = pending ?: return
        queued.childRequests.forEach { finalizeCancelled(it) }
        pending = null
    }

    private fun publishQueueState() {
        val current = currentRequest
        if (current != null) return // running state is emitted via emitStep
        val queuedCount = pending?.childRequests
            ?.count { !it.cancelToken.isCancelled() }
            ?: 0
        _state.value = if (queuedCount > 0) SyncState.Queued(queuedCount) else SyncState.Idle
    }

    private fun mergedCancelToken(request: MergedSyncRequest): SyncCancelToken =
        CompositeCancelToken(request.childRequests.map { it.cancelToken })

    /**
     * Maps [SyncScope] onto [ProjectionScope]. Without a workspace pointer at
     * coordinator level, all scopes collapse to [ProjectionScope.All].
     * Implementations of [RecalculateLocalProjectionsUseCase] may interpret
     * this as "all workspaces of the current user" and filter further.
     */
    private fun projectionScopeFor(scope: SyncScope): ProjectionScope = when (scope) {
        SyncScope.UploadOnly,
        SyncScope.ActiveWorkspace,
        SyncScope.AllUserData,
        SyncScope.ChangedSinceLastSync,
        -> ProjectionScope.All
    }
}
