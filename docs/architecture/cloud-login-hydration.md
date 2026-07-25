# Cloud Login on a New Device — Hydration Audit

<!-- DOCS:TOC -->
## Contents
- [Cloud Login on a New Device — Hydration Audit](#cloud-login-on-a-new-device--hydration-audit)
- [TL;DR for agents](#tldr-for-agents)
- [Verdict](#verdict)
- [What was checked](#what-was-checked)
- [F1 — Asymmetric flag gating writes dangling workspace refs (critical)](#f1--asymmetric-flag-gating-writes-dangling-workspace-refs-critical)
- [F2 — Bootstrap pins a workspace that does not exist locally](#f2--bootstrap-pins-a-workspace-that-does-not-exist-locally)
- [F3 — The pull fetches one batch per collection, with no pagination loop](#f3--the-pull-fetches-one-batch-per-collection-with-no-pagination-loop)
- [F4 — One unreadable workspace aborts the whole bootstrap, blocking sign-in](#f4--one-unreadable-workspace-aborts-the-whole-bootstrap-blocking-sign-in)
- [Minor](#minor)
- [What holds up](#what-holds-up)
- [Remediation](#remediation)
- [Skeptic review](#skeptic-review)
- [Verification checklist](#verification-checklist)
<!-- DOCS:END -->

## TL;DR for agents

- Question audited: *does signing in to an existing cloud account hydrate everything
  the app needs before the user is shown the workspace list / create-workspace screen?*
- Answer: **no**, and the primary defect is not the missing pull — it is that
  `SyncFeatureFlag(enabled = false)` gates `WorkspaceSyncer` but **not** the direct
  `UserRemoteRepository` writes, so the shipped build writes `users/{uid}.workspaceIds`
  entries that point at Firestore documents which were never created.
- The damage accumulated in production while the sync feature was dark, and would have
  surfaced the day the flag was flipped.
- **R1–R5 have since landed** (issue #342), including the flag flip: sync is live in the
  online build. The findings below are kept as the record of what the code used to do and
  why the guards exist. Read [Remediation](#remediation) for the status of each item.

READ WHEN:
- changing `SyncFeatureFlag` or anything it gates
- flipping the online build to `enabled = true`
- touching `PostAuthBootstrapUseCase`, `CreateWorkspaceUseCase`, or the workspace selector
- investigating "I signed in on my other phone and my data is gone"

Audit date: 2026-07-25. Baseline: `main` @ `93880d1b0`.
Fixed: 2026-07-25, issue #342 — R1–R5 landed; `SyncFeatureFlag(enabled = true)` in the
online build.

<!-- AI:SECTION id=cloud-login-verdict task=sync,auth,login,workspace,known-issues -->
## Verdict

The navigation ordering is correct: `LoginUseCase` awaits `PostAuthBootstrapUseCase`,
which awaits `workspaceSyncer.syncAll()` fail-loud, and only then does `SignInViewModel`
post `NavigateToWorkspaceSelector`. No screen renders ahead of the pull.

The data never arrives, because in the online build the pull is a no-op, and the remote
user document has meanwhile been populated with references that no pull could satisfy.
<!-- AI:END -->

## What was checked

| Layer | File |
| --- | --- |
| Auth entry | [`LoginUseCase`](../../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/usecase/LoginUseCase.kt) |
| Hydration | [`PostAuthBootstrapUseCase`](../../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/usecase/PostAuthBootstrapUseCase.kt) |
| Sync entry | [`SyncCoordinatorWorkspaceSyncer`](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/SyncCoordinatorWorkspaceSyncer.kt) |
| Pull | [`PullRemoteChangesUseCaseImpl`](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/PullRemoteChangesUseCaseImpl.kt) |
| Local read model | [`WorkspaceDao.getByUserId`](../../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/db/dao/WorkspaceDao.kt) |
| UI | [`WorkspaceSelectorViewModel`](../../feature/workspace/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/workspace/selector/WorkspaceSelectorViewModel.kt), [`WorkspaceSelectorScreen`](../../feature/workspace/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/workspace/selector/WorkspaceSelectorScreen.kt) |
| Start route | [`AppLaunchViewModel.resolveStartRoute`](../../navigation/src/commonMain/kotlin/com/georgeci/moneysurfer/navigation/AppLaunchViewModel.kt) |
| Rules | [`firestore.rules`](../../firestore.rules) — `isMember()` at L58, `workspaces/{wid}` at L332 |

<!-- AI:SECTION id=cloud-login-f1-dangling-refs task=sync,firestore,feature-flag,data-integrity -->
## F1 — Asymmetric flag gating writes dangling workspace refs (critical)

`SyncCoordinatorWorkspaceSyncer.pushAll()` returns **successfully** when the flag is off:

```kotlin
override suspend fun pushAll() {
    if (!syncFeatureFlag.enabled) return
    ...
}
```

In `CreateWorkspaceUseCase` that no-op is indistinguishable from a landed push, so
`bind()` passes and execution continues into the two `UserRemoteRepository` calls, which
are **not** behind the flag:

- `userRemoteRepository.addWorkspaceRef(firebaseUid, newId)`
- `userRemoteRepository.setDefaultWorkspace(firebaseUid, newId)`

Resulting Firestore state for every workspace created by the shipped online build:

```
users/{uid}.workspaceIds      = [W]      ← written
users/{uid}.defaultWorkspaceId = W       ← written
workspaces/W                             ← MISSING
workspaces/W/members/{uid}               ← MISSING
```

The use case's own comment anticipates exactly this hazard — *"Skipping `addWorkspaceRef`
… keeps the global state consistent: `users/{uid}.workspaceIds` won't reference a
workspace whose `members/{uid}` row never landed (would otherwise lock subsequent pulls
with PERMISSION_DENIED via firestore.rules `isMember`)"* — but that protection only fires
on a **thrown** push failure, not on a silently skipped one.

Consequence chain for the audited scenario (device B, fresh install, same account):

1. `PostAuthBootstrapUseCase` reads `users/{uid}` directly (not flag-gated) → `ExistingUser(workspaceIds = [W], default = W)`.
2. `syncAll()` no-ops → Room stays empty.
3. `session.currentWorkspaceId` is seeded to `W` anyway.
4. Selector joins `workspaces ⨝ workspace_members WHERE userId = :uid` → empty list; the
   Continue button is disabled because `activeWorkspace` resolves against that empty list;
   the only live control is "Create workspace".
5. User creates a duplicate → `addWorkspaceRef` appends → `workspaceIds = [W, W2]`, both phantom.

**Fixed (R1):** the remote block is entered only when `firebaseUid != null && syncFeatureFlag.enabled`.
Refs already written by shipped builds stay on Firestore — F4's tolerance is what keeps them
from blocking sign-in.
<!-- AI:END -->

<!-- AI:SECTION id=cloud-login-f2-pointer-seed task=auth,login,workspace,session -->
## F2 — Bootstrap pins a workspace that does not exist locally

`PostAuthBootstrapUseCase` seeds `session.currentWorkspaceId` from
`defaultWorkspaceId ?: workspaceIds.firstOrNull()` without checking that the workspace was
actually hydrated into Room.

On the next cold start `resolveStartRoute` sees `userId != null && workspaceId != null` and
routes straight to `Route.Dashboard`, on top of a local database that has no rows for that
workspace. The selector is never shown, so the user has no way back to a valid workspace.

Independently: nothing guarantees `defaultWorkspaceId` is a member of `workspaceIds`. A
server-side skew pins a workspace the pull never visits, even with sync fully enabled.

**Fixed (R2):** both candidates are filtered through `WorkspaceRepository.getById` before
anything is pinned; when nothing hydrated the use case returns `Result.CloudDataUnavailable`
and the selector says so instead of showing an empty list.
<!-- AI:END -->

<!-- AI:SECTION id=cloud-login-f3-pull-pagination task=sync,pull,pagination -->
## F3 — The pull fetches one batch per collection, with no pagination loop

`pullBatch` reads the cursor, issues **one** query capped at `BATCH_SIZE = 100`, advances
the cursor to `max(updatedAt)` and returns. There is no loop that repeats until the
collection is drained, and `SyncCoordinatorImpl.runSyncRequest` calls the pull exactly once.

So even with the flag on, a fresh sign-in hydrates at most 100 documents per collection per
workspace before the user reaches the dashboard. The remainder trickles in through the
1-minute in-process ticker (`BACKGROUND` → `ChangedSinceLastSync`), another 100 per
collection per minute.

Severity is **medium, not critical**: cursors are per-collection and monotonic, so the pull
does converge and no data is lost. What breaks is the first impression — an account with a
few thousand transactions shows wrong balances for tens of minutes of foreground time.

Related: [`md/test_debt.md`](../../md/test_debt.md) §5 already flags that cursor paging is
untested. That entry is about missing tests; this finding is that the loop is absent.

**Fixed (R3):** `pullBatches` repeats until a batch comes back short of `BATCH_SIZE`, capped at
`MAX_BATCHES_PER_COLLECTION = 50` (5 000 docs per collection per workspace per sync). The cursor
is persisted every round, so hitting the ceiling just defers the tail to the next sync.
<!-- AI:END -->

<!-- AI:SECTION id=cloud-login-f4-bootstrap-abort task=sync,pull,auth,error-handling -->
## F4 — One unreadable workspace aborts the whole bootstrap, blocking sign-in

`PullRemoteChangesUseCaseImpl` raises on the first plugin failure for any workspace in
scope, and `PostAuthBootstrapUseCase` binds that into an `AuthError`. There is no
per-workspace tolerance.

Combined with F1 this is a latent kill switch: the moment `SyncFeatureFlag` is flipped to
`true`, every account carrying a phantom `workspaceIds` entry hits
`fetchWorkspaceDoc(W)` → `allow get: if isMember(wid)` → denied → sync error → **sign-in
becomes impossible**, with `SignInError.PermissionDenied` shown on the form.

**Verified** (2026-07-25) in
[`firestore-tests/test/danglingWorkspaceRefs.spec.js`](../../firestore-tests/test/danglingWorkspaceRefs.spec.js).
The rules layer answers the question the audit left open:

| Read against a phantom `wid` | Result |
| --- | --- |
| `get workspaces/{wid}` | **`permission-denied`** — not an empty snapshot |
| `get workspaces/{wid}` when the member row survives but the root doc is gone | succeeds, `exists() == false` |
| any subcollection query (`accounts`, …) | `permission-denied` |
| `invites` filtered by `targetUserId == uid` | **succeeds**, returns empty |
| `invites` unfiltered | `permission-denied` |

Two consequences. First, "workspace not found" and "workspace not yours" are genuinely
different outcomes — the denial comes from `isMember`, not from the document's absence — so
skipping on a denial does not also swallow an empty workspace. Second, `invites` is the one
collection that does *not* fail on a phantom ref, because `allow read: if
resource.data.targetUserId == request.auth.uid` admits the filtered query without a member
row. That is why phase 2 uses `fetchInvitesForUser` and why it never trips over this.

**Fixed (R4):** remote reads are wrapped so a raise is distinguishable from a local apply
failure; a workspace that raises is logged and skipped, and the pull carries on with the rest.
Both SDK shapes are covered because the tolerance is on *any* raising read, not on the root
doc specifically — so the open [`firestore-tests/`](../../firestore-tests) question no longer
gates anything.
<!-- AI:END -->

## Minor

- ~~The `PostAuthBootstrapUseCase` KDoc claims the seeded default lets "a fresh device skip
  the selector and land on Dashboard".~~ Rewritten alongside R2; `SignInViewModel` still
  posts `NavigateToWorkspaceSelector` unconditionally.

## What holds up

Worth stating explicitly so a fix does not churn code that is already correct:

- Ordering — the selector cannot render before `syncAll()` completes; the pull is awaited
  inside the login use case and is fail-loud.
- Pull order is FK-safe: workspace root (`-100`) → members (`0`) → invites (`10`) →
  accounts (`20`) → categories (`30`) → transactions (`40`) → …, with the owner's
  `UserEntity` stubbed via `insertIgnore` in both `WorkspaceSyncPlugin` and
  `WorkspaceMemberSyncPlugin`.
- `SyncScope.AllUserData` discovers workspaces from `users/{uid}.workspaceIds` on the
  server, so workspaces unknown to the device are found.
- Identity lines up end to end: local `UserId` == Firebase uid == the selector's join key.
- Session pointers are DataStore-backed and `set` suspends until persisted, so there is no
  read-your-own-write race between `currentFirebaseUid.set(uid)` and the provider that
  reads it.
- Firestore rules permit everything a legitimate member needs to pull.

<!-- AI:SECTION id=cloud-login-remediation task=sync,auth,roadmap,known-issues -->
## Remediation

| # | Change | Status |
| --- | --- | --- |
| R1 | `CreateWorkspaceUseCase` enters the remote block only when `firebaseUid != null && syncFeatureFlag.enabled` — same treatment the demo session already gets | **Done** |
| R2 | `PostAuthBootstrapUseCase` verifies against `WorkspaceRepository` that the resolved workspace reached Room before pinning it, and returns `Result.CloudDataUnavailable` when the remote lists workspaces and none hydrated | **Done** |
| R3 | `pullBatches` loops while `docs.size == BATCH_SIZE`, checking the cancel token each round, capped at `MAX_BATCHES_PER_COLLECTION` | **Done** |
| R4 | A workspace whose *remote reads* raise is logged and skipped as a stale ref instead of aborting the pull; plugin (local write) failures still abort | **Done** |
| R5 | `SyncFeatureFlag(enabled = true)` in the online build | **Done** — the owner's call, taken once R1–R4 landed |

Four things fell out of the fix that the audit did not call for:

- **Sign-in is now atomic.** The pointers pinned before the bootstrap
  (`currentUserId`, `currentFirebaseUid`) cannot simply be deferred — the pull reads
  `currentFirebaseUid` to discover the user's workspaces — so `AbandonAuthSessionUseCase`
  rolls them back and signs out when the bootstrap returns `Left`. Without it a failed
  bootstrap left a signed-in session with no workspace, and `resolveStartRoute` sent the
  next cold start past sign-in into a selector with nothing in it and no retry path.
- **`arrayRemove` for stale refs was not implemented.** Skipping is enough to unblock
  sign-in, and pruning the remote list is a destructive write on data the client may be
  misreading (an unreadable workspace is not provably a dead one). Left deliberate.
- **The tolerance in R4 is scoped to denials only.** A first cut swallowed every remote
  read failure, so a dropped connection produced `PullSummary(0, 0)` and the coordinator
  recorded a successful sync — the UI would have claimed the user was up to date while
  nothing arrived. `readRemote` now classifies: PERMISSION_DENIED skips the workspace,
  everything else still aborts the pull.
- **R1 needed a mirror on the push side.** Gating the remote block also removed the only
  call that registers `users/{uid}.workspaceIds` for a workspace created while sync was
  dark, and nothing else ever adds one — the workspace would have been pushed by the
  outbox after the flip and stayed invisible to every other device. `WorkspaceRefRegistrar`
  now registers the ref from `WorkspaceSyncPlugin.push`, where it belongs.

Two smaller consequences, both user-facing:

- **Anonymous sign-in must not be signed out on rollback.** `signInAnonymously()` reuses
  `auth.currentUser` and mints a new uid when there is none, so signing out over a transient
  bootstrap failure would orphan that account's cloud data permanently. The rollback clears
  the local pointers and leaves the provider session alone.
- **The selector needed an exit.** Signed in with no workspace has no back entry and no
  route to Settings, so `Result.CloudDataUnavailable` would have been the same dead end
  §2 set out to remove. The selector now carries a sign-out action whenever it is the root
  of the stack.

R2 does not violate layering: `WorkspaceRepository` is a domain interface, alongside the
`UserRemoteRepository` and `WorkspaceSyncer` the use case already depends on.
<!-- AI:END -->

## Skeptic review

Each finding was argued against before being kept. What the counter-arguments changed:

| Challenge | Outcome |
| --- | --- |
| "F1 is harmless — `addWorkspaceRef` is best-effort anyway" | **Rejected.** The harm is the *success*, not the failure: the write lands and references a document that does not exist. |
| "F3 is not a bug — the ticker catches up" | **Partially accepted.** Downgraded from critical to medium: convergence is real, data loss is not. The defect is a misleading first screen, not corruption. |
| "F4 overstates it — sign-in would not actually block" | **Kept**, and since confirmed: the emulator test shows a denied `get()`, so the abort path is the one that ran. |
| "None of this matters, sync is not shipped" | **Rejected, and it inverts the priority.** Because the feature is dark, the corrupting writes in F1 run unobserved in production and only surface at flip time. This is what makes R1 urgent rather than deferrable. |
| "R2 puts a Room query in the domain layer" | **Rejected** — `WorkspaceRepository` is a domain interface. |

## Verification checklist

- [x] `CreateWorkspaceUseCase` performs **no** remote writes with the flag off (R1) —
      `CreateWorkspaceUseCaseTest`.
- [x] Bootstrap leaves `currentWorkspaceId` null when the pull hydrated nothing (R2) —
      `PostAuthBootstrapUseCaseTest`.
- [x] A failed bootstrap leaves no session pointers set — `AuthSessionRollbackTest`.
- [x] Multi-batch pull: 250 docs in one collection drained by a single sync (R3) —
      `PullRemoteChangesUseCaseImplSpec`.
- [x] One unreadable workspace out of N does not fail the pull (R4), in both SDK shapes —
      denied root `get()` and denied subcollection query —
      `PullRemoteChangesUseCaseImplSpec`.
- [x] A network failure aborts the pull instead of passing as a stale ref —
      `PullRemoteChangesUseCaseImplSpec`.
- [x] An anonymous rollback keeps the provider session — `AuthSessionRollbackTest`.
- [x] A pushed workspace registers its own ref — `WorkspaceRefRegistrarSpec`.
- [x] Emulator test for a `users/{uid}.workspaceIds` entry with no matching
      `workspaces/{wid}` — `firestore-tests/test/danglingWorkspaceRefs.spec.js`, 7 cases.
      The denial is real, and the phantom ref reads back from `users/{uid}` as if nothing
      were wrong, which is why the bug stayed invisible.
