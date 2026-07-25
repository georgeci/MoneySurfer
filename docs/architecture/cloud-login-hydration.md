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
- The damage accumulates in production **today**, while the sync feature is dark, and
  surfaces the day the flag is flipped.
- Nothing in this document has been fixed. Read the [Remediation](#remediation) order
  before touching any of it — R1 and R4 are coupled.

READ WHEN:
- changing `SyncFeatureFlag` or anything it gates
- flipping the online build to `enabled = true`
- touching `PostAuthBootstrapUseCase`, `CreateWorkspaceUseCase`, or the workspace selector
- investigating "I signed in on my other phone and my data is gone"

Audit date: 2026-07-25. Baseline: `main` @ `93880d1b0`. No code was changed.

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

Not yet verified empirically: whether the GitLive wrapper throws on a denied `get()` or
returns `exists = false`. If it returns `exists = false`, the root-doc plugin skips and the
failure instead surfaces on the first subcollection query. Either way the pull aborts;
only the error shape differs. Prove it in [`firestore-tests/`](../../firestore-tests) before
relying on either branch.
<!-- AI:END -->

## Minor

- The `PostAuthBootstrapUseCase` KDoc claims the seeded default lets "a fresh device skip
  the selector and land on Dashboard". `SignInViewModel` posts
  `NavigateToWorkspaceSelector` unconditionally — the doc describes behaviour that does not
  exist.

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

Ordered; R1 and R4 must land together in any change that also flips the flag.

| # | Change | Rationale | Size |
| --- | --- | --- | --- |
| R1 | In `CreateWorkspaceUseCase`, enter the remote block only when `firebaseUid != null && syncFeatureFlag.enabled` — same treatment the demo session already gets | Stops writing dangling refs into production Firestore | S |
| R2 | In `PostAuthBootstrapUseCase`, after `syncAll()` verify the resolved workspace exists in Room before pinning it; if the remote lists workspaces but none hydrated, return a distinct result the UI can render as "cloud data unavailable" | Kills both the silent drop into create-workspace and the Dashboard-over-empty-DB on next launch | S |
| R3 | Loop `pullBatch` while `docs.size == BATCH_SIZE`, checking the cancel token each round, with an iteration ceiling | Full hydration before the first dashboard | M |
| R4 | Treat a workspace whose root doc is unreadable as a stale ref: log, skip, optionally `arrayRemove` it; do not abort the bootstrap | Prevents flipping the flag from bricking sign-in for accounts damaged by F1 | M |
| R5 | `SyncFeatureFlag(enabled = true)` in the online build | The only change that actually enables the scenario; a release decision, not a bug fix. R1–R4 are its preconditions | — |

R2 does not violate layering: `WorkspaceRepository` is a domain interface, alongside the
`UserRemoteRepository` and `WorkspaceSyncer` the use case already depends on.
<!-- AI:END -->

## Skeptic review

Each finding was argued against before being kept. What the counter-arguments changed:

| Challenge | Outcome |
| --- | --- |
| "F1 is harmless — `addWorkspaceRef` is best-effort anyway" | **Rejected.** The harm is the *success*, not the failure: the write lands and references a document that does not exist. |
| "F3 is not a bug — the ticker catches up" | **Partially accepted.** Downgraded from critical to medium: convergence is real, data loss is not. The defect is a misleading first screen, not corruption. |
| "F4 overstates it — sign-in would not actually block" | **Kept**, with a caveat: the abort path is confirmed in code, but the exact SDK error shape on a denied `get()` is unverified. Flagged as a test to write, not an assumption to build on. |
| "None of this matters, sync is not shipped" | **Rejected, and it inverts the priority.** Because the feature is dark, the corrupting writes in F1 run unobserved in production and only surface at flip time. This is what makes R1 urgent rather than deferrable. |
| "R2 puts a Room query in the domain layer" | **Rejected** — `WorkspaceRepository` is a domain interface. |

## Verification checklist

Before closing any of R1–R4:

- [ ] Emulator test in `firestore-tests/` for a `users/{uid}.workspaceIds` entry with no
      matching `workspaces/{wid}` — assert the observed SDK error shape (F4).
- [ ] Test that `CreateWorkspaceUseCase` performs **no** remote writes with the flag off (R1).
- [ ] Test that bootstrap leaves `currentWorkspaceId` null when the pull hydrated nothing (R2).
- [ ] Multi-batch pull test: >100 docs in one collection drained by a single sync (R3).
- [ ] Bootstrap test: one unreadable workspace out of N does not fail the sign-in (R4).
