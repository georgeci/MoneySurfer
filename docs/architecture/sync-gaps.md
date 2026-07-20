# Sync v2 — Known Gaps and Divergences

<!-- DOCS:TOC -->
## Contents
- [Sync v2 — Known Gaps and Divergences](#sync-v2--known-gaps-and-divergences)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
- [Outbox](#outbox)
- [Pull](#pull)
- [Conflict resolver](#conflict-resolver)
- [Network](#network)
- [Background scheduler](#background-scheduler)
- [Recalc / projections](#recalc--projections)
- [Demo migration](#demo-migration)
- [Telemetry](#telemetry)
- [Persistence of lastOutcome](#persistence-of-lastoutcome)
- [SDK exception classification](#sdk-exception-classification)
- [Tests](#tests)
- [Things explicitly not in scope](#things-explicitly-not-in-scope)
<!-- DOCS:END -->

## TL;DR for agents

- This is the running list of where the shipped implementation deviates from the original sync plan.
- Use it as a checklist before promising "fully implemented" to a reviewer.
- Items here are intentional Phase-1 trade-offs, not bugs to fix opportunistically.
- Adding to this list is preferable to silently shipping an undocumented divergence.

READ WHEN:
- reviewing a sync change for completeness
- estimating Phase-2/3/4/6 work
- writing release notes that touch sync behaviour
- onboarding to the sync subsystem

<!-- AI:SECTION id=sync-gaps-rules task=sync,gaps,roadmap,known-issues -->
## Rules

- Any new sync-subsystem PR that introduces a divergence from the original
  sync plan must add an entry here in the same change.
- Items below are explicit Phase-1 trade-offs — do not "fix" them in a
  drive-by; they need their own scoped change with tests.
- "Things explicitly not in scope" are product decisions; bringing them back
  needs an ADR update, not a code change.
<!-- AI:END -->

This is the running list of places where the shipped implementation
deviates from the original sync plan or where Phase-6 polish items still
need work. Use it as a checklist before promising "fully implemented" to
a reviewer.

## Outbox

- **Dual-write atomicity not enforced.**
  The sync plan (§4.3, Room schema changes) prescribes a single
  `db.withTransaction { dao.insert(...); outbox.enqueue(...) }`. Today
  the enqueue happens **after** the Room write. Source-level note in
  [OutboxEnqueuer.kt](../../sync/api/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/repository/OutboxEnqueuer.kt).
  Risk: process kill between the two writes leaves a Room row with no
  scheduled push.
- **Per-row backoff missing.** `markFailed` resets `status = PENDING`
  immediately. A retryable error that re-fails on the next cycle will
  busy-spin until the worker runs out of patience. No
  `lastAttemptAt` column, no `2^attempts`-second filter on `pending(...)`.
- **Outbox compaction missing.** `INSERT t1 / UPDATE t1 / DELETE t1`
  is three writes on push, not one.
- **Per-workspace scope filtering disabled.** `pending(scope, limit)`
  ignores `scope` and returns the global FIFO queue
  ([PendingMutationQueueImpl.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/internal/repository/PendingMutationQueueImpl.kt)).
  Will be wired when the coordinator carries `currentWorkspaceId`.
- **No `IN_FLIGHT` reaper.** A row left as `IN_FLIGHT` by a worker that
  died hard between `markInFlight` and the recovery `catch` is invisible
  to `pendingCount` and to the next drain.

## Pull

- **Cursor advance is not transactional with applied rows.**
  Per-doc DAO writes commit immediately; cursor advance happens at the
  end of the loop in a separate DAO call. At-least-once apply works only
  because LWW tie-breaking is `TakeLocal`. If a future resolver introduces
  side effects, this contract has to tighten — see
  [sync-pull-lww.md](sync-pull-lww.md#cursor--apply-atomicity).
- **Tombstone retention / GC not implemented.**
  Push-side soft delete shipped: `MutationOperation.DELETE` writes a
  `TombstonePatch` (`deletedAt` + `updatedAt` + `clientVersionCode`)
  via `update`, never `firestore.delete()` — see
  [sync-pull-lww.md](sync-pull-lww.md#tombstones-soft-delete). But
  tombstoned docs stay in Firestore forever: no retention window, no
  garbage collection. Fine at current volumes; a scheduled cleanup
  (Cloud Function or owner-client sweep) is future work, and any
  retention window must stay longer than the longest plausible
  offline-device gap or trimmed tombstones resurrect stale rows.
- **Tombstone `updatedAt` uses the client clock.** The tombstone stamps
  the mutation's enqueue time. A peer whose cursor already advanced past
  that value (possible only with clock skew between devices) misses the
  tombstone — same root cause as the server-timestamp gap below.
- **No server timestamps for `updatedAt`.** Push uses the client clock
  via `Clock.System.now()` set by the repository before enqueue.
  `FieldValue.serverTimestamp()` + read-back is in
  the sync plan (§4.4) but unimplemented.
  Practical effect: a device with a skewed clock can win conflicts it
  shouldn't.
- **Workspace-level docs still on v1.**
  [SyncCoordinatorWorkspaceSyncer.kt](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/SyncCoordinatorWorkspaceSyncer.kt)
  still owns push/pull of `workspaces/{wid}` itself — only its
  subcollections went through the cursor pipeline.
- **No multi-workspace fan-out.** `SyncScope.AllUserData` collapses to
  the same workspace list as `ActiveWorkspace` — only the active
  workspace is pulled.

## Conflict resolver

- **`LwwConflictResolver` is the only resolver.** `Skip` /
  `ConflictInbox` exists in the sealed interface but is not produced.
  Manual resolution UI is not on the roadmap until a real signal demands
  it.
- **No field-level merges.** `Merged` is a real branch in
  `applyResolution` but no resolver currently emits it.

## Network

- **`NoOpNetworkMonitor` only.** Always reports online. Real
  `ConnectivityManager` / `NWPathMonitor` adapters are not wired.
  Bounded `awaitNetwork` (5 min for `BACKGROUND`, 30 s for `APP_START`)
  has no observable effect today.

## Background scheduler

- **iOS scheduler is a logging stub.** `BGTaskScheduler` is not wired
  through cinterop. Sync on iOS runs only while the app is in foreground
  and the user / app code triggers it (`APP_START`, `MANUAL`,
  `LOCAL_CHANGE`). No true background.
- **Android scheduler is functional but not invoked.** Nothing in
  `:shared` calls `BackgroundSyncScheduler.schedulePeriodic` today —
  the in-process 1-minute loop in
  [AppLaunchViewModel.kt](../../navigation/src/commonMain/kotlin/com/georgeci/moneysurfer/navigation/AppLaunchViewModel.kt)
  carries periodic sync while the app is open. Out-of-process Android
  background sync requires explicit wiring and a Koin
  `WorkerFactory`.
- **No retry backoff via the scheduler.** A
  `SyncError.NetworkUnavailable` failure in the coordinator does not
  feed back into `BackgroundSyncScheduler.scheduleOneShot(retryDelay)` —
  only the Android `SyncWorker.Result.retry()` branch leverages
  WorkManager's own backoff. The mapping `SyncError → retry policy`
  would need a small adapter.

## Recalc / projections

- **`NoOpRecalculateLocalProjectionsUseCase` is what's bound.** Real
  account-balance / projection recalc is its own subsystem, not yet
  built. The coordinator already gates the call on
  `downloadedCount > 0`, so swapping the binding for a real
  implementation is a one-line DI change once it lands.

## Demo migration

- **`WipeDemoDataUseCase` shipped.** Per
  the sync plan (§2.11), demo data is
  wiped on transition to a real account; it is never pushed to
  Firestore. Search for `WipeDemoDataUseCase` to find the call site
  inside auth flows.

## Telemetry

- **Logging only.** `KermitSyncTelemetry` writes to Kermit. No
  Crashlytics, no OpenTelemetry. The interface is small and one-line to
  swap in `:sync-surfer`.

## Persistence of `lastOutcome`

- **`lastOutcome` does not survive a process restart.** It is a
  `MutableStateFlow<LastSyncOutcome>` initialised to `None` on every
  coordinator construction. The plan in
  the sync plan (§2.12, sync metadata in DataStore) is to persist
  it via DataStore — not done yet.

## SDK exception classification

- **String-matching for Firestore exceptions.**
  [SyncErrorClassifier.kt](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/SyncErrorClassifier.kt)
  inspects `error.message?.lowercase()` because `gitlive` does not
  surface a typed `Code` enum uniformly across platforms. A native
  per-platform mapper would be more reliable — Phase-6 polish.

## Tests

The test inventory under
[`:sync/src/commonTest`](../../sync/default/src/commonTest/) and
[`:sync-surfer/src/commonTest`](../../sync-surfer/src/commonTest/) covers the FAQ
checklist on the coordinator and the outbox dual-write happy path.
What's *not* covered:

- Pull cursor + applier integration (FakeFirestore would unblock this).
- Recovery branches in `UploadPendingChangesUseCaseImpl` —
  `markFailed` after a partial batch, `IN_FLIGHT` resurrection.
- WorkManager / `SyncWorker` end-to-end (treated as platform-trusted).

## Things explicitly **not** in scope

- **`ConflictInbox`-style manual resolution UI.** Not planned — LWW is
  the product decision.
- **Live Firestore listeners** (`addSnapshotListener` / `.snapshots()`).
  Sync remains pull-driven; FCM-triggered immediate pulls are a future
  addition (see sync plan §2.6, live Firestore listeners).
- **Budgets and recurring rules in sync.** `BudgetEntity` and
  `RecurringRuleEntity` are local-only. `SyncEntityType` deliberately
  omits them; adding them is a self-contained future change.
