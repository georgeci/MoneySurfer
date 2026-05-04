# Firestore data model

Target project: `moneysurfer-dev`. Mode: **Firestore Native**. Region: TBD —
proposed `eur3` (Europe multi-region). Region is immutable after the database
is created.

The model mirrors the Room entities in `:data` so the same domain `Account`,
`Category`, `Transaction`, etc. can be hydrated from either source. Workspace
is the tenancy boundary — every per-user document is nested under
`workspaces/{workspaceId}` so a single security rule can gate access by
membership.

```
users/{uid}
  displayName        : string
  email              : string
  defaultWorkspaceId : string?
  createdAt          : timestamp

workspaces/{workspaceId}
  name         : string
  description  : string                -- "" when empty
  baseCurrency : string                -- ISO 4217 default for the workspace
  ownerId      : string                -- stringified Room user id (will become Firebase uid once auth lands)
  archived     : bool
  createdAt    : int64                 -- epoch millis (matches Room storage)

workspaces/{wid}/members/{uid}
  role     : "OWNER" | "EDITOR" | "VIEWER"
  joinedAt : int64

workspaces/{wid}/accounts/{accountId}
  name     : string
  type     : "CASH" | "BANK" | "CARD" | "SAVINGS"
  currency : string                    -- ISO 4217
  balance  : int64                     -- minor units (cents)

workspaces/{wid}/categories/{categoryId}
  name      : string
  type      : "EXPENSE" | "INCOME"
  parentId  : string?                  -- self-reference under same workspace
  createdAt : int64

workspaces/{wid}/transactions/{txnId}
  accountId    : string
  categoryId   : string?               -- null for INITIAL_BALANCE rows
  amount       : int64                 -- minor units; negative = expense
  currencyCode : string                -- ISO 4217
  note         : string
  timestamp    : int64                 -- when the transaction occurred (epoch millis)
  type         : "REGULAR" | "INITIAL_BALANCE"

workspaces/{wid}/budgets/{budgetId}
  name         : string
  categoryIds  : array<string>
  periodStart  : timestamp
  periodEnd    : timestamp
  capMinor     : int64
  createdAt    : timestamp

workspaces/{wid}/recurringRules/{ruleId}
  template    : map                    -- partial transaction shape
    accountId  : string
    categoryId : string?
    amountMinor: int64
    currency   : string
    note       : string
  cadence     : "DAILY" | "WEEKLY" | "MONTHLY" | "YEARLY"
  dayOfWeek   : int?                   -- 1..7, only when cadence = WEEKLY
  dayOfMonth  : int?                   -- 1..31, only when cadence = MONTHLY/YEARLY
  monthOfYear : int?                   -- 1..12, only when cadence = YEARLY
  startsAt    : timestamp
  endsAt      : timestamp?
  active      : bool
```

## Indexes (composite)

Single-field equality + `timestamp DESC` covers the common list queries.

| Collection                                | Fields                                                          | Order        |
|-------------------------------------------|-----------------------------------------------------------------|--------------|
| `workspaces/{wid}/transactions`           | `accountId` ASC, `timestamp` DESC                               | DESC         |
| `workspaces/{wid}/transactions`           | `categoryId` ASC, `timestamp` DESC                              | DESC         |
| `workspaces/{wid}/transactions`           | `type` ASC, `timestamp` DESC                                    | DESC         |
| `workspaces/{wid}/transactions`           | `accountId` ASC, `type` ASC, `timestamp` DESC                   | DESC         |
| `workspaces/{wid}/categories`             | `type` ASC, `name` ASC                                          | ASC          |

The selected-category preview uses an in-memory count derived from the
transaction stream, so no additional index is needed for that.

## Security rules (sketch)

```
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {

    function signedIn() {
      return request.auth != null;
    }

    function isMember(wid) {
      return signedIn()
        && exists(/databases/$(db)/documents/workspaces/$(wid)/members/$(request.auth.uid));
    }

    function isOwner(wid) {
      return signedIn()
        && get(/databases/$(db)/documents/workspaces/$(wid)).data.ownerUid == request.auth.uid;
    }

    match /users/{uid} {
      allow read, write: if signedIn() && request.auth.uid == uid;
    }

    match /workspaces/{wid} {
      allow read: if isMember(wid);
      allow create: if signedIn() && request.resource.data.ownerUid == request.auth.uid;
      allow update, delete: if isOwner(wid);

      match /members/{uid} {
        allow read: if isMember(wid);
        allow write: if isOwner(wid);
      }

      match /{path=**} {
        // accounts, categories, transactions, budgets, recurringRules
        allow read, write: if isMember(wid);
      }
    }
  }
}
```

## Mapping from existing Room types

| Room                          | Firestore                                                      |
|-------------------------------|----------------------------------------------------------------|
| `WorkspaceEntity`             | `workspaces/{wid}` (id is the doc id)                          |
| `WorkspaceMemberEntity`       | `workspaces/{wid}/members/{uid}`                               |
| `AccountEntity`               | `workspaces/{wid}/accounts/{aid}`                              |
| `CategoryEntity`              | `workspaces/{wid}/categories/{cid}` (parentId stays string)    |
| `TransactionEntity`           | `workspaces/{wid}/transactions/{tid}`                          |
| `BudgetEntity`                | `workspaces/{wid}/budgets/{bid}`                               |
| `RecurringRuleEntity`         | `workspaces/{wid}/recurringRules/{rid}`                        |
| `UserEntity`                  | `users/{uid}`                                                  |

`balance: Long` (minor units) and `amount: Long` map directly to int64. Enums
(`AccountType`, `CategoryType`, `TransactionType`) are stored as their `name`
strings — same convention as the Room mappers, so the existing
`runCatching { Enum.valueOf(...) }.getOrDefault(...)` fallback applies.

Numeric ids in Room are autogenerated `Long`s; Firestore documents use
auto-id strings. The cross-source repository will own the mapping (Room
copies are read-through cache; Firestore is the source of truth once
remote sync lands).

## Sync

The v1 push-then-pull copier described in earlier drafts has been replaced by sync v2 (cursor-based incremental pull, dual-write outbox, LWW conflict resolution, soft-delete, server timestamps planned). See [docs/architecture/sync.md](../docs/architecture/sync.md) and its sub-docs (`sync-architecture`, `sync-coordinator`, `sync-outbox`, `sync-pull-lww`, `sync-platform`).

Document ids: now UUID-backed value classes from domain (no Room `Long` ids). Mapping in `sync-surfer/.../data/sync/SyncDtoMappers.kt`.
