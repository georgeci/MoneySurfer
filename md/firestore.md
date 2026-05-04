# Firestore data model

Target project: `moneysurfer-dev`. Mode: **Firestore Native**. Region: `eur3`
(Europe multi-region; immutable after creation).

The model mirrors the Room entities in `data-local` 1:1 by field name so a
Firestore document round-trips through Kotlin serialization without per-field
mapping. Workspace is the tenancy boundary — every per-user document is
nested under `workspaces/{wid}` and gated by membership in the security rules.

> Time conventions and per-entity field tables live in
> [docs/architecture/data-models.md](../docs/architecture/data-models.md).
> This file documents Firestore-specific concerns: collection layout, write
> contract, indexes, and rules sketch.

## Collections

```
users/{uid}                                # personal user doc + workspace pointers
userEmails/{lowercased_email}              # email → uid lookup, single-doc reads only
appConfig/{docId}                          # public read-only config (force-update gate)

workspaces/{wid}                           # tenancy root
workspaces/{wid}/members/{uid}             # membership row, doc id = userId
workspaces/{wid}/invites/{inviteId}        # invitations, doc id = invite UUID
workspaces/{wid}/accounts/{aid}
workspaces/{wid}/categories/{cid}
workspaces/{wid}/transactions/{tid}
workspaces/{wid}/budgets/{bid}
workspaces/{wid}/recurringRules/{rid}
```

## Wire types

- Moment-in-time fields (`createdAt`, `updatedAt`, `operationAt`,
  `deletedAt`, `expiresAt`, `respondedAt`, `leftAt`, `removedAt`,
  `nextRunAt`) — `int64` epoch milliseconds.
- Calendar dates (`startDate`) — `string` ISO-8601 (`"2026-05-04"`).
- Enums — name strings (e.g. `"OWNER"`, `"EXPENSE"`).
- Money / amounts — `int64` minor units.

Field-by-field shape: see
[data-models.md](../docs/architecture/data-models.md#domain--room--firestore).

## Sync metadata (every entity DTO)

- `updatedAt: int64` — LWW key, also drives cursor-based pull.
- `deletedAt: int64?` — soft-delete tombstone (`null` = live). Hard delete is
  forbidden by rules.
- `clientVersionCode: int` — app-version gate, validated server-side. See
  [docs/architecture/app-version-gate.md](../docs/architecture/app-version-gate.md).

## Indexes (composite)

Single-field equality + `operationAt` / `updatedAt` DESC covers the common
list queries. Source of truth: [`firestore.indexes.json`](../firestore.indexes.json).

| Collection                                | Fields                                                          |
|-------------------------------------------|-----------------------------------------------------------------|
| `transactions` (collection group)         | `accountId` ASC, `operationAt` DESC                             |
| `transactions`                            | `categoryId` ASC, `operationAt` DESC                            |
| `transactions`                            | `type` ASC, `operationAt` DESC                                  |
| `transactions`                            | `accountId` ASC, `type` ASC, `operationAt` DESC                 |
| `categories`                              | `type` ASC, `name` ASC                                          |
| `invites`                                 | `status` ASC, `createdAt` DESC                                  |
| `invites`                                 | `email` ASC, `status` ASC                                       |
| `invites`                                 | `targetUserId` ASC, `status` ASC                                |
| `invites`                                 | `targetUserId` ASC, `updatedAt` ASC                             |

## Security rules

Source: [`firestore.rules`](../firestore.rules). Rules bug log:
[firestore-rules-bugs.md](../docs/architecture/firestore-rules-bugs.md).

Highlights:

- Workspace tenancy — every nested write is gated by `isMember(wid)`.
  Workspace owner alone can update the parent workspace doc.
- Hard delete forbidden everywhere — clients must `update` with `deletedAt`.
- `clientVersionCode` minimum is enforced via `hasValidClientVersion()` on
  every entity write.
- `userEmails/{email}` is `get`-only, no `list`. Writes require the email key
  to match the requester's Firebase Auth email claim.
- `invites` collection-group reads are declared on the wildcard
  `match /{path=**}/invites/{inviteId}` (per-doc reads inside a workspace
  also covered).

## Mapping

- Room ↔ Firestore DTO: [`SyncDtoMappers.kt`](../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/SyncDtoMappers.kt).
  No field renames in this layer — Room columns and Firestore fields share
  names character-for-character.
- Domain ↔ Room: per-repository under
  [`data-local/.../data/repository/`](../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/).
  Rich-type ↔ primitive conversion lives here.

## Sync

The v1 push-then-pull copier was replaced by sync v2 (cursor-based incremental
pull, dual-write outbox, LWW conflict resolution, soft-delete). See
[docs/architecture/sync.md](../docs/architecture/sync.md) and its sub-docs
(`sync-architecture`, `sync-coordinator`, `sync-outbox`, `sync-pull-lww`,
`sync-platform`).

Document IDs are UUID-backed value classes from `domain` (no Room `Long` IDs).
