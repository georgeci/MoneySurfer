# Integration Test Debt

Date: 2026-04-30

Backlog of integration-test scenarios that came up but didn't make this iteration. Each item carries enough context to pick up later without re-deriving the design. Cross-links existing IT classes — read those first to know the harness shape before adding new tests.

Existing IT inventory (under `integration-test/src/androidDeviceTest/`):
- [`OutboxIdempotentRepushIT`](../integration-test/src/androidDeviceTest/kotlin/com/georgeci/moneysurfer/integration/android/OutboxIdempotentRepushIT.kt) — terminal-status invite re-push rule branch.
- [`OutboxDrainAndRecoveryIT`](../integration-test/src/androidDeviceTest/kotlin/com/georgeci/moneysurfer/integration/android/OutboxDrainAndRecoveryIT.kt) — happy-path drain + partial-batch failure (`markFailed` semantics).
- [`PullCursorAndLwwIT`](../integration-test/src/androidDeviceTest/kotlin/com/georgeci/moneysurfer/integration/android/PullCursorAndLwwIT.kt) — single-client cursor + TakeRemote/TakeLocal/tombstone via manual Firestore rewrites.
- [`WorkspaceSyncRoundTripIT`](../integration-test/src/androidDeviceTest/kotlin/com/georgeci/moneysurfer/integration/android/WorkspaceSyncRoundTripIT.kt) — v1 sync round trip (push 5 → wipe → pull 5).
- [`ClearWorkspaceIT`](../integration-test/src/androidDeviceTest/kotlin/com/georgeci/moneysurfer/integration/android/ClearWorkspaceIT.kt) — soft-delete sweep verifies `deletedAt` lands on every doc.
- [`MultiClientConvergenceIT`](../integration-test/src/androidDeviceTest/kotlin/com/georgeci/moneysurfer/integration/android/MultiClientConvergenceIT.kt) — bidirectional happy-path (owner↔peer) via two named `FirebaseApp` instances.
- [`MultiClientConflictIT`](../integration-test/src/androidDeviceTest/kotlin/com/georgeci/moneysurfer/integration/android/MultiClientConflictIT.kt) — concurrent edits LWW battle, tombstone over peer's local newer edit, equal-`updatedAt` tie-breaker.
- Shared bootstrap: [`MultiClientFixture`](../integration-test/src/androidDeviceTest/kotlin/com/georgeci/moneysurfer/integration/android/MultiClientFixture.kt) — `bootstrapTwoClient(...)` returns both sides pre-synced to baseline.

---

## Sync + conflict resolution

### 1. Stale push overwrites newer remote (no server-side LWW) — production gap test

**Why:** server-side merge isn't there. If peer drains an outbox mutation whose `updatedAt` is older than what's already on Firestore (e.g. peer was offline while owner pushed twice), peer's `set()` blindly overwrites the newer remote. Real failure mode worth visualising in a test.

**Setup:** owner pushes account at `t2`. Peer's outbox already holds an enqueued update payload at `t1 < t2` (enqueued before peer learned about t2). Peer drains.

