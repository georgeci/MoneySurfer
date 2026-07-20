# Sync v2 — Coordinator

<!-- DOCS:TOC -->
## Contents
- [Sync v2 — Coordinator](#sync-v2--coordinator)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
- [Public surface](#public-surface)
- [Single-actor model](#single-actor-model)
- [Request merging](#request-merging)
- [Lifecycle of a single sync run](#lifecycle-of-a-single-sync-run)
- [Cancel tokens](#cancel-tokens)
- [State emission](#state-emission)
- [Lifetime — ApplicationScope](#lifetime--applicationscope)
- [Scope mapping](#scope-mapping)
- [Telemetry hooks](#telemetry-hooks)
<!-- DOCS:END -->

## TL;DR for agents

- `SyncCoordinator` is the single entry point — UI/feature code never touches Firestore directly.
- All mutable coordinator state lives inside one actor loop fed by a `Channel<SyncCommand>`.
- `state` reflects live activity; `lastOutcome` is the terminal-result side-channel.
- The coordinator runs on `ApplicationScope`, not on `viewModelScope`.

READ WHEN:
- changing coordinator commands, request merging, or cancel semantics
- adding a new pipeline step in `runSyncRequest`
- touching `SyncHandle` / `SyncStep` / `SyncState` shape
- modifying telemetry hooks

<!-- AI:SECTION id=sync-coordinator-rules task=sync,coordinator,actor,cancel,telemetry -->
## Rules

- All mutable state of the coordinator (`pending`, `currentRequest`, `currentJob`)
  is owned by `actorLoop()` and only mutated through `SyncCommand` messages.
- External callers never read or write that state directly.
- Cancel tokens are atomic and must be flipped synchronously **before** the
  matching `SyncCommand` is sent, to eliminate races on the worker job.
- There is at most **one** pending merged request and at most **one** running
  merged request at any time.
- `state` is for live activity (`Idle` / `Queued(n)` / `Running(...)`);
  terminal results live in `lastOutcome`.
- Cleanup in terminal branches must run under `withContext(NonCancellable)` so
  that emitting steps after cancellation does not re-throw.
- The actor MUST always emit `WorkerFinished` in `finally` so the loop
  unblocks and `pending` can be promoted.
- The coordinator runs on `ApplicationScope`; killing a `ViewModel` must not
  cancel an in-flight sync.
<!-- AI:END -->

The coordinator is the single entry point to sync. UI and feature code never
touch Firestore directly. Source:
[SyncCoordinatorImpl.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/internal/coordinator/SyncCoordinatorImpl.kt).

## Public surface

```kotlin
interface SyncCoordinator {
    val state: StateFlow<SyncState>          // Idle / Queued(n) / Running(...)
    val lastOutcome: StateFlow<LastSyncOutcome>  // None / Success / Failed / Cancelled

    fun requestSync(reason: SyncReason, mode: SyncMode = Enqueue): SyncHandle

    fun cancelCurrent()        // running run only — leaves queue intact
    fun cancelAllQueued()      // queued only — leaves running run intact
    fun cancelAll()            // both, atomically (single command)
}
```

`state` reflects only **live** activity. Terminal results live in
`lastOutcome`. UI badges that say "synced N min ago" should subscribe to
`lastOutcome`; spinners and status text should subscribe to `state`. See
coordinator FAQ §10 in the original design notes.

`SyncHandle` is what `requestSync` returns to the caller:

```kotlin
interface SyncHandle {
    val id: SyncRequestId
    val status: StateFlow<SyncHandleStatus>           // Queued / Running / Completed / Failed / Cancelled
    val steps: SharedFlow<SyncStep>                   // replay = 1, DROP_OLDEST, buffer 64
    val result: Deferred<SyncResult<SyncSummary>>     // SyncResult<T> = Either<SyncError, T>
    fun cancel()
}
```

`steps` uses `BufferOverflow.DROP_OLDEST` so a slow subscriber cannot stall
the sync (coordinator FAQ §20). `replay = 1` means a late
subscriber sees at least the most recent step, which is good enough for
status text.

## Single-actor model

All mutable state of the coordinator — `pending: MergedSyncRequest?`,
`currentRequest`, `currentJob` — is owned by `actorLoop()` and never read
or written from outside. External callers (`requestSync`, `cancelXxx`,
`handle.cancel()`) interact via a `Channel<SyncCommand>` configured with
`UNLIMITED` capacity.

`SyncCommand` is a sealed interface:

```kotlin
internal sealed interface SyncCommand {
    data class Enqueue(val request: SyncRequest, val mode: SyncMode) : SyncCommand
    data class CancelHandle(val requestId: SyncRequestId) : SyncCommand
    data object CancelCurrent : SyncCommand
    data object CancelQueued : SyncCommand
    data object CancelAll : SyncCommand
    data object WorkerFinished : SyncCommand   // sent by the worker job upon completion
}
```

Cancel tokens are atomic so callers may flip them synchronously **before**
sending the command (see `SyncCoordinatorImpl.requestSync`):

```kotlin
cancelAction = {
    request.cancelToken.cancel()
    commands.trySend(SyncCommand.CancelHandle(request.id))
}
```

The actor then performs the visible state transitions (status, steps,
`lastOutcome`). This eliminates the race on `currentJob` /
`currentRequest` (coordinator FAQ §1).

## Request merging

`SyncRequest` is one call to `requestSync`. Each request has its own
cancel token, status `StateFlow`, steps `SharedFlow`, and result
`CompletableDeferred`.

`MergedSyncRequest` is one or more `SyncRequest` collapsed into a single
physical run:

- `reasons` is the union (`Set<SyncReason>`).
- `scope` is widened by `mergeScope(...)` —
  `AllUserData > ActiveWorkspace > ChangedSinceLastSync > UploadOnly`.
  Source: [SyncScope.kt](../../sync/api/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/api/SyncScope.kt).
- `childRequests` keeps the individual `SyncRequest` objects so each
  caller's `SyncHandle` reflects only its own lifecycle.

`SyncMode.Enqueue` merges a new request into the existing pending
`MergedSyncRequest` if one exists. `SyncMode.ReplaceQueued` cancels the
current pending merge and replaces it with the new request.
`ForceAfterCurrent` was deliberately **not** implemented — see
coordinator FAQ §8.

There is at most **one** pending merged request and at most **one**
running merged request at a time. The actor never has more than two
in-flight, total.

## Lifecycle of a single sync run

`startWorkerIfNeeded()` promotes `pending` to `currentRequest` and
launches `runSyncRequest` on `appScope`. The pipeline is:

1. `SyncStep.Started` — emitted to children + telemetry.
2. `appVersionGate.refresh()` — refreshes the cached version status from
   Firestore. If the build is `Unsupported`, raise
   `SyncError.UnsupportedAppVersion`. **The gate refreshes every cycle**
   so that toggling `forceUpdate` on the server takes effect within the
   next sync, not just at app start. See [app-version-gate.md](app-version-gate.md).
3. `awaitNetwork` — only emits `SyncStep.WaitingForNetwork` if currently
   offline. Behavior depends on `Set<SyncReason>.toNetworkWaitMode()`:
   - `BACKGROUND` → bounded 5 min,
   - `APP_START` → bounded 30 s,
   - everything else → indefinite (user-cancellable via `handle.cancel()`).
   Bounded timeout returning null raises `SyncError.NetworkUnavailable`.
4. `SyncStep.UploadingPendingChanges` → `uploadPendingChangesUseCase(scope, …)`.
5. If `scope != UploadOnly`, `SyncStep.PullingRemoteChanges` →
   `pullRemoteChangesUseCase(scope, …)`.
6. If `downloadedCount > 0`, `SyncStep.RecalculatingProjections` →
   `recalculateLocalProjectionsUseCase(...)`. Skipped on idle pulls and on
   `UploadOnly` (coordinator FAQ §7).
7. `SyncSummary` returned via `Either.Right`.

Every stage observes `cancelToken.throwIfCancelled()` between steps. Long
loops inside use case impls call it themselves between batches.

The worker job has three terminal branches in
[`SyncCoordinatorImpl.startWorkerIfNeeded`](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/internal/coordinator/SyncCoordinatorImpl.kt):

| Outcome of `runSyncRequest` | Action |
|------------------------------|--------|
| `Right(summary)`             | `completeSuccess`: each non-completed child gets `Completed` step, `Completed(summary)` status, `result` is filled with `summary.right()`. `lastOutcome = Success(summary, now)`. |
| `Left(SyncError.Cancelled)`  | `completeCancelled`: each child finalised via `finalizeCancelled`, `lastOutcome = Cancelled(now)`. |
| `Left(other error)`          | `completeFailure`: each child gets `Failed(error)` step + status, `result` is filled with `error.left()`. `lastOutcome = Failed(error, now)`. |
| `SyncCancelledException` thrown | Identical to `Left(Cancelled)` but cleanup runs in `withContext(NonCancellable)` so emitting steps does not re-throw. |
| Any other `Throwable`        | Catch-all — wrapped as `SyncError.Unknown(t)` and treated as a failure, again under `NonCancellable`. The actor MUST always emit `WorkerFinished`. |

`finally { commands.trySend(SyncCommand.WorkerFinished) }` is what
unblocks the actor and triggers `startWorkerIfNeeded` for any new
`pending`.

## Cancel tokens

[SyncCancelToken.kt](../../sync/api/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/api/SyncCancelToken.kt):

- `SimpleCancelToken` — atomic boolean, used per-`SyncRequest`.
- `CompositeCancelToken` — wraps multiple children with **all** semantics:
  cancelled only when **every** child is cancelled. This is what the
  coordinator uses for a `MergedSyncRequest` so that one caller cancelling
  does not abort the merged physical run for the others
  (coordinator FAQ §2, §4).
- `SyncCancelledException : CancellationException` — thrown by
  `throwIfCancelled()`.

`finalizeCancelled(child)` is the one place that emits `SyncStep.Cancelled`,
flips `status` to `Cancelled`, fills `result` with `SyncError.Cancelled.left()`,
and is guarded by `if (child.result.isCompleted) return` so re-cancel is
idempotent (coordinator FAQ §3).

## State emission

`emitStep(request, step)` does three things in order:

1. For each non-completed child request: `child.steps.emit(step)` and
   `child.status.value = SyncHandleStatus.Running(step)`.
2. `_state.value = SyncState.Running(...)`.
3. `telemetry.onStepEntered(...)`.

When a child has already completed (e.g. cancelled) it is filtered out so
its terminal status sticks.

`publishQueueState()` is what brings `_state` back to `Idle` /
`Queued(n)` once a worker job finishes — it is invoked by
`handleWorkerFinished()` and during `Enqueue` / cancel handling.

## Lifetime — `ApplicationScope`

The coordinator launches its actor loop on
[`ApplicationScope`](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/runtime/ApplicationScope.kt),
**not** on `viewModelScope`. The scope is process-bound,
`SupervisorJob() + Dispatchers.Default`. UI navigating away or
ViewModels dying does not cancel the running sync — only `handle.cancel()`
or one of the coordinator-level `cancelXxx` does. Tests substitute the
delegate with a `TestScope`.

This is the contract that makes "Scenario 1 — offline write → reconnect →
push" of the sync plan (§4.7, scenarios) actually work: the user can
close the screen during `WaitingForNetwork`, and the coordinator keeps
suspending on `networkMonitor.online.first { it }` until the network
returns or the user logs out (`SessionShutdownGate.shutdown()`).

## Scope mapping

```kotlin
fun SyncReason.toScope() = when (this) {
    APP_START, FOREGROUND, SWIPE_REFRESH -> ActiveWorkspace
    MANUAL                               -> AllUserData
    LOCAL_CHANGE                         -> UploadOnly
    BACKGROUND                           -> ChangedSinceLastSync
}
```

Outbox draining ignores scope today — see
[sync-outbox.md](sync-outbox.md). Pull honours scope: `UploadOnly`
returns immediately, every other scope hits the same collection list
(members → invites → accounts → categories → transactions, in that order).
The `ChangedSinceLastSync` differentiation is realised through cursors:
the SQL/Firestore filter `where("updatedAt", ">", cursor)` already does
that for any scope other than `UploadOnly`.

## Telemetry hooks

Source:
[SyncTelemetry.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/telemetry/SyncTelemetry.kt),
implementation:
[KermitSyncTelemetry.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/internal/telemetry/KermitSyncTelemetry.kt).

Calls (from `SyncCoordinatorImpl`):

- `onSyncStarted(requestId, reasons, scope)` — when a worker is promoted
  from pending to running.
- `onStepEntered(requestId, step)` — every `emitStep`.
- `onSyncCompleted(requestId, summary, duration)` — `completeSuccess`.
- `onSyncFailed(requestId, error, duration)` — `completeFailure`.
- `onSyncCancelled(requestId, duration)` — `completeCancelled`.

Durations are wall-clock (`Clock.System.now()` at start vs at terminal),
tracked in `startedAt: MutableMap<SyncRequestId, Instant>`. The map is
cleared on the terminal call (`durationSinceStart` does `remove(...)`),
so it can never grow unbounded.

`KermitSyncTelemetry` logs at `info` for lifecycle and `debug` for steps,
prefixed `[req=…]` so a single sync run can be greppped across noisy logs.
