# Firestore Rules — Bug List and Schema Gaps

<!-- DOCS:TOC -->
## Contents
- [Firestore Rules — Bug List and Schema Gaps](#firestore-rules-bug-list-and-schema-gaps)
- [TL;DR for agents](#tldr-for-agents)
- [#3 — addWorkspaceRef after failed syncWorkspace (root cause)](#3-addworkspaceref-after-failed-syncworkspace-root-cause)
  - [Symptom](#symptom)
  - [Origin](#origin)
  - [Fix](#fix)
  - [Recovery for already-broken data](#recovery-for-already-broken-data)
- [#1 — clientVersionCode >= 1 is a no-op gate](#1-clientversioncode-1-is-a-no-op-gate)
  - [Fix options](#fix-options)
  - [Recommendation](#recommendation)
- [#6 — Subcollection writes do not validate workspaceId payload](#6-subcollection-writes-do-not-validate-workspaceid-payload)
  - [Fix](#fix)
- [#4 — users/{uid} has no payload validation](#4-usersuid-has-no-payload-validation)
  - [Fix](#fix)
- [#2 — members/{uid} create requires hasValidClientVersion()](#2-membersuid-create-requires-hasvalidclientversion)
  - [Check](#check)
- [#7 — Push order race on workspace creation](#7-push-order-race-on-workspace-creation)
  - [Check](#check)
- [#5 — workspaces collection-level list](#5-workspaces-collection-level-list)
  - [Check](#check)
- [Summary](#summary)
<!-- DOCS:END -->

> Tracked rule issues. Bumped versions: see line 1 of `firestore.rules`.


## TL;DR for agents

- Journal of issues found while reviewing `firestore.rules`. One HIGH-severity bug (#3) is fixed; the rest are deferred low/medium hazards.
- Format per entry: **Problem → Severity → Fix → Status**.
- Rules themselves are correct on the happy path; the gaps are missing defensive coverage for edge cases.
- App-side bug #3 used to exploit that missing defence — now patched in `CreateWorkspaceUseCase`.

READ WHEN:
- editing `firestore.rules`
- debugging a `PERMISSION_DENIED` from the client
- planning a server-side `clientVersionCode` floor bump
- reviewing the workspace-creation push order
- adding a new top-level Firestore collection

Related: [app-version-gate](app-version-gate.md), [persistence](persistence.md).

<!-- AI:SECTION id=rules-bug-3 task=firestore-rules,bootstrap,permission-denied -->
## #3 — `addWorkspaceRef` after failed `syncWorkspace` (root cause)

**Severity:** HIGH — the exact `PERMISSION_DENIED` seen in production. **Status:** ✅ Fixed.

### Symptom

User signs in, the per-workspace pull in `PostAuthBootstrapUseCase` fails with:

```
com.google.firebase.firestore.FirebaseFirestoreException: PERMISSION_DENIED:
  Missing or insufficient permissions.
```

Nothing can be pulled, the user is permanently degraded on this workspace.

### Origin

Pre-fix `CreateWorkspaceUseCase.kt` lines 69–82:

```kotlin
Either.catch { workspaceSyncRepository.syncWorkspace(newId).awaitReport() }
    .onLeft { log.w(it) { "[remote] syncWorkspace failed wid=${newId.value}" } }
    .onRight { ... }
Either.catch { userRemoteRepository.addWorkspaceRef(firebaseUid, newId) }   // ← still ran
    .onLeft { ... }
Either.catch { userRemoteRepository.setDefaultWorkspace(firebaseUid, newId) }
    .onLeft { ... }
```

If `syncWorkspace` failed (network, partial push, rules), `users/{uid}.workspaceIds` still got the ref. Next login → `syncWorkspace(wid)` tries to read `/workspaces/{wid}/...` → rules `isMember(wid)` checks `exists(/workspaces/{wid}/members/{uid})` → no membership row → permanent `PERMISSION_DENIED`.

### Fix

`addWorkspaceRef` + `setDefaultWorkspace` are gated on `Right` from `syncWorkspace`:

```kotlin
val syncResult = Either.catch { workspaceSyncRepository.syncWorkspace(newId).awaitReport() }
    .onLeft { ... }.onRight { ... }
if (syncResult.isRight()) {
    Either.catch { userRemoteRepository.addWorkspaceRef(firebaseUid, newId) }...
    Either.catch { userRemoteRepository.setDefaultWorkspace(firebaseUid, newId) }...
} else {
    log.w { "skipping ... — workspace push failed; would corrupt users/$uid.workspaceIds" }
}
```

Test — `skips addWorkspaceRef + setDefault when syncWorkspace throws...` in `CreateWorkspaceUseCaseTest.kt`.

### Recovery for already-broken data

The fix does not heal already-corrupted `users/{uid}` docs. Manual options:

1. Console-delete the broken `wid` from `users/{uid}.workspaceIds`.
2. Create the missing `/workspaces/{wid}/members/{uid}` (if you are owner).
3. Full reset — delete `users/{uid}` entirely and re-sign-in.

Optional self-healing in `PostAuthBootstrap` — on `PERMISSION_DENIED` for a specific wid, silently `arrayRemove` it from `users/{uid}.workspaceIds`. Not implemented; awaits explicit request.
<!-- AI:END -->

<!-- AI:SECTION id=rules-bug-1 task=firestore-rules,app-version,client-version -->
## #1 — `clientVersionCode >= 1` is a no-op gate

**Severity:** MEDIUM — rules-level force-update is bypassed by every shipped build. **Status:** ⏳ Deferred.

```firestore
function hasValidClientVersion() {
  return request.resource.data.clientVersionCode is int
    && request.resource.data.clientVersionCode >= 1;
}
```

The comment says "Bump in lockstep with `appConfig/mobile.minSupportedAppVersionCode`", but nobody bumps it. Until then the client-side force-update (see [app-version-gate](app-version-gate.md)) can be bypassed — old builds write freely.

### Fix options

**A — static bump.** Bump the constant by hand together with `appConfig/mobile.minSupportedAppVersionCode`. Cheap; risky (forgetting to keep them in sync).

**B — dynamic.** Read in the rule:

```firestore
function hasValidClientVersion() {
  let minCode = get(/databases/$(database)/documents/appConfig/mobile)
    .data.minSupportedAppVersionCode;
  return request.resource.data.clientVersionCode is int
    && request.resource.data.clientVersionCode >= minCode;
}
```

Costs +1 read per write — expensive (Firestore billing). Also depends on `appConfig/mobile`; if deleted, all writes deny.

### Recommendation

Not critical now (force-update is client-side). When server-side enforcement is wanted — option A with a CI check enforcing synchrony.
<!-- AI:END -->

<!-- AI:SECTION id=rules-bug-6 task=firestore-rules,schema,subcollections -->
## #6 — Subcollection writes do not validate `workspaceId` payload

**Severity:** MEDIUM — schema integrity, not security. **Status:** ⏳ Deferred.

```firestore
match /transactions/{tid} {
  allow create, update: if isMember(wid) && hasValidClientVersion();
}
```

A user who is a member of workspace `A` can POST `/workspaces/A/transactions/{tid}` with payload `{ workspaceId: "B", ... }`. A pull on the device merges the transaction into the wrong workspace locally → confusion.

### Fix

```firestore
match /transactions/{tid} {
  allow create, update: if isMember(wid)
    && hasValidClientVersion()
    && request.resource.data.workspaceId == wid;
}
```

Apply to all subcollections: `accounts`, `categories`, `transactions`, `budgets`, `recurringRules`.
<!-- AI:END -->

<!-- AI:SECTION id=rules-bug-4 task=firestore-rules,users,schema -->
## #4 — `users/{uid}` has no payload validation

**Severity:** LOW — schema integrity; reads are protected anyway. **Status:** ⏳ Deferred.

```firestore
match /users/{uid} {
  allow read: if signedIn() && request.auth.uid == uid;
  allow create, update: if signedIn() && request.auth.uid == uid;
}
```

A user can write any field into their own `users/{uid}` — including a spoofed `workspaceIds = [<someone_else_wid>]`. `PostAuthBootstrap` then tries to read that workspace and fails with `PERMISSION_DENIED` (rules block reads). But the garbage stays in `users/{uid}`.

### Fix

```firestore
match /users/{uid} {
  allow read: if signedIn() && request.auth.uid == uid;
  allow create, update: if signedIn() && request.auth.uid == uid
    && request.resource.data.workspaceIds is list
    && (request.resource.data.defaultWorkspaceId == null
        || request.resource.data.defaultWorkspaceId in request.resource.data.workspaceIds);
}
```
<!-- AI:END -->

<!-- AI:SECTION id=rules-bug-2 task=firestore-rules,members,invites -->
## #2 — `members/{uid}` create requires `hasValidClientVersion()`

**Severity:** LOW — could break the invitation flow if the DTO is incomplete. **Status:** ⏳ Unconfirmed.

```firestore
match /workspaces/{wid}/members/{uid} {
  allow create, update:
    if (isOwner(wid) || request.auth.uid == uid)
    && hasValidClientVersion();
}
```

If `WorkspaceMemberDoc` does not carry `clientVersionCode`, every write into `members` is denied. The field is required on every write path.

### Check

Verify that `WorkspaceMemberDoc` (in `data/.../sync/SyncDtos.kt`) includes a `clientVersionCode: Int` field and that it serializes. Not a blocker until confirmed; if the invitation flow starts failing, look here.
<!-- AI:END -->

<!-- AI:SECTION id=rules-bug-7 task=firestore-rules,sync,workspace-creation -->
## #7 — Push order race on workspace creation

**Severity:** LOW — not proven, needs a repro. **Status:** ⏳ Unconfirmed.

Standard push order on workspace creation:

1. `POST /workspaces/{wid}` (workspace doc) — `ownerId == auth.uid` ✓
2. `POST /workspaces/{wid}/members/{auth.uid}` — `isOwner(wid) || auth.uid == uid` ✓
3. `POST /workspaces/{wid}/categories/{cid}` — `isMember(wid)` via `exists()`

Between 1 and 2 the owner exists but the member-row does not. If pushes are parallel (Firestore SDK may batch), step 3 could land before step 2 → deny.

### Check

Verify `WorkspaceSyncRepositoryImpl` pushes **sequentially** or atomically through a `WriteBatch`. If parallel — rewrite to sequential.
<!-- AI:END -->

<!-- AI:SECTION id=rules-bug-5 task=firestore-rules,workspaces,query -->
## #5 — `workspaces` collection-level `list`

**Severity:** LOW — nobody scans top-level today. **Status:** ⏳ Preventive.

```firestore
match /workspaces/{wid} {
  allow get, list: if isMember(wid);
}
```

`list` (a top-level query) degrades: the rules engine evaluates per-document; if any single doc in the result set has a non-member viewer, the whole query is denied. Not critical as long as the client does not run `firestore.collection("workspaces").get()`.

### Check

Grep the data layer — nobody should be doing top-level lists of `workspaces`. Pulls always go per-`workspaceId` via `users/{uid}.workspaceIds`. Recommend blacklisting top-level scans via a unit test or kover rule.
<!-- AI:END -->

## Summary

| # | Topic | Severity | Status |
|---|-------|----------|--------|
| 3 | `addWorkspaceRef` after failed `syncWorkspace` | HIGH | ✅ Fixed |
| 1 | `clientVersionCode >= 1` — fake gate | MEDIUM | ⏳ Deferred |
| 6 | No `workspaceId` payload validation in subcollections | MEDIUM | ⏳ Deferred |
| 4 | `users/{uid}` without payload validation | LOW | ⏳ Deferred |
| 2 | `members/{uid}` clientVersionCode requirement | LOW | ⏳ Unconfirmed |
| 7 | Push order race on workspace creation | LOW | ⏳ Unconfirmed |
| 5 | Top-level `workspaces` list without guard | LOW | ⏳ Preventive |

Overall: rules themselves are **correct** on the happy path, but lack **defensive coverage** for edge cases. App-side bug #3 exploited that lack — now fixed.
