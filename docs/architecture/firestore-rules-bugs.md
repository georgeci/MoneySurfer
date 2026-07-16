# Firestore Rules — Bug List and Schema Gaps

<!-- DOCS:TOC -->
## Contents
- [Firestore Rules — Bug List and Schema Gaps](#firestore-rules-bug-list-and-schema-gaps)
- [TL;DR for agents](#tldr-for-agents)
- [#8 — Invite-less workspace join breaks tenant isolation](#8-invite-less-workspace-join-breaks-tenant-isolation)
  - [Problem](#problem)
  - [Fix](#fix)
  - [Why not a Cloud Function / deterministic invite id](#why-not-a-cloud-function-deterministic-invite-id)
- [#3 — addWorkspaceRef after failed syncWorkspace (root cause)](#3-addworkspaceref-after-failed-syncworkspace-root-cause)
  - [Symptom](#symptom)
  - [Origin](#origin)
  - [Fix](#fix)
  - [Recovery for already-broken data](#recovery-for-already-broken-data)
- [#1 — clientVersionCode >= 1 is a no-op gate](#1-clientversioncode-1-is-a-no-op-gate)
  - [Fix options](#fix-options)
  - [Recommendation](#recommendation)
- [#6 — Subcollection writes do not validate workspaceId payload](#6-subcollection-writes-do-not-validate-workspaceid-payload)
- [#156 — Poison-document crash loop; no write-shape validation](#156-poison-document-crash-loop-no-write-shape-validation)
  - [Problem](#problem)
  - [Fix — two layers](#fix-two-layers)
- [#4 — users/{uid} has no payload validation](#4-usersuid-has-no-payload-validation)
  - [Fix](#fix)
- [#2 — members/{uid} create requires hasValidClientVersion()](#2-membersuid-create-requires-hasvalidclientversion)
  - [Check](#check)
- [#7 — Push order race on workspace creation](#7-push-order-race-on-workspace-creation)
  - [Check](#check)
- [#5 — workspaces collection-level list](#5-workspaces-collection-level-list)
  - [Check](#check)
- [#161 — userEmails email→uid existence oracle](#161-useremails-emailuid-existence-oracle)
  - [Problem](#problem)
  - [Decision](#decision)
  - [Why not a server callable](#why-not-a-server-callable)
- [Summary](#summary)
<!-- DOCS:END -->

> Tracked rule issues. Bumped versions: see line 1 of `firestore.rules`.


## TL;DR for agents

- Journal of issues found while reviewing `firestore.rules`. Two HIGH-severity bugs (#8 invite-less join, #3 addWorkspaceRef) and MEDIUM #156 (poison-document crash loop / write-shape validation) are fixed; the rest are deferred low/medium hazards. #6 turned out obsolete (subsumed by #156).
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

<!-- AI:SECTION id=rules-bug-8 task=firestore-rules,members,invites,tenant-isolation -->
## #8 — Invite-less workspace join breaks tenant isolation

**Severity:** HIGH — breaks per-workspace tenant isolation. **Status:** ✅ Fixed (issue #152, rules v2.1.0).

### Problem

The old `members/{uid}` create rule was:

```firestore
allow create:
  if (isOwner(wid) || request.auth.uid == uid) && hasValidClientVersion();
```

The self-create branch (`request.auth.uid == uid`) let **any authenticated user** create their own member row in an arbitrary workspace with **no check that an invite targeting them exists**. Once the row existed, `isMember(wid)` returned true everywhere, so the intruder could read *and* write every `accounts`, `transactions`, `budgets`, `categories`, and `recurringRules` document in another household. The only barrier was knowing the (random) `wid` — which leaks via invite flows, logs (see the PII-logging issue), screenshots, and support channels — hence HIGH rather than Critical.

It also defeated owner eviction: the self-`update` branch allowed `status in ["ACTIVE", "LEFT"]` regardless of the current status, so a user the owner had set to `REMOVED` could flip themselves back to `ACTIVE` and rejoin.

### Fix

Self-create is now gated on an **owner-issued invite that admits the caller**. Because only the owner can create an invite (see the invites block), a stranger has nothing to point at and cannot forge one.

```firestore
allow create:
  if hasValidClientVersion() && (
       (request.auth.uid == uid && hasJoinableInvite(wid))
    || isOwner(wid)
  );
```

`hasJoinableInvite(wid)` reads the invite id off the member doc (`request.resource.data.inviteId`), `exists()`-guards it, then `get()`s the invite and requires it to be `PENDING`/`ACCEPTED` **and** to address the caller by `targetUserId` or a case-insensitive email match. `PENDING`/`ACCEPTED` (not `PENDING` only) tolerates the invitee's own `PENDING → ACCEPTED` flip landing first and idempotent outbox retries; `CANCELLED`/`DECLINED` invites are excluded so a revoked invite grants nothing.

The eviction bypass is closed on two fronts. (1) `isMember(wid)` now requires the caller's member row to be `status == "ACTIVE"` rather than merely to exist — member rows are soft-deleted (`LEFT`/`REMOVED`, never hard-deleted), so an existence check kept granting a left or evicted user full read/write to every `accounts`/`transactions`/etc. subcollection. A missing `status` (pre-status legacy docs) defaults to `ACTIVE`. (2) `resource.data.status != "REMOVED"` on the self-`update` branch stops a `REMOVED` row from self-resurrecting to `ACTIVE`; rejoining requires a fresh invite.

Client side, `WorkspaceMemberSyncPlugin.push` stamps `inviteId` onto the pushed `WorkspaceMemberDoc` by looking up the admitting invite in local Room (`WorkspaceInviteDao.findJoinableInviteId`). No new persisted column — the id is derived at push time, so there is **no Room migration**. Owner-created rows carry a null `inviteId` and are admitted by the owner branch.

Tests: `firestore-tests/test/members.spec.js` — the old "self-create succeeds with no invite" test is now "non-invited stranger CANNOT self-create", plus positive (PENDING / ACCEPTED / email-only), negative (wrong target, cancelled, dangling id), and "REMOVED cannot self-resurrect" cases.

### Why not a Cloud Function / deterministic invite id

The repo has no Cloud Functions deployment, and invite doc ids are random UUIDs (one email may have several), so rules cannot compute the invite path from `wid`+`uid` alone. Carrying the invite id on the member doc lets the rule verify the unforgeable owner-issued binding without a query or a function hop. `users/{uid}.invitedWorkspaceIds` was rejected as a signal — it is writable by any signed-in user (the invite-sender seeding path), so it is forgeable by the attacker.
<!-- AI:END -->

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

**Severity:** MEDIUM — schema integrity, not security. **Status:** ❌ Obsolete — the premise was wrong; subsumed by #156 write-shape validation (rules v2.2.0).

The original write-up proposed:

```firestore
match /transactions/{tid} {
  allow create, update: if isMember(wid)
    && hasValidClientVersion()
    && request.resource.data.workspaceId == wid;
}
```

on the theory that a member of workspace `A` could POST `/workspaces/A/transactions/{tid}` with payload `{ workspaceId: "B", ... }` and mis-file it locally on pull.

**Why it's obsolete:** the wire DTOs carry **no `workspaceId` field**. Check `data-remote/.../RemoteDtos.kt` — `TransactionDoc`, `AccountDoc`, `CategoryDoc`, etc. have no such field. The workspace id is taken from the trusted document path and injected at decode time (`SyncDtoMappers.toEntity(id, workspaceId = scopeKey)` in `sync-surfer`, driven by `PullRemoteChangesUseCaseImpl`). There is nothing on the payload to spoof, and adding `request.resource.data.workspaceId == wid` would **deny every legitimate write** (the field is always absent). Do **not** add that check.

The real (and confirmed) subcollection-schema gap was untyped fields, tracked and fixed under **#156** below.
<!-- AI:END -->

<!-- AI:SECTION id=rules-bug-156 task=firestore-rules,schema,subcollections,poison-document,sync -->
## #156 — Poison-document crash loop; no write-shape validation

**Severity:** MEDIUM — persistent sync outage (poison-message crash loop). **Status:** ✅ Fixed (rules v2.2.0).

### Problem

Entity write rules validated only membership + `clientVersionCode`; field **types** were never checked. Any member could write a document with a wrong-typed field (e.g. `amount: "abc"` where `TransactionDoc` expects a `Long`). On the next incremental pull, `doc.decode(...)` threw inside the plugin's `applyDoc`; the exception aborted the whole pull **before** the cursor advanced past the offending doc. Every co-member's pull re-fetched that same doc, failed, and retried indefinitely → sync down for the whole workspace. (No RCE/path-traversal: `workspaceId` comes from the trusted path, `doc.id` is only a Room key.)

### Fix — two layers

1. **Rules reject the malformed write at the source.** `firestore.rules` gains `hasValidAccountShape` / `hasValidCategoryShape` / `hasValidTransactionShape`, wired into the `accounts` / `categories` / `transactions` create+update rules. Each field is type-checked **only when present** (`data.get(f, <default>) is <type>`), because the wire format omits fields left at their DTO default — a minimal doc must still pass; only a present-but-wrong-typed field is rejected. Nullable fields also accept an explicit null. Field lists mirror `RemoteDtos.kt`; `budgets`/`recurringRules` are left unguarded — they have no wire DTO and no plugin decodes them, so no poison-pull path exists yet.

2. **Client tolerates an undecodable doc.** `PluginHelpers.decodeOrNull` wraps `RemoteDocument.decode`; on any decode throw (except `CancellationException`) it logs and returns null, and the plugin returns `SKIPPED_APPLY_RESULT` (`applied = false`). A skipped doc still advances the pull cursor, so one bad row is skipped instead of wedging the batch. This heals docs already in the store and those written by older clients that the rules can't retroactively fix. All six decoding plugins (account, category, transaction, workspace, member, invite) use it.

Tests: `firestore-tests/test/entities.spec.js` (write-shape accept/reject, incl. the `amount: "abc"` scenario); `sync-surfer/.../plugin/DecodeOrNullSpec.kt` (skip on malformed, rethrow on cancellation). The existing "plugin failure surfaces as a sync error" pull spec still holds — a genuine apply/DB failure (not a decode) still aborts and retries.
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

<!-- AI:SECTION id=rules-bug-161 task=firestore-rules,userEmails,invites,privacy -->
## #161 — `userEmails` email→uid existence oracle

**Severity:** LOW — account-existence / email→uid oracle. **Status:** ✅ Accepted as intended for invites (issue #161). No rules change.

### Problem

`match /userEmails/{email}` allows `get: if signedIn()`. Any authenticated user can `.get()` `userEmails/victim@example.com` for an **exact** address and learn whether an account exists and its `uid`. `list` is denied, so this is single-key lookup only (no table scan / bulk harvest), but it is still an oracle for any guessed address. The harvested `uid` was, before rules bug #8 (issue #152), usable in the invite-less-join attack once a `wid` was known.

### Decision

**Accepted as intended.** The mapping *is* the invite-targeting mechanism: `SendInviteUseCase` resolves a recipient email to a `uid` via `userEmails/{email}` (`UserRemoteRepositoryImpl.findByEmail`) and refuses to send an invite when no account exists. An owner inviting a co-member by email must be able to perform exactly this single-key lookup, so a client-side `get` on the exact key is a functional requirement, not an incidental leak.

Residual risk is contained by the surrounding rules:

- `allow list: if false` — no enumeration; an attacker must already know the full address to probe it, so this reveals nothing they could not also test via a password-reset / sign-up flow.
- The write rule binds the email key to the writer's Firebase Auth email claim (`email == request.auth.token.email.lower()`), closing the email-squatting hole — a leaked `uid` is genuine, not attacker-planted.
- The one attack that made the harvested `uid` dangerous — invite-less self-join (rules bug #8 / issue #152) — is fixed: self-`create` on `members/{uid}` now requires an owner-issued invite that admits the caller (`hasJoinableInvite`), so a `uid` alone grants nothing.

Net LOW residual: single-address existence disclosure to authenticated users, no bulk harvest, no downstream escalation.

### Why not a server callable

The issue's alternative — move email→uid resolution behind a server callable returning only a boolean/opaque token — is **not viable without a rules-access-control change that is out of scope here**:

- A callable closes the oracle only if the direct `allow get` on `userEmails/{email}` is simultaneously **denied** in `firestore.rules`. Leaving the direct read in place keeps the oracle regardless of any callable, so the callable buys nothing on its own. Removing/denying the direct read is a rules access-control change (and a rules deployment) — off-limits for this task.
- The repo has **no Cloud Functions deployment** (`firebase.json` wires only `firestore.rules` + `firestore.indexes.json` + emulators; there is no `functions/` module). Standing one up is new infrastructure well beyond a LOW defence-in-depth item.
- The existing client reads the mapping directly (`data-remote/.../UserRemoteRepositoryImpl.kt` `findByEmail`); routing through a callable would be a client rewrite for no isolation gain while #152 already neutralizes the escalation path.

Revisit only if the app later adds a Cloud Functions backend for other reasons — at that point folding email→uid resolution behind a callable and denying the direct `get` becomes cheap and removes the last of the LOW residual.
<!-- AI:END -->

## Summary

| # | Topic | Severity | Status |
|---|-------|----------|--------|
| 8 | Invite-less workspace join breaks tenant isolation | HIGH | ✅ Fixed |
| 3 | `addWorkspaceRef` after failed `syncWorkspace` | HIGH | ✅ Fixed |
| 156 | Poison-document crash loop; no write-shape validation | MEDIUM | ✅ Fixed |
| 1 | `clientVersionCode >= 1` — fake gate | MEDIUM | ⏳ Deferred |
| 6 | No `workspaceId` payload validation in subcollections | MEDIUM | ❌ Obsolete (no such field; see #156) |
| 4 | `users/{uid}` without payload validation | LOW | ⏳ Deferred |
| 2 | `members/{uid}` clientVersionCode requirement | LOW | ⏳ Unconfirmed |
| 7 | Push order race on workspace creation | LOW | ⏳ Unconfirmed |
| 5 | Top-level `workspaces` list without guard | LOW | ⏳ Preventive |
| 161 | `userEmails` email→uid existence oracle | LOW | ✅ Accepted (intended for invites) |

Overall: rules themselves are **correct** on the happy path, but lack **defensive coverage** for edge cases. App-side bug #3 exploited that lack — now fixed.
