# Sync v2 — Pull, LWW, Tombstones

<!-- DOCS:TOC -->
## Contents
- [Sync v2 — Pull, LWW, Tombstones](#sync-v2--pull-lww-tombstones)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
- [Cursor-based incremental pull](#cursor-based-incremental-pull)
- [Collections in scope](#collections-in-scope)
  - [User-scoped collections (phase 3)](#user-scoped-collections-phase-3)
- [Apply per doc](#apply-per-doc)
  - [Members — special-case userId stub](#members--special-case-userid-stub)
- [LWW conflict resolver](#lww-conflict-resolver)
- [Outbox bypass](#outbox-bypass)
- [Cursor + apply atomicity](#cursor--apply-atomicity)
- [Tombstones (soft delete)](#tombstones-soft-delete)
  - [Local tombstone retention](#local-tombstone-retention)
- [WorkspaceInvite](#workspaceinvite)
- [SyncMetaRepository](#syncmetarepository)
- [Recalc placeholder](#recalc-placeholder)
- [v1 still in the system](#v1-still-in-the-system)
<!-- DOCS:END -->

## TL;DR for agents

- Pull is cursor-based on `updatedAt` per `(workspaceId, collection)`.
- Conflict resolution is LWW; ties go to local (stable, idempotent re-pulls).
- Pull writes go through DAOs directly — never through the outbox.
- Tombstones land as hard local deletes — except transactions, which keep a
  local `deletedAt` row (issue #346). The cursor advances past them either way.
- Collections are pulled in order: members → invites → accounts → categories → transactions.

READ WHEN:
- changing pull batching, cursor, or conflict logic
- adding a new pulled collection or a new resolver
- modifying tombstone / soft-delete behaviour
- editing `SyncMeta` schema or DAO

<!-- AI:SECTION id=sync-pull-lww-rules task=sync,pull,lww,tombstones,cursor -->
## Rules

- Pull writes must use DAO calls directly — `OutboxEnqueuer.*` is forbidden
  on the pull path so remote rows do not loop back as pushes.
- Cursor advancement must monotonically use the max `updatedAt` actually
  applied; never advance past a doc that has not been written.
- LWW tie-break (`remoteUpdatedAt == localUpdatedAt`) is **TakeLocal** —
  this is what makes re-pulls idempotent.
- A pulled member doc must be preceded by an `INSERT OR IGNORE` into `users`
  to satisfy the FK on `workspace_members.userId`.
- `deletedAt != null` on a pulled DTO means hard local delete.
- `SyncScope.UploadOnly` returns immediately from the pull use case.
- Active workspace is read once (`session.currentWorkspaceId.flow.first()`);
  if `null`, the pull is a no-op.
<!-- AI:END -->

The pull stage downloads remote changes per workspace, resolves
conflicts, and writes the result directly to Room — bypassing the outbox
so a remote-originated change does not loop back as a push.

Source:
[PullRemoteChangesUseCaseImpl.kt](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/PullRemoteChangesUseCaseImpl.kt).

## Cursor-based incremental pull

Per `(workspaceId, collection)`:

1. `syncMeta.markAttempt(workspaceId, collection, now())` — observability.
2. `cursor = syncMeta.cursor(workspaceId, collection)` — last successful
   `updatedAt` we wrote, in epoch millis. `null` ⇒ first pull, treated
   as `0`.
3. Firestore query:
   ```
   workspaces/{wid}/{collection}
     .where("updatedAt", ">", cursorMillis)
     .orderBy("updatedAt", ASCENDING)
     .limit(BATCH_SIZE)              // 100
   ```
4. Apply each doc (see below). Track `maxUpdatedAt` across the batch.
5. After the loop:
   - `setCursor(workspaceId, collection, maxUpdatedAt)` if it advanced.
   - `markSuccess(workspaceId, collection, now())`.
6. If the batch is empty, `markSuccess` is still called.

`pullCollection` returns `(downloadedCount, conflictCount)`. The use case
sums these across the collections in scope and returns `PullSummary`.

The "active workspace" comes from
`session.currentWorkspaceId.flow.first()`. If it is `null`, the use case
returns `PullSummary(0, 0)` immediately (a freshly-logged-in user with
no active workspace yet — the bootstrap flow handles workspace
selection elsewhere).

## Collections in scope

```kotlin
private fun collectionsInScope(scope: SyncScope) = when (scope) {
    UploadOnly -> emptyList()
    ActiveWorkspace, ChangedSinceLastSync, AllUserData -> listOf(
        WORKSPACE_MEMBERS,
        WORKSPACE_INVITES,
        ACCOUNTS,
        CATEGORIES,
        TRANSACTIONS,
    )
}
```

The order is deliberate. Members and invites come **before** entity
collections so a freshly-pulled workspace already has its
`workspace_members` row by the time entity queries that gate on
membership run. This replaces the v1 `ensureLocalOwnerMembership`
workaround in
[SyncCoordinatorWorkspaceSyncer.kt](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/SyncCoordinatorWorkspaceSyncer.kt).

`SyncScope.AllUserData` collapses to the same list — there is no
multi-workspace fan-out yet (still per-`currentWorkspaceId`).

`WORKSPACES`, `WORKSPACE_REFS`, `USERS` are **not** part of the cursor
pipeline. The user / workspace docs are still written via
`WorkspaceSyncRepositoryImpl` (v1) and `UserRemoteRepository` directly.

### User-scoped collections (phase 3)

`SyncEntityPlugin.pullScope` says which document tree a plugin's collection hangs
off. The workspace loop above runs `PullScope.Workspace` plugins only; a separate
phase runs the `PullScope.User` ones once, with `scopeKey = uid`.

Today that is `users/{uid}/config` — one document per synced setting
([ADR-004](../adr/ADR-004-configuration.md)). Without the discriminator a
user-scoped plugin would be run per workspace and would either be handed the
workspace root document or query a path that does not exist.

The phase is **cursorless**: it reads the whole collection on every pull, because
the synced set is around ten tiny documents and the alternative is cursor
bookkeeping in a new scope plus a rule that has to permit a filtered, ordered
query rather than a bare `list`. "Remote is not newer" is therefore the normal
outcome, and the config plugin reports it as *skipped*, not as a conflict.

A denial in this phase skips the collection and logs, rather than failing the
pull: these documents are private to one user and hold nothing another device
cannot re-derive, while a rules gap that failed the pull would read as "nobody can
sign in".

## Apply per doc

For each `DocumentSnapshot` in the batch:

1. **Tombstone check.** If the DTO has `deletedAt != null`, remove the
   local row via the matching DAO (e.g. `accountDao.delete(id)`) and
   record `applied = true, wasConflict = false`. The cursor still
   advances on tombstones — that is exactly how a delete propagates from
   one device to another.
   Transactions are the exception: they *mark* the row instead, with
   `transactionDao.softDelete(id, dto.deletedAt!!)` — a targeted UPDATE,
   not an upsert of the decoded doc, since a tombstone patch carries no
   real fields and a device that never held the row must not have one
   conjured out of it (the `accountId` foreign key would reject it
   anyway). The tombstone still wins unconditionally, without consulting
   the resolver: a delete is not a field-level edit a newer local write
   can outrank.
2. **Conflict resolution.** Read the current local entity, build a
   `ConflictMetadata`, and ask the resolver:
   ```kotlin
   conflictResolver.resolve(
       local = local,
       remote = dto.toEntity(...),
       metadata = ConflictMetadata(
           entityType,
           entityId    = snap.id,
           localUpdatedAt  = local?.updatedAt?.let(Instant::fromEpochMilliseconds),
           remoteUpdatedAt = Instant.fromEpochMilliseconds(dto.updatedAt),
       ),
   )
   ```
3. **Apply the resolution** through `applyResolution`:
   | Resolution        | Effect on counts             | Effect on Room |
   |-------------------|------------------------------|----------------|
   | `TakeRemote(v)`   | `applied++`                  | DAO upsert. |
   | `TakeLocal(_)`    | `wasConflict++`              | None (local wins). |
   | `Merged(v)`       | `applied++` and `wasConflict++` | DAO upsert. |
   | `Skip`            | `wasConflict++`              | None (deferred to a future inbox). |

`applied` is what flows into `PullSummary.downloadedCount` (and gates the
`SyncStep.RecalculatingProjections` step in the coordinator).

### Members — special-case userId stub

Member docs hold a roster row whose `userId` is the doc id. Local
`workspace_members.userId` is FK-bound to `users.id`, but peer users are
not pulled into the local `users` table by the rest of the pipeline.
Before applying a member doc the use case stubs a row:

```kotlin
userDao.insertIgnore(
    UserEntity(id = snap.id, displayName = dto.displayName.ifEmpty { null }, isAnon = false),
)
```

`INSERT OR IGNORE` keeps the current user's existing row intact while
giving foreign-membership rows their FK target. See
[PullRemoteChangesUseCaseImpl.kt](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/PullRemoteChangesUseCaseImpl.kt).

## LWW conflict resolver

Source:
[LwwConflictResolver.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/repository/LwwConflictResolver.kt).

```kotlin
override fun <T : Any> resolve(local: T?, remote: T, metadata: ConflictMetadata): ConflictResolution<T> {
    if (local == null) return TakeRemote(remote)
    val localUpdatedAt = metadata.localUpdatedAt ?: Instant.DISTANT_PAST
    return if (metadata.remoteUpdatedAt > localUpdatedAt)
        TakeRemote(remote)
    else
        TakeLocal(local)
}
```

Tie-breaking on equal `updatedAt` favours **local** — stable, so a
re-pull of the same remote doc has no effect.

There is no per-field merge yet, no manual-resolution `Skip` (the
resolution exists in the sealed interface but is never produced by
`LwwConflictResolver`).

`ConflictResolver` is bound by `@Single(binds = [ConflictResolver::class])`
in `:sync-surfer`, so swapping resolvers is a one-line DI change once a
field-level resolver lands.

## Outbox bypass

The pull writes directly through `*Dao` calls
(`accountDao.upsertAll(listOf(entity))`), **never through
`OutboxEnqueuer.enqueueUpsert`**. That is by design:

- A remote row was already pushed by some peer; the local copy must
  match without re-emitting it back to Firestore.
- The repositories' `insert / update / delete` methods *would* enqueue —
  the pull intentionally goes through DAOs to skip them.

Operationally this means the pull and the user-write paths share
**different Room entry points**. There is no shared `upsertWithoutOutbox`
helper; the contract is that DAO calls are bare-DB and that anything
calling `Repository.insert/update/delete` is a "user write" that should
hit the outbox.

## Cursor + apply atomicity

The plan in
the original sync plan (§4.3, Room schema changes) calls for cursor
advancement and applied rows to live inside one Room transaction. **The
current implementation does not** wrap them in `db.withTransaction { }`.
What it does:

- Each per-doc `applyResolution` calls a single DAO method (effectively
  one Room transaction per doc).
- Cursor advance happens **after** the per-doc loop, by a separate
  `syncMeta.upsert(...)` call.

If the process dies between applying doc `N` and advancing the cursor,
the next pull will re-fetch from the old cursor; the repeated docs are
harmless because LWW with equal `updatedAt` returns `TakeLocal`. Net
effect: at-least-once apply with idempotent writes — acceptable for the
LWW resolver.

If a future resolver introduces side effects (telemetry, outbox writes),
the cursor advancement should be moved into the same transaction. Today
it is not necessary.

## Tombstones (soft delete)

DTOs in
[SyncDtoMappers.kt](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/SyncDtoMappers.kt)
all carry `deletedAt: Long? = null`. A non-null value means "this row
has been deleted on a peer device". The current pull behaviour:

- `deletedAt != null` ⇒ DAO `delete(id)` locally. **Hard delete** in
  Room, soft delete in Firestore — the doc is preserved as a tombstone
  so future pulls (with `cursor > deletedAt`) don't see it again.
- **Transactions are soft-deleted in Room too** (issue #346):
  `transactionDao.softDelete(id, deletedAt)` marks the row, every read
  query in `TransactionDao` filters on `deletedAt IS NULL`, and
  `TransactionRepository.restore(id)` lifts the tombstone in a single
  UPDATE. That is what makes Undo survive process death, and it is also
  what keeps an edit that was already open when a peer's delete arrived
  from colliding with the surviving primary key
  (`UpdateTransactionUseCase`). A restore pushes
  an ordinary upsert whose `deletedAt` is null, which lifts the
  tombstone remotely as well — no INSERT-after-DELETE flicker.
- The cursor still advances past tombstones — the doc has a real
  `updatedAt`, just like any other update.

The push side is symmetric: `MutationOperation.DELETE` writes a
`TombstonePatch` — `deletedAt`, `updatedAt`, and `clientVersionCode`, all
via a field-mask `update` — instead of `firestore.delete()` (see
[TombstonePush.kt](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/plugin/TombstonePush.kt),
shared by every `*SyncPlugin.push`). Firestore Rules deny hard deletes
outright (`allow delete: if false` on every entity collection), so the
tombstone update is the only delete shape the server accepts.

Push-side tombstone contract:

- `deletedAt` and `updatedAt` both take the mutation's **enqueue time**
  (`PendingMutation.createdAt`), so a retried push writes an identical
  patch (idempotent) and peers' `updatedAt > cursor` pulls fetch the
  tombstone like any other update. Client clock, not
  `serverTimestamp()` — the clock-skew caveat in
  [sync-gaps.md](sync-gaps.md) applies to tombstones too.
- A DELETE whose doc never reached Firestore (entity created and
  deleted between two drains — the INSERT push no-ops because its
  `getById` finds no live row) is **skipped**: `pushTombstone` checks
  `get().exists` first. Updating a missing doc would raise NOT_FOUND on
  every retry and wedge the batch; a doc that never existed remotely has
  nothing for peers to forget.
- A soft delete enqueues exactly one DELETE mutation. Deleting an
  already-tombstoned row enqueues nothing: a second tombstone would
  carry a later `deletedAt` than the one peers already agreed on.
- Tombstoned **docs** are never garbage-collected today — remote
  retention/GC is a known gap, listed in [sync-gaps.md](sync-gaps.md).
  Local transaction tombstones *are* collected; see below.

### Local tombstone retention

`transactions.deletedAt` rows are purged by
`PurgeDeletedTransactionsUseCase` (`:domain`), which drops every
tombstone older than **30 days**.

- **Who runs it:** the app, once per launch, from `AppLaunchViewModel`.
  Not a background worker: it is a single indexed `DELETE` over rows
  nobody reads, and scheduling it on two platforms would buy nothing.
  It runs off the startup path and its failures are logged and
  swallowed, so housekeeping can never keep the app off its first
  screen.
- **Why 30 days:** everything that reads a tombstone is local and
  short-lived — the Undo Snackbar (seconds), an edit that was already
  open when the row was deleted (`UpdateTransactionUseCase`), and a CSV
  import, which has to find the tombstone rather than insert over a
  surviving id. Thirty days is far past all three and short enough that
  deleted rows do not accumulate.
- **Measured from the delete's own timestamp**, which for a pulled
  tombstone is the *deleting* device's clock, not the moment this one
  heard about it. A delete pushed by a peer that had been offline longer
  than the window therefore arrives already expired and is collected on
  the next launch. Deliberate: none of the three readers above outlives
  the trip, there is no UI for undoing another device's delete, and an
  import that finds no tombstone just inserts the row — same end state.
- **What it does not touch:** the remote doc keeps its `deletedAt`.
  Purging here says nothing about any peer's copy; that tombstone stays
  on the server for them to pull, and collecting it is the separate gap
  above.
- After the purge the row is gone for good: a restore finds nothing and
  is a no-op, and a CSV import of the same id inserts it fresh.

## `WorkspaceInvite`

Invite docs have their own status field (`PENDING / ACCEPTED / DECLINED
/ REVOKED / EXPIRED`); `deletedAt` is kept as a defensive fallback only.
Pull treats both signals: if `deletedAt != null`, hard-delete; otherwise
follow conflict resolution like the other entities. See
[PullRemoteChangesUseCaseImpl.applyInvite](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/PullRemoteChangesUseCaseImpl.kt).

## `SyncMetaRepository`

Interface in
[SyncMetaRepository.kt](../../sync/api/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/repository/SyncMetaRepository.kt)
(`:sync/api`, so feature modules can read the cursors without depending on the
runtime), implementation in
[SyncMetaRepositoryImpl.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/internal/repository/SyncMetaRepositoryImpl.kt),
schema in
[SyncMetaEntity.kt](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/db/entity/SyncMetaEntity.kt).

Composite PK `(workspaceId, collection)`, no FK on `workspaceId`. Three
timestamps:

| Field                 | Set by | Read by |
|------------------------|--------|---------|
| `lastPulledAt`         | `setCursor` after a successful batch. | `cursor()` for the next pull's `WHERE updatedAt > ?`; rendered on Settings → Sync, where a cursor sitting ahead of a remote write is the answer to "why did the pull bring nothing". |
| `lastSyncSuccessAt`    | `markSuccess` on every collection that finishes (even if empty). | UI badges. |
| `lastSyncAttemptAt`    | `markAttempt` at the start of `pullCollection`. | UI badges, freshness signal. |

`clearScope(scopeKey)` is the per-workspace wipe — also what Settings → Sync
calls behind "Reset cursors & re-pull", which then requests a MANUAL sync so the
re-pull actually happens instead of waiting for the next trigger. That screen
cancels the coordinator and waits for it to go idle *before* clearing: a pull in
flight writes `setCursor(now)` per collection as it finishes each one, and a new
request queues behind it rather than merging into it, so wiping mid-run would be
undone before the re-pull ever ran. Full table
clear is `deleteAll()` from `SyncMetaDao`, called by
`LocalDataResetRepositoryImpl.clearAll()` on logout.

Reading is via DAO `get(workspaceId, collection)`. Updates are
`upsert(...)` — the impl reads, copies the field, writes. The plan
(sync plan §4.3, Room schema changes) calls these out as
needing to live inside the same transaction as the per-batch row writes;
they are not, today.

## Recalc placeholder

`RecalculateLocalProjectionsUseCase` exists but is wired to
[NoOpRecalculateLocalProjectionsUseCase](../../sync/default/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/internal/usecase/NoOpRecalculateLocalProjectionsUseCase.kt):

```kotlin
override suspend fun invoke(scope: ProjectionScope, cancelToken: SyncCancelToken):
    SyncResult<ProjectionSummary> = ProjectionSummary(recalculatedCount = 0).right()
```

The coordinator only invokes the step when `pullSummary.downloadedCount
> 0` (coordinator FAQ §7), so the NoOp costs nothing on
idle cycles. Real account-balance / projection recalculation is a
separate subsystem (depends on signed amounts, `OPENING_BALANCE`
handling, multi-currency) and lands later — see
[sync-gaps.md](sync-gaps.md).

## v1 still in the system

[SyncCoordinatorWorkspaceSyncer.kt](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/SyncCoordinatorWorkspaceSyncer.kt)
is the v1 push→pull coordinator. It still owns workspace-level doc
push/pull (the document at `workspaces/{wid}` itself) and the legacy
manual sync pipeline.

It is intentionally left running until Phase 3 finishes porting the
workspace doc into the cursor-based pipeline — see the comment near the
top of
[PullRemoteChangesUseCaseImpl.kt](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/PullRemoteChangesUseCaseImpl.kt).
