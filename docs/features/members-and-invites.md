# Members and Invites

<!-- DOCS:TOC -->
## Contents
- [Members and Invites](#members-and-invites)
- [TL;DR for agents](#tldr-for-agents)
- [Decisions (locked)](#decisions-locked)
- [Member model](#member-model)
- [Invite model](#invite-model)
- [Dual-write contract](#dual-write-contract)
- [In-app discovery flow](#in-app-discovery-flow)
- [Firestore rules](#firestore-rules)
  - [/members/{uid}](#membersuid)
  - [/workspaces/{wid}/invites/{inviteId} (per-workspace)](#workspaceswidinvitesinviteid-per-workspace)
  - [Wildcard read for invite discovery](#wildcard-read-for-invite-discovery)
  - [Test coverage](#test-coverage)
- [Forward-looking notes](#forward-looking-notes)
<!-- DOCS:END -->

## TL;DR for agents

- Roles are `OWNER / EDITOR / VIEWER`. Member rows are soft-delete only.
- Pending state lives on `WorkspaceInvite`, not on a `MEMBER.status = INVITED`.
- Invites are stored as Firestore docs at `workspaces/{wid}/invites/{id}`. There is no email delivery, no invite-link tokens, and no Cloud Function pipeline — invitees discover invites in-app.
- Both `WorkspaceMember` and `WorkspaceInvite` follow the standard dual-write pattern: Room write, then `OutboxEnqueuer` enqueue, then sync plugin push.
- Owner cannot leave (returns `OwnerCannotLeave`); ownership transfer is deferred post-v1.

READ WHEN:
- editing membership lifecycle (join / leave / remove / role change)
- adding or modifying invite use cases
- touching `firestore.rules` blocks for `/members/{uid}` or `/invites/{inviteId}`
- changing workspace bootstrap or pull-all-user-data flows
- wiring new members/invites screens (call existing repos, do not bypass dual-write)

Related: [persistence](../architecture/persistence.md), [sync](../architecture/sync.md), [firestore-rules-bugs](../architecture/firestore-rules-bugs.md).

<!-- AI:SECTION id=members-invites-decisions task=members,invites,roles,policy -->
## Decisions (locked)

| # | Question | Decision |
|---|---|---|
| 1 | Role naming | Keep `OWNER / EDITOR / VIEWER` (industry standard, code already uses, Firestore rules tied) |
| 2 | Member status set | `ACTIVE / LEFT / REMOVED` only — no `INVITED` (pending lives on `WorkspaceInvite`) |
| 3 | Invite-link tokens | Dropped. No `inviteToken/maxUsages/usedCount`, no `WorkspaceInviteLink` entity |
| 4 | Email delivery | Dropped. In-app only — invite written to Firestore, surfaced when invitee opens app |
| 5 | Owner transfer | Deferred. v1 keeps `OwnerCannotLeave`; `TransferOwnershipUseCase` is post-v1 |

Implication: scope = email invites stored as Firestore docs, accepted in-app, no backend functions, no link sharing, no ownership handoff.
<!-- AI:END -->

<!-- AI:SECTION id=members-model task=members,domain,kotlin -->
## Member model

`domain/.../model/WorkspaceMember.kt`:

```kotlin
data class WorkspaceMember(
    val userId: UserId,
    val workspaceId: WorkspaceId,
    val role: WorkspaceRole,
    val status: WorkspaceMemberStatus = WorkspaceMemberStatus.ACTIVE,
    val displayName: String = "",
    val email: String? = null,
    val addedByUserId: UserId? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val leftAt: Long? = null,
    val removedAt: Long? = null,
)
```

`domain/.../model/WorkspaceMemberStatus.kt`:

```kotlin
enum class WorkspaceMemberStatus {
    ACTIVE,    // current participant
    LEFT,      // voluntarily left via LeaveWorkspaceUseCase; leftAt set
    REMOVED,   // kicked by owner via RemoveMemberUseCase; removedAt set
}
```

`displayName` and `email` are intentional snapshots — populated when the row is written and never retro-updated. The membership roster keeps stable history even after the user later edits their profile or deletes their account.

Soft-delete only: rules forbid hard deletes, terminal state is encoded in `status` plus the matching `*At` timestamp.
<!-- AI:END -->

<!-- AI:SECTION id=invites-model task=invites,domain,kotlin -->
## Invite model

`domain/.../model/WorkspaceInvite.kt`:

```kotlin
data class WorkspaceInvite(
    val id: WorkspaceInviteId,
    val workspaceId: WorkspaceId,
    val email: String,
    val targetUserId: UserId?,        // resolved at send-time, null if not yet a user
    val role: WorkspaceRole,
    val status: InviteStatus,
    val invitedByUserId: UserId,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long,              // createdAt + 14d
    val respondedAt: Long? = null,
)
```

`InviteStatus`: `PENDING, ACCEPTED, DECLINED, CANCELLED, EXPIRED`. `EXPIRED` is computed client-side when `now > expiresAt && status == PENDING` (no Cloud Function flips it; persisted only when a client cancels).

`targetUserId` is populated lazily — null when first invited unknown email; resolved on accept. Faster lookup for return users.

No `inviteToken / maxUsages / usedCount` fields exist (decision 3).
<!-- AI:END -->

<!-- AI:SECTION id=members-invites-dual-write task=members,invites,sync,dual-write -->
## Dual-write contract

Both entities follow the standard MoneySurfer dual-write pattern:

1. Repository impl writes to Room via DAO (local truth).
2. `OutboxEnqueuer.enqueueUpsert(entityType, id, scopeKey=workspaceId, op)` enqueues a `PendingMutation`. No payload serialization happens at enqueue time — the push worker re-reads the entity from Room.
3. `SyncCoordinator` picks up the mutation; the matching `*SyncPlugin` stamps `clientVersionCode` and pushes to Firestore.
4. Status transitions (`cancel`, `markAccepted`, `markDeclined`, `markRemoved`, `markLeft`) are read-modify-upsert — preserves dual-write contract, never bypasses it.

Reference impls (the ones a new entity should mirror):
- `data-local/.../repository/WorkspaceInviteRepositoryImpl.kt`
- `WorkspaceMemberRepositoryImpl` (members; same shape)
- `sync-surfer/.../sync/plugin/WorkspaceInviteSyncPlugin.kt`
- `sync-surfer/.../sync/plugin/WorkspaceMemberSyncPlugin.kt`

Specs guarding the contract:
- `sync-surfer/.../repository/WorkspaceInviteRepositoryDualWriteSpec.kt` — insert, demo skip, status transitions enqueue (PENDING → ACCEPTED/DECLINED/CANCELLED), payload JSON shape.
- `WorkspaceMemberRepositoryDualWriteSpec` — mirror for members; covers `markRemoved` / `markLeft` upsert + outbox enqueue with new status.
- `integration-test/.../repository/WorkspaceInviteRepositoryIntegrationIT.kt` — real Room round-trip.

`SyncEntityTypes` carries both `WORKSPACE_MEMBER` and `WORKSPACE_INVITE`. Push order is `members → invites → accounts/categories/transactions` (members must exist before subcollection writes that key off `isMember(wid)`).
<!-- AI:END -->

<!-- AI:SECTION id=invites-discovery task=invites,bootstrap,query -->
## In-app discovery flow

Decision 4 (no email delivery) means invitees must find pending invites when they open the app. Two read surfaces support this:

1. **Per-workspace roster.** Members of `wid` can list `workspaces/{wid}/invites/*` directly — used by the owner-side roster screen.
2. **Cross-workspace inbox for the current user.** A Firestore *collection-group* query (`invites` collection group) filtered by `targetUserId == auth.uid` (and the legacy email-match path) returns every pending invite addressed to the current user without enumerating workspaces.

`ListPendingInvitesForCurrentUserUseCase` combines `getByEmail` + `getByTargetUserId`, filters status `PENDING`, and computes display-only `EXPIRED` client-side.

The legacy email-match path lower-cases both sides because some early invites were stored mixed-case before `SendInviteUseCase` started normalizing addresses. `is string` guards keep the rule from erroring on docs missing the field or tokens missing the email claim.
<!-- AI:END -->

<!-- AI:SECTION id=members-invites-rules task=members,invites,firestore-rules,security -->
## Firestore rules

### `/members/{uid}`

```js
match /members/{uid} {
  allow read: if isMember(wid);
  // Owner manages everyone (role, status=REMOVED, snapshots).
  // A signed-in user may create their own row (accept invite) or
  // soft-leave their own row (status=LEFT). Self-writes cannot
  // change role and cannot flip status to REMOVED.
  allow create:
    if (isOwner(wid) || request.auth.uid == uid)
    && hasValidClientVersion();
  allow update:
    if hasValidClientVersion() && (
         isOwner(wid)
      || (request.auth.uid == uid
          && request.resource.data.role == resource.data.role
          && request.resource.data.status in ["ACTIVE", "LEFT"])
    );
  allow delete: if false;
}
```

### `/workspaces/{wid}/invites/{inviteId}` (per-workspace)

```js
match /invites/{inviteId} {
  allow read: if isMember(wid);
  allow read: if resource.data.targetUserId == request.auth.uid;

  allow create: if isOwner(wid)
    && hasValidClientVersion()
    && request.resource.data.status == "PENDING"
    && request.resource.data.invitedByUserId == request.auth.uid;

  // Owner can transition (PENDING → CANCELLED, etc.); the invitee can flip
  // PENDING → ACCEPTED/DECLINED on rows that target them by email or
  // targetUserId. Idempotent re-push of the same terminal status is allowed
  // so an outbox retry whose first push already landed does not loop forever.
  allow update: if hasValidClientVersion() && (
       isOwner(wid)
    || (signedIn()
        && (
             (resource.data.email is string
                && request.auth.token.email is string
                && resource.data.email.lower() == request.auth.token.email.lower())
          || resource.data.targetUserId == request.auth.uid
        )
        && (
             (resource.data.status == "PENDING"
                && request.resource.data.status in ["ACCEPTED", "DECLINED"])
          || (resource.data.status in ["ACCEPTED", "DECLINED"]
                && request.resource.data.status == resource.data.status)
        ))
  );
  allow delete: if false;
}
```

### Wildcard read for invite discovery

The cross-workspace inbox needs to read invites *without* membership in the parent workspace. The wildcard block lifts that read:

```js
match /{path=**}/invites/{inviteId} {
  allow read: if resource.data.targetUserId == request.auth.uid;
  allow read: if resource.data.email is string
    && request.auth.token.email is string
    && resource.data.email.lower() == request.auth.token.email.lower();
}
```

ALL writes still go through the nested per-workspace `match /workspaces/{wid}/invites/{inviteId}` block. The wildcard only relaxes reads, and only for docs that actually target the requesting user.

### Test coverage

- `firestore-tests/test/invites.spec.js`
- `firestore-tests/test/members.spec.js`
<!-- AI:END -->

## Forward-looking notes

The shared/feature layer (members screen wiring, invite-form
bottom-sheet, pending-invites inbox screen, navigation, banners) and
new UIKit rows (`SurferInvitePendingRow`, `SurferInviteForm`,
`SurferRoleMenu`) are still phased work and not yet implemented. The
domain / data / rules layers documented here are already shipped.