**Acceptance:**
- Peer drain returns `Either.Right` (rules don't catch this — both writes are valid `update`s for a member).
- Server-side read shows peer's `t1` content with peer's `t1` `updatedAt`.
- Document the gap with a clear FIXME pointing at the resolver — fix is server-side LWW (Cloud Function or rule with `request.resource.data.updatedAt > resource.data.updatedAt`) or per-doc optimistic-concurrency token.

---

### 2. Resurrection after outbox-pending mutation collides with remote tombstone

**Why:** pull bypasses the outbox. If a tombstone arrives while a stale upsert is still queued, the order is: pull deletes locally → outbox drain pushes the old payload → row resurrects on Firestore at the old `updatedAt`. Next pull then ignores it (cursor already past the tombstone), so server has a "ghost" doc that nobody reads.

**Setup:** owner enqueues `INSERT` for account A; before drain, manually publish a tombstone for A on Firestore via owner's Firestore client (`set(deletedAt=tombTs)`). Owner pulls (tombstone applied, local row deleted). Owner drains the queue.

**Acceptance:**
- Server-side read shows `name = "<old payload>"`, `deletedAt = null`, `updatedAt = <old updatedAt>` — i.e., resurrection happened.
- FIXME documenting that outbox should consult the latest known `deletedAt` before pushing (or repos should drop pending mutations on tombstone pull). Not blocking — flag as known-bug.

---

### 3. Three-client convergence

**Why:** two-client tests prove pairwise convergence; three-way reveals fan-out issues (e.g. invite badge counters, member-roster delta size).

**Setup:** add `clientCHarness = AndroidIntegrationHarness(appName = "c-$tag")` alongside existing two. Owner adds C as member; A pushes edit; B and C both pull, both converge.

**Acceptance:** all three local `accountDao().getById(...).name` agree after their respective pulls. All three cursors at the same `updatedAt`.

**Note:** named-FirebaseApp lifecycle — make sure `tearDown` deletes all three. The existing `EmulatorEnv` pattern handles it per-instance.

---

### 4. Cancellation mid-batch on `uploadPendingChanges`

**Why:** the use case has a cooperative cancellation branch (catch `CancellationException` → `markCompleted(processed)` + `markFailed(rest with "cancelled")`) that nothing exercises. Real scenario: user backs out of a sync screen, cancel token flips, batch should leave a consistent queue.

**Setup:** enqueue 3 valid mutations. Pass an `onProgress` lambda that calls `cancelToken.cancel()` after the first event. Run drain.

**Acceptance:**
- `uploadPendingChanges` throws `CancellationException`.
- Mutation #1 is gone from the queue (markCompleted).
- Mutations #2 and #3 are PENDING with `attempts = 1` and `lastError = "cancelled"`.
- Firestore has #1's document but not #2 or #3.

**Pre-req:** check `SimpleCancelToken.cancel()` API surface — used inside the harness as a no-op token; the real impl is in `sync/api/SyncCancelToken.kt`.

---

### 5. Pull batch pagination (`BATCH_SIZE = 100`)

**Why:** [`PullRemoteChangesUseCaseImpl.BATCH_SIZE`](../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/PullRemoteChangesUseCaseImpl.kt) is 100. The cursor-paged pull is correct iff the next query picks up where the last one left off. Single-batch tests don't prove it.

**Note (issue #342):** the pull now *drains* — `pullBatches` loops until a batch comes back short of `BATCH_SIZE`, so one sync pulls the whole collection rather than 100 docs per minute. Unit coverage against a fake reader is in `PullRemoteChangesUseCaseImplSpec` ("a collection larger than one batch is drained by a single pull"). What is still missing is the same thing against the real Firestore emulator, where the query, the cursor and the ordering are the SDK's rather than a fake's.

**Setup:** seed > 100 (e.g. 250) account docs into Firestore via `set(...)` in a loop, with strictly-increasing `updatedAt`. One pull.

**Acceptance:**
- One pull: `downloadedCount = 250`, cursor advances to the 250th doc's `updatedAt`.
- Local Room has all 250 rows.
- Second pull: `downloadedCount = 0`.

---

### 6. Equal-updatedAt with non-baseline starting state (regression for `seed = 0L` quirk)

**Why:** [`PullRemoteChangesUseCaseImpl`](../data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/PullRemoteChangesUseCaseImpl.kt) treats null cursor as `0L`. A doc with `updatedAt = 0L` is invisible to the first pull. Worth a sentinel test so this trap is documented in code.

**Setup:** seed an account with `updatedAt = 0L` directly into Firestore. Pull with null cursor.

**Acceptance:** pull's `downloadedCount = 0` (the doc is missed). Add an explanatory comment + link to the FAQ entry that explains why we use `1_700_000_000_000L` as the canonical seed timestamp.

---

## Tombstone / FK consistency

### 7. `transactions.accountId` cascade migration + propagation test

**Status:** production gap surfaced by [`PullCursorAndLwwIT.pull_takes_local_when_local_is_newer_and_propagates_remote_tombstone`](../integration-test/src/androidDeviceTest/kotlin/com/georgeci/moneysurfer/integration/android/PullCursorAndLwwIT.kt) — the test currently sidesteps SQLite 787 by manually deleting the local transaction before the tombstone pull (FIXME there).

**Real fix:**
- Option A (preferred): add `onDelete = CASCADE` to `TransactionEntity.accountId` FK. Bumps Room schema version → migration script that copies the table with the cascade clause. Same call for `categories.parentId` if introduced later.
- Option B: reorder `PullRemoteChangesUseCaseImpl.collectionsInScope` so dependents come first for tombstones (transactions → categories → accounts → members). Mixed insert/delete-friendly order is messier — INSERTS need parents first.

**Test once fix is in:**
- Remove the FIXME workaround in `PullCursorAndLwwIT`.
- Add a new IT: seed account + transaction, owner publishes tombstone for account only, peer pulls, transaction deleted via cascade, account row gone, no FK exception. Verifies cascade actually fires.

---

### 8. Workspace-level tombstone propagates to all sub-collections via subsequent pulls

**Why:** [`ClearWorkspaceIT`](../integration-test/src/androidDeviceTest/kotlin/com/georgeci/moneysurfer/integration/android/ClearWorkspaceIT.kt) verifies the wire-side soft-delete writes land. Doesn't prove the *peer* side handles a soft-deleted workspace gracefully. Open question: should peer's pull still apply tombstoned member rows? Should peer's local Room remove the workspace row when the workspace doc has `deletedAt`?

**Setup:** two clients synced. Owner runs `clearWorkspace(wid)`. Peer pulls.

**Acceptance:** define + assert what should happen. Likely:
- Peer's member rows tombstoned → hard-deleted on pull.
- Peer's account/category/transaction rows tombstoned → hard-deleted on pull.
- Peer's local workspaces row: ??? — currently v2 pull doesn't touch workspaces. Either v2 needs to learn the workspaces collection, or peer keeps a stale shell. Decide first, then test.

**Pre-req:** decision in `md/sync.md` on workspace-doc deletion semantics. Track separately.

---

## Domain use cases (currently mocked at unit level)

### 9. `AcceptInviteUseCase` end-to-end (recipient flow)

**Why:** unit tests stub remote and run the local-only branch (`currentFirebaseUid = null`). The remote branch — invitee writes their member doc + flips invite status — only exists in `OutboxIdempotentRepushIT` indirectly (via the rule-fix scenario). No happy-path coverage of the full use case.

**Setup:** owner creates invite for peer's email → pushes. Peer signs in, runs `AcceptInviteUseCase(inviteId)`.

**Acceptance:**
- Local: peer's `workspace_members` row exists with role from invite, status=ACTIVE.
- Local: peer's invite row updates to status=ACCEPTED.
- Outbox drain succeeds: Firestore member doc created at `workspaces/{wid}/members/{peerUid}`, invite doc updates to ACCEPTED.
- Owner pulls members → sees peer's row.

**Pre-req:** wire `AcceptInviteUseCase` into `AndroidIntegrationHarness`. The use case is in `domain/usecase/`; needs `inviteRepository`, `memberRepository`, `userRemoteRepository`, `session`. Most of those are already in the harness.

---

### 10. `SendInviteUseCase` end-to-end (owner flow)

**Why:** owner side of the invite handshake. Tests userEmails-table lookup, invite doc creation under rules' `isOwner(wid)` branch.

**Setup:** owner signs in. Peer pre-registers an account so `userEmails/<peer-email>` exists. Owner runs `SendInviteUseCase(workspaceId, peerEmail, role)`.

**Acceptance:**
- Local invite row created.
- Outbox drain: Firestore invite doc at `workspaces/{wid}/invites/{id}` with status=PENDING, `targetUserId = peerUid` (resolved via `userEmails`), `invitedByUserId = ownerUid`.
- Peer's collection-group query (test 11 below) would find this invite.

---

### 11. Recipient invite discovery via collection-group query

**Why:** `firestore.rules` has a wildcard block `match /{path=**}/invites/{inviteId}` allowing read for `targetUserId == auth.uid` OR `email match`. Without this, recipients can't see invites without already being members. The wildcard is fragile (separate `allow read` clauses, type-guards on email tokens) and easy to break in future rule edits.

**Setup:** owner creates an invite targeting peer (test 10's setup). Peer signs in but is **not** a member of the workspace yet. Peer runs `firestore.collectionGroup("invites").where("targetUserId", "==", peerUid).get()`.

**Acceptance:**
- Query returns the invite — proves the wildcard rule's `targetUserId` branch is live.
- Repeat with `where("email", "==", peerEmailLowercased)` — proves the `email` fallback branch.
- Repeat with a third user not targeted by the invite — query returns empty (rule denies, doesn't error).

**Pre-req:** there's an `IncomingInviteRemoteRepository` in `:data` doing exactly this query. Wire it into harness OR call `firestore.collectionGroup` directly from the test.

---

## Member lifecycle

### 12. Peer self-leaves, owner sees status update

**Why:** firestore.rules' `members/{uid}` self-update branch: `request.auth.uid == uid && status in ["ACTIVE", "LEFT"]`. Untested end-to-end; rule edits could regress silently.

**Setup:** [`bootstrapTwoClient`](../integration-test/src/androidDeviceTest/kotlin/com/georgeci/moneysurfer/integration/android/MultiClientFixture.kt). Peer updates own member row to status=LEFT (via `memberRepository.markLeft(...)` if exists, or DAO + outbox).

**Acceptance:**
- Peer drain succeeds (rule allows self-update because status ∈ {ACTIVE,LEFT} and role unchanged).
- Owner pulls → sees peer's row with status=LEFT.
- Negative case: peer tries status=REMOVED → drain returns `Either.Left(PERMISSION_DENIED)` (only owner can REMOVE).

---

### 13. Owner removes peer, peer pulls and observes the removal

**Why:** members rule's owner branch (`isOwner(wid)`) untouched by current ITs. Owner-driven member-state changes are core to access control.

**Setup:** bootstrap. Owner sets peer's member row to status=REMOVED.

**Acceptance:**
- Owner drain succeeds.
- Peer pulls → local member row updates to status=REMOVED.
- Negative: any subsequent peer write to a sub-collection should fail (`isMember(wid)` evaluates true for now because the doc still exists; but downstream code branches on status). If the rule actually checks status=ACTIVE, add that and assert here.

---

## Coordinator / orchestrator

### 14. `SyncCoordinator` end-to-end (upload then pull, event emission)

**Why:** the coordinator is the production glue. Currently nothing in the IT suite drives it — every test calls `uploadPendingChanges` / `pullRemoteChanges` directly. A breakage between `SyncCoordinatorImpl` and the use cases (e.g. wrong scope, missing event) wouldn't surface.

**Setup:** wire `SyncCoordinatorImpl` + `NoOpNetworkMonitor` into harness. Trigger a `requestSync(scope)` call.

**Acceptance:**
- Outbox drained AND pull executed in that order.
- Event flow emits `SyncStarted → ... → SyncCompleted`.
- Idempotent: a second `requestSync` while first is in-flight collapses (no double-drain).

**Pre-req:** review `md/SyncCoordinator.md` (incl. its «Design Q&A» appendix) to confirm the contract before writing assertions.

---

## App-version gate

### 15. `AppVersionGate` flips between Supported ↔ Unsupported mid-stream

**Why:** [`OutboxEnqueuer.isEnabled()`](../data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/OutboxEnqueuer.kt) consults the gate. Current harness uses an always-Supported stub. Production behaviour: an in-flight session is told it's now unsupported (via Remote Config). Subsequent enqueues drop silently; in-flight upload finishes; pulls keep working (read-only).

**Setup:** custom `AppVersionGate` that flips `isSyncAllowed()` after the first enqueue. Enqueue mutation A (allowed). Flip gate. Enqueue mutation B (dropped). Drain.

**Acceptance:**
- Outbox has only A.
- Drain pushes A, queue empty.
- A subsequent `enqueueUpsert` is a no-op (still gated).
- Pull still works regardless of the gate (read-side isn't gated).

---

## Outbox edge cases

### 16. Retry → eventual success

**Why:** [`OutboxIdempotentRepushIT`](../integration-test/src/androidDeviceTest/kotlin/com/georgeci/moneysurfer/integration/android/OutboxIdempotentRepushIT.kt) covers retry on a terminal-state invite. Doesn't cover "first drain failed because of a fixable cause, second drain succeeds." E.g. user added as member after first failed push.

**Setup:** peer enqueues an account update for a workspace they're NOT yet a member of. Drain → fail (PERMISSION_DENIED), `attempts = 1`. Owner adds peer to `members/{peerUid}`. Peer drains again.

**Acceptance:**
- Second drain succeeds.
- Mutation removed from queue.
- Firestore has the doc.
- `attempts` and `lastError` from the prior failure don't leak into a new mutation enqueued later (verify via a second enqueue → drain → no stale attempts).

---

### 17. Mixed-collection batch

**Why:** drain pushes mutations in `createdAt` order regardless of collection. A batch like `[account-update, transaction-insert, account-update]` exercises the per-doc `pushOne` dispatch + rule pass on alternating sub-collections. Currently single-collection batches dominate the suite.

**Setup:** enqueue interleaved mutations across accounts + transactions + categories.

**Acceptance:** all push, queue empty, server has each doc, ordering preserved.

---

## CI / hermetic runs

### 18. Verify `qaIntegrationDeviceHermetic` actually runs

**Why:** [`qaIntegrationDeviceHermetic`](../gradle/qa.gradle.kts) is documented but unused in this iteration's runs. Boots Gradle-managed AVD + firebase emulator. Worth running once to confirm system image download path + Allure XML deduplication logic still work after recent changes.

**Setup:** clean state, no AVD running, run `./gradlew qaIntegrationDeviceHermetic`.

**Acceptance:**
- First run downloads `aosp-atd` system image (≈200 MB).
- All current ITs pass on the managed device.
- Allure report at `build/reports/allure/android-device/index.html` shows all classes with no duplicate runs from leftover XMLs.

---

## Pre-requisite tracking

| Item | Blocker |
|---|---|
| #7 cascade migration test | Schema migration decision (CASCADE vs reorder pull) |
| #8 workspace tombstone | Design decision in `md/sync.md` on workspace doc lifecycle |
| #9, #10 invite use cases | Wire `Accept`/`SendInviteUseCase` into harness |
| #14 coordinator | Review `SyncCoordinator.md` for contract |
| #15 version-gate flip | Custom test-only gate impl |

Items #1–#6, #11, #12, #13, #16, #17, #18 are unblocked — pick any.
