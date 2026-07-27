# Sync v2 — Outbox

<!-- DOCS:TOC -->
## Contents
- [Sync v2 — Outbox](#sync-v2--outbox)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
- [Why an outbox](#why-an-outbox)
- [Files](#files)
- [Schema](#schema)
- [PendingMutationQueue interface](#pendingmutationqueue-interface)
- [OutboxEnqueuer — the dual-write helper](#outboxenqueuer--the-dual-write-helper)
  - [Atomicity caveat](#atomicity-caveat)
- [Drain cycle — UploadPendingChangesUseCaseImpl](#drain-cycle--uploadpendingchangesusecaseimpl)
  - [pushOne — entity-type dispatch](#pushone--entity-type-dispatch)
- [Demo + version gate at the entry](#demo--version-gate-at-the-entry)
- [Lifecycle interactions](#lifecycle-interactions)
- [What "scope" means for the outbox today](#what-scope-means-for-the-outbox-today)
- [What's not (yet) in the outbox](#whats-not-yet-in-the-outbox)
- [Mental model — write path summary](#mental-model--write-path-summary)
<!-- DOCS:END -->

## TL;DR for agents

- The outbox is the durable queue of **local mutations** waiting for Firestore.
- Every UI-triggered write to a synchronisable entity goes to Room **and** to the outbox.
- Pull writes go through DAOs only — they **never** re-enter the outbox.
- Demo sessions and builds blocked by the app-version gate skip the outbox entirely.
- Dual-write is **not** transactional today (Phase-1 caveat — see below).

READ WHEN:
- touching the write path (any repository that owns a sync entity)
- changing `OutboxEnqueuer`, `PendingMutationQueue`, or `pending_mutations`
- adding a new `SyncEntityType` or a new Firestore push path
- modifying drain / batch / failure-handling behaviour

<!-- AI:SECTION id=sync-outbox-rules task=sync,outbox,write-path,dual-write -->
## Rules

- A user-write to a synchronisable entity must call **both** `dao.write(...)` and
  `outboxEnqueuer.enqueueUpsert/enqueueDelete(...)`.
- `OutboxEnqueuer.isEnabled()` short-circuits the enqueue **and the JSON
  encoding**: demo sessions (empty `currentFirebaseUid`) and builds with
  `AppVersionStatus.Unsupported` must not write outbox rows.
- DELETE enqueue must read the row before delete to capture `workspaceId` —
  the outbox needs it to target the right Firestore subcollection.
- `markCompleted` deletes the row. `markFailed` resets `status = PENDING`,
  bumps `attempts`, captures `lastError`.
- `pendingCount` excludes `IN_FLIGHT` (only `PENDING` + `FAILED` count for
  the UI badge).
- The pull stage **must not** go through `OutboxEnqueuer` — only via DAOs.
- Phase-1 caveat: dual-write is not yet inside `db.withTransaction { }`.
  Treat any new write-path code as moving toward that contract.
<!-- AI:END -->

The outbox is the durable queue of **local mutations** waiting to be pushed
to Firestore. Every UI-triggered write to a synchronisable entity goes
into Room and into the outbox; the next sync run drains it. This is the
mechanism that makes the app offline-first.

## Why an outbox

Without one, a Room write that fails to push leaves the app in a state
where local truth and remote truth disagree silently — the user has no
idea their change has not propagated. With an outbox:

- Local writes always succeed (Room first, outbox second).
- Push retries are bounded by `attempts` and survive process restart.
- "How many local changes are unsynchronised?" is just `pendingCount`.
- The pull stage applies remote rows directly to Room **without
  re-enqueueing them**, so remote changes don't ping-pong back through
  the outbox.

## Files

| File | What |
|------|------|
| [PendingMutationQueue.kt](../../sync/api/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/repository/PendingMutationQueue.kt) | Domain interface in `:sync`. |
| [PendingMutation.kt](../../sync/api/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/repository/PendingMutation.kt) | Domain row. |
| [MutationOperation.kt](../../sync/api/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/repository/MutationOperation.kt) | `INSERT` / `UPDATE` / `DELETE`. |
| [PendingMutationEntity.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/db/entity/PendingMutationEntity.kt) | Room entity, table `pending_mutations`. |
| [PendingMutationDao.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/db/dao/PendingMutationDao.kt) | DAO. |
| [PendingMutationQueueImpl.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/internal/repository/PendingMutationQueueImpl.kt) | DAO-backed implementation. |
| [OutboxEnqueuer.kt](../../sync/api/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/repository/OutboxEnqueuer.kt) | Helper used by every dual-writing repository. |
| [UploadPendingChangesUseCaseImpl.kt](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/UploadPendingChangesUseCaseImpl.kt) | Drains the queue. |

## Schema

`pending_mutations` (no FK constraints — it is an independent ledger):

| Column        | Type     | Notes |
|---------------|----------|-------|
| `id`          | TEXT PK  | UUID minted by `OutboxEnqueuer` (one per mutation, not per entity). |
| `entityType`  | TEXT     | `SyncEntityType.name`. |
| `entityId`    | TEXT     | The entity's primary key. |
| `operation`   | TEXT     | `INSERT` / `UPDATE` / `DELETE`. |
| `workspaceId` | TEXT?    | `null` for `WORKSPACE` and `USER`. |
| `payload`     | TEXT?    | JSON-encoded DTO. `null` for `DELETE`. |
| `createdAt`   | INTEGER  | epoch millis at enqueue. |
| `attempts`    | INTEGER  | bumped by `markFailed`. |
| `status`      | TEXT     | `PENDING` / `IN_FLIGHT` / `FAILED`. |
| `lastError`   | TEXT?    | error message captured on `markFailed`. |

Indices on `status`, `workspaceId`, `createdAt`. Index on `createdAt` is
what gives drain its FIFO order.

## `PendingMutationQueue` interface

```kotlin
interface PendingMutationQueue {
    suspend fun enqueue(mutation: PendingMutation)
    suspend fun pending(scope: SyncScope, limit: Int = DEFAULT_BATCH_LIMIT): List<PendingMutation>
    suspend fun markInFlight(ids: List<String>)
    suspend fun markCompleted(ids: List<String>)
    suspend fun markFailed(id: String, error: String)
    val pendingCount: Flow<Int>

    companion object { const val DEFAULT_BATCH_LIMIT: Int = 100 }
}
```

DAO realisation
([PendingMutationQueueImpl.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/internal/repository/PendingMutationQueueImpl.kt)):

| API           | DAO behaviour |
|---------------|----------------|
| `enqueue`     | Insert-if-absent with `status = PENDING`: `INSERT … SELECT … WHERE NOT EXISTS (SELECT 1 … WHERE entityType = :entityType AND entityId = :entityId AND operation = :operation AND status = 'PENDING')`. Rows carry no payload, so N queued rows for one entity all push the identical current value — renaming an account five times used to queue five pushes. The `PENDING` scope is the correctness half: a write landing while a row is `IN_FLIGHT` must create a new row, or the change made after the push read the entity is lost. Room's `@Index` has no `WHERE` clause, so it cannot be a unique index. |
| `pending`     | `SELECT * WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT :limit`. |
| `markInFlight`| `UPDATE … SET status = 'IN_FLIGHT' WHERE id IN (:ids)`. |
| `markCompleted` | `DELETE FROM pending_mutations WHERE id IN (:ids)`. **Completion deletes the row.** It does not flip a flag. |
| `markFailed`  | `UPDATE … SET status='PENDING', attempts = attempts + 1, lastError = :error WHERE id = :id`. **Failure resets to PENDING** so the next sync cycle picks it up again. |
| `pendingCount`| `SELECT COUNT(*) WHERE status != 'IN_FLIGHT'`. Counts both `PENDING` and `FAILED` for the UI badge. |

`scope` filtering is currently a no-op — `workspaceFilterFor(scope)`
returns `null` for every scope value. The DAO query then collapses the
filter (`workspaceId IS NULL OR workspaceId = :wid OR workspaceId IS NULL`).
This is a Phase-1 simplification documented in
[PendingMutationQueueImpl.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/internal/repository/PendingMutationQueueImpl.kt);
real per-workspace scoping needs the coordinator to know
`currentWorkspaceId`.

## `OutboxEnqueuer` — the dual-write helper

Every repository that writes a synchronisable entity injects
`OutboxEnqueuer` and calls `enqueueUpsert` or `enqueueDelete` after the
Room write. The helper centralises three concerns:

1. **Demo / version gating** — `isEnabled()` checks
   `session.currentFirebaseUid` is non-empty (so demo sessions skip)
   **and** `appVersionGate.isSyncAllowed()` returns `true` (so a build
   blocked by `appConfig/mobile.minSupportedAppVersionCode` cannot
   accumulate doomed mutations to push later — see [app-version-gate.md](app-version-gate.md)).
2. **Payload packaging** — repositories serialize their DTO with
   `outboxEnqueuer.json`, the same `Json` instance the upload pipeline
   uses to deserialize.
3. **Mutation construction** — UUID for `id`, `Clock.System.now()` for
   `createdAt`, `attempts = 0`, `lastError = null`, then `outbox.enqueue(...)`.

```kotlin
suspend fun isEnabled(): Boolean {
    val uid = session.currentFirebaseUid.flow.first()
    if (uid.isNullOrEmpty()) return false
    return appVersionGate.isSyncAllowed()
}
```

`isEnabled()` short-circuits both the JSON serialization and the queue
write. Repositories check it explicitly before serializing the DTO so
demo writes never even pay the encoding cost:

```kotlin
// AccountRepositoryImpl.enqueueUpsert
private suspend fun enqueueUpsert(entity: AccountEntity, operation: MutationOperation) {
    if (!outboxEnqueuer.isEnabled()) return
    val payload = outboxEnqueuer.json.encodeToString(entity.toDoc())
    outboxEnqueuer.enqueueUpsert(
        entityType  = SyncEntityType.ACCOUNT,
        entityId    = entity.id,
        workspaceId = WorkspaceId(entity.workspaceId),
        operation   = operation,
        payloadJson = payload,
    )
}
```

Source:
[AccountRepositoryImpl.kt](../../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/AccountRepositoryImpl.kt).
The same pattern lives in
[TransactionRepositoryImpl.kt](../../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/TransactionRepositoryImpl.kt),
[CategoryRepositoryImpl.kt](../../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/CategoryRepositoryImpl.kt),
[WorkspaceRepositoryImpl.kt](../../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/WorkspaceRepositoryImpl.kt),
[WorkspaceMemberRepositoryImpl.kt](../../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/WorkspaceMemberRepositoryImpl.kt),
and
[WorkspaceInviteRepositoryImpl.kt](../../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/WorkspaceInviteRepositoryImpl.kt).

For DELETE the helper is given the entity id only; payload is null:

```kotlin
override suspend fun delete(id: AccountId) {
    val existing = dao.getById(id.value)
    dao.delete(id.value)
    if (existing != null) {
        outboxEnqueuer.enqueueDelete(
            entityType  = SyncEntityType.ACCOUNT,
            entityId    = existing.id,
            workspaceId = WorkspaceId(existing.workspaceId),
        )
    }
}
```

The repo reads the row before the delete so it can supply
`workspaceId` to the outbox — the row is gone after delete, the outbox
needs to remember which workspace's collection to target.

### Atomicity caveat

The original sync plan (§4.3, Room schema changes) calls for
dual-writes to live inside a single Room transaction. **The current
implementation does not.** `OutboxEnqueuer` runs the enqueue **after**
the Room write, not inside `db.withTransaction { }`. The header comment
in
[OutboxEnqueuer.kt](../../sync/api/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/repository/OutboxEnqueuer.kt)
spells this out:

> Phase 1 limitation: enqueue happens after the Room write rather than
> inside a single transaction — KMP Room cross-DAO transactions are not
> yet wired up. If the enqueue throws between two writes the entity row
> exists locally but no push will be scheduled. Acceptable in dev;
> addressed when we reconcile via Phase 2 cursor/atomicity work.

The practical risk: if the process is killed between the Room write and
the outbox insert, the local row exists but no push will be scheduled.
The next pull cycle will not surface the inconsistency because it pulls
based on `updatedAt` cursors, and the local row has whatever
`updatedAt` it was given — if a peer device has not also written, the
divergence is silent.

Reconciliation strategy is **not** implemented yet. When KMP Room cross-DAO
transactions are wired up, the dual-write will move into
`db.withTransaction { }` and this caveat goes away.

## Drain cycle — `UploadPendingChangesUseCaseImpl`

Source:
[UploadPendingChangesUseCaseImpl.kt](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/UploadPendingChangesUseCaseImpl.kt).

Algorithm:

1. `outbox.pending(scope, limit = DEFAULT_BATCH_LIMIT)` — get up to 100
   `PENDING` rows, oldest first.
2. If empty → `UploadSummary(0).right()` immediately.
3. `outbox.markInFlight(batch.map { it.id })` — flip every selected row
   to `IN_FLIGHT`.
4. For each mutation, in order:
   - `cancelToken.throwIfCancelled()` — cooperative cancellation between
     items, not in the middle of a Firestore call.
   - `pushOne(mutation)` — Firestore write (see below).
   - Append to `completed` list.
   - `onProgress(UploadProgress(entityType, current, total))` — surfaces
     to the coordinator's `SyncStep.UploadingEntity`.
5. After the loop, `outbox.markCompleted(completed)` deletes the rows.

Recovery branches if `forEach` doesn't finish:

| Caught                   | Action |
|---------------------------|--------|
| `CancellationException`   | `markCompleted(completed)` for the rows already pushed; `markFailed(id, "cancelled")` for the rest of the batch (resets `IN_FLIGHT` → `PENDING`, bumps `attempts`); rethrow. |
| Any other `Throwable`     | Same cleanup, then `raise(failure.toSyncError())`. |

Notes:

- `markFailed(id, …)` resets `status` to `PENDING`, so on the next sync
  cycle the failed row is picked up again. There is no per-row backoff
  yet — all `PENDING` rows are eligible immediately. Backoff is listed
  as a Phase-6 polish item in the original sync plan (§5, phased rollout).
- A row stuck in `IN_FLIGHT` is a bug-state: it means the worker died
  hard between `markInFlight` and the recovery `catch`. There is no
  reaper. `pendingCount` excludes `IN_FLIGHT`, so such a row is invisible
  to the UI badge until it is cleaned up by hand or by Room wipe.
- The whole drain runs **outside** `db.withTransaction` — Firestore IO
  cannot be inside a Room transaction, and the queue is its own table.
  Each `markInFlight` / `markCompleted` / `markFailed` is its own DAO
  call.

### `pushOne` — entity-type dispatch

`UploadPendingChangesUseCaseImpl.pushOne` switches on
`SyncEntityType`:

| Entity type                  | Path |
|------------------------------|------|
| `WORKSPACE`                  | `workspaces/{id}` directly. `INSERT` / `UPDATE` use `set(...)`, `DELETE` writes a soft-delete tombstone (see below). |
| `ACCOUNT`                    | `workspaces/{wid}/accounts/{id}` |
| `CATEGORY`                   | `workspaces/{wid}/categories/{id}` |
| `TRANSACTION`                | `workspaces/{wid}/transactions/{id}` |
| `WORKSPACE_MEMBER`           | `workspaces/{wid}/members/{id}` (where `id == userId`) |
| `WORKSPACE_INVITE`           | `workspaces/{wid}/invites/{id}` |
| `WORKSPACE_REF` / `USER`     | **No-op.** Written via `UserRemoteRepository` directly, not via the outbox. |

Subcollection writes go through `pushStamped`, which decodes the JSON
payload, **rewrites `clientVersionCode`** from `appInfo.versionCode`, and
calls `set(...)`. Every push therefore re-stamps the `clientVersionCode`
field, so a row last written by an older client gets upgraded the next
time a current build pushes any version of it. This matches the Firestore
Rules `hasValidClientVersion()` enforcement described in
[app-version-gate.md](app-version-gate.md).

`MutationOperation.DELETE` never calls `firestore.delete()` — the rules
deny hard deletes on every entity collection. Instead every plugin pushes
a `TombstonePatch` (`deletedAt` + `updatedAt` + `clientVersionCode`) via
a field-mask `update`, skipping docs that never reached Firestore. Full
contract in
[sync-pull-lww.md → Tombstones](sync-pull-lww.md#tombstones-soft-delete).

## Demo + version gate at the entry

The dual-write contract is `OutboxEnqueuer.isEnabled()`-guarded. Two
sessions skip the outbox entirely:

1. **Demo session.** `currentFirebaseUid` is empty, so `isEnabled()`
   returns `false`. Local writes still go to Room — the user can add
   transactions, accounts, etc. — but nothing leaks to Firestore. This
   matches the policy in
   the sync plan (§2.11): demo data is a
   sandbox and never replicates.
2. **Build blocked by app-version gate.** When `AppVersionGate` says
   `Unsupported`, `isEnabled()` returns `false` so an obsolete build can
   not pile up mutations that a newer client would later push wholesale.
   The same gate is also enforced inside `runSyncRequest` so an outbox
   that was filled before the gate flipped is held back at sync time.
   See [sync-platform.md](sync-platform.md#app-version-gate) for the
   full gate behaviour.

## Lifecycle interactions

- **`LogoutUseCase`** calls
  [`SessionShutdownGate.shutdown()`](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/internal/SessionShutdownGateImpl.kt)
  → `SyncCoordinator.cancelAll()` →
  `BackgroundSyncScheduler.cancelAll()` **before**
  `LocalDataResetRepository.clearAll()`. Order matters: an in-flight push
  that started reading from `pending_mutations` must be cancelled before
  Room is wiped, otherwise it would either crash or push half-deleted
  state.
- **`LocalDataResetRepository.clearAll`** wipes
  [`pending_mutations` and `sync_meta` first](../../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/LocalDataResetRepositoryImpl.kt),
  before any FK-bearing table. They have no FK constraints, so they are
  cheap to delete and need to disappear before the entities they referred
  to.

## What "scope" means for the outbox today

Drain is currently **scope-blind**: every sync run, regardless of
`SyncReason` or `SyncScope`, will pull up to 100 oldest `PENDING` rows
across all workspaces and push them. This means a `BACKGROUND` cycle
triggered by any reason will still push pending changes; conversely a
`UploadOnly` cycle will not pull anything but **will** drain.

The intent (and the comment in `PendingMutationQueueImpl`) is that
Phase 2/3 will introduce per-workspace filtering once the coordinator
knows about `currentWorkspaceId`. Until then, treat the outbox as a
single global queue.

## What's not (yet) in the outbox

- **Backoff per row.** `markFailed` resets to `PENDING` immediately. A
  retryable failure that re-fails will busy-spin until the worker either
  succeeds, the row is cancelled, or `attempts` becomes uncomfortably
  large. There is no `lastAttemptAt` column and no `WHERE
  lastAttemptAt < now() - backoff(attempts)` filter.
- **Outbox compaction.** A sequence of `INSERT t1`, `UPDATE t1`,
  `DELETE t1` rolls forward as three Firestore writes; it is not collapsed
  to one `DELETE`.
- **Dead-letter / inspection UI.** A row stuck with high `attempts` does
  not surface anywhere except `lastError`. There is no operator surface
  to drop / requeue / inspect.

These are listed under Phase 6 polish in the original sync plan
(§5, phased rollout).

## Mental model — write path summary

```
UI tap → ViewModel → DomainUseCase
                       │
                       ▼
        Repository.insert / update / delete
                       │
       ┌───────────────┼────────────────┐
       ▼                                ▼
  Room.dao.write              OutboxEnqueuer.enqueue*  (skipped for demo / blocked-version)
       (always succeeds)          │
                                  ▼
                         pending_mutations row
                         (status = PENDING)

                — later, when coordinator runs —

  UploadPendingChangesUseCase
       │
       ├ pending(scope, limit)        — read up to 100 PENDING rows
       ├ markInFlight(ids)            — atomic flip
       ├ for each: pushOne(...)       — Firestore set / tombstone update
       │     onProgress → coordinator → SyncStep.UploadingEntity
       ├ markCompleted(done)          — deletes rows from pending_mutations
       └ on failure: markFailed(...)  — resets to PENDING, attempts++
```

The pull path (see [sync-pull-lww.md](sync-pull-lww.md)) writes
**directly through DAOs**, deliberately bypassing `OutboxEnqueuer`, so
rows landing from Firestore do not trigger another push.
