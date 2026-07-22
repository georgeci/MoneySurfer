# Data Models

<!-- DOCS:TOC -->
## Contents
- [Data Models](#data-models)
- [TL;DR for agents](#tldr-for-agents)
- [Time policy](#time-policy)
- [ID and naming conventions](#id-and-naming-conventions)
- [Domain ↔ Room ↔ Firestore](#domain--room--firestore)
  - [users/{uid}](#usersuid)
  - [userEmails/{lowercasedemail}](#useremailslowercasedemail)
  - [workspaces/{wid}](#workspaceswid)
  - [workspaces/{wid}/members/{uid} — WorkspaceMember](#workspaceswidmembersuid--workspacemember)
  - [workspaces/{wid}/invites/{inviteId} — WorkspaceInvite](#workspaceswidinvitesinviteid--workspaceinvite)
  - [workspaces/{wid}/accounts/{aid}](#workspaceswidaccountsaid)
  - [workspaces/{wid}/categories/{cid}](#workspaceswidcategoriescid)
  - [workspaces/{wid}/transactions/{tid} — Transaction](#workspaceswidtransactionstid--transaction)
  - [workspaces/{wid}/budgets/{bid} — Budget](#workspaceswidbudgetsbid--budget)
  - [workspaces/{wid}/recurringRules/{rid} — RecurringRule](#workspaceswidrecurringrulesrid--recurringrule)
- [Sync metadata fields](#sync-metadata-fields)
- [Mappers](#mappers)
<!-- DOCS:END -->

## TL;DR for agents

- Domain uses rich time types (`kotlin.time.Instant`, `kotlinx.datetime.LocalDate`,
  `kotlinx.datetime.YearMonth`). Storage and wire keep primitive forms
  (`Long epochMillis` for moments, ISO-8601 `String` for calendar dates).
- Conversion lives in repository/mappers, **not** in domain or DAO.
- Standard time field names: `operationAt` (when the business event happened),
  `createdAt`, `updatedAt`, `deletedAt`. No legacy synonyms (`addedAt`,
  `joinedAt`, `timestamp`).
- Soft-delete only on Firestore — every entity DTO carries `deletedAt: Long?`
  and `clientVersionCode: Int`.
- Field names are identical between Room columns and Firestore document fields
  (1:1 round-trip). If they differ, fix the data layer, do not paper over it
  in the mapper.

READ WHEN:
- adding or renaming a field on any entity
- changing time semantics on a model
- adding a new sync collection
- writing or reviewing data mappers

<!-- AI:SECTION id=time-policy task=time,persistence,domain -->
## Time policy

| Type | Use for | Storage (Room / Firestore) |
|---|---|---|
| `kotlin.time.Instant` | Technical moments — `createdAt`, `updatedAt`, `deletedAt`, `syncedAt`, `expiresAt`, `respondedAt`, `leftAt`, `removedAt`, `nextRunAt`, sync cursors, outbox times. Also business "когда реально произошло" — `Transaction.operationAt`. | `Long` epoch milliseconds |
| `kotlinx.datetime.LocalDate` | Calendar dates without wall-clock time — `Budget.startDate`, `RecurringRule.startDate`, period boundaries when only the day matters. | ISO-8601 `String` (`"2026-05-04"`) |
| `kotlinx.datetime.YearMonth` | Monthly periods — `Budget.period` month, monthly reports/limits. | ISO-8601 `String` (`"2026-05"`) |
| `kotlinx.datetime.LocalDateTime` | UI/input only (date+time pickers). Never primary storage. | n/a |

Rules:

- Domain models declare the rich type. Storage/wire types are primitive.
- Conversion is centralized in `data-local` and `data-remote` mapper helpers,
  not duplicated per repo.
- `Transaction.operationAt` is `Instant` (point in time, ordered with
  `createdAt` as tiebreaker). Group/render by local day in UI via
  `instant.toLocalDateTime(zone).date`.
- Never group by `epochMillis / dayMs` — always go through `TimeZone`.
- Inject `domain.primitives.ClockUseCase` and call `clock.now()` for "now"; do
  not call `kotlin.time.Clock.System.now()` directly outside that abstraction.
- `kotlin.time.Instant` is the canonical instant type (Workspace already uses
  it). Do not introduce `kotlinx.datetime.Instant` in new code.
<!-- AI:END -->

## ID and naming conventions

- Domain IDs: UUID-backed value classes with `Companion.uuid()`
  (e.g. `WorkspaceId`, `TransactionId`).
- Room columns and Firestore fields share names character-for-character.
- Standard timestamps: `createdAt`, `updatedAt`, `deletedAt`. Operation moment
  on a domain event is `operationAt`. Avoid synonyms.
- Soft-delete tombstone: `deletedAt: Long?` (`null` = live).
- App-version gate: `clientVersionCode: Int` on every Firestore entity write.
  See [app-version-gate.md](app-version-gate.md).

## Domain ↔ Room ↔ Firestore

Tables below describe the **target** schema. Storage type follows the time
policy above (Room/Firestore primitive on the left, domain rich type on the
right).

### `users/{uid}`

| Field | Domain | Room | Firestore |
|---|---|---|---|
| `id` | `UserId` | PK `id: String` | doc id |
| `displayName` | `String?` | `String?` | `String?` |
| `email` | `String?` | — *(not in Room)* | `String?` |
| `isAnon` | `Boolean` | `Boolean` | `Boolean` |
| `workspaceIds` | `List<WorkspaceId>` | — | `List<String>` |
| `defaultWorkspaceId` | `WorkspaceId?` | — | `String?` |
| `invitedWorkspaceIds` | `List<WorkspaceId>` | — | `List<String>` |
| `createdAt` *(Firestore-only)* | — | — | `Long` |

`UserDoc.createdAt` is intentionally not surfaced into the domain model.

### `userEmails/{lowercased_email}`

Discovery-only mapping. Firestore-only; no Room mirror.

| Field | Firestore |
|---|---|
| `uid` | `String` |
| `updatedAt` | `Long` |

Single-key `get` only (`list` denied). This exact-address lookup is the invite
email→uid resolution and is an **accepted** LOW existence oracle — see
[firestore-rules-bugs.md](firestore-rules-bugs.md) #161 for the decision and why
a server-callable alternative is out of scope.

### `workspaces/{wid}`

| Field | Domain | Room | Firestore |
|---|---|---|---|
| `id` | `WorkspaceId` | PK `id: String` | doc id |
| `name` | `String` | `String` | `String` |
| `description` | `String` | `String` | `String` |
| `baseCurrency` | `CurrencyCode` | `String` | `String` |
| `ownerId` | `UserId` (FK→users) | `String` (FK) | `String` |
| `archived` | `Boolean` | `Boolean` | `Boolean` |
| `createdAt` | `Instant` | `Long` | `Long` |
| `updatedAt` | `Instant` | `Long` | `Long` |
| `deletedAt` | `Instant?` | — *(local truth, no soft-delete)* | `Long?` |
| `clientVersionCode` | — | — | `Int` |

### `workspaces/{wid}/members/{uid}` — **WorkspaceMember**

Field name unified: was `addedAt` in Firestore; now `createdAt` everywhere.

| Field | Domain | Room | Firestore |
|---|---|---|---|
| `userId` | `UserId` | PK part `userId: String` | doc id |
| `workspaceId` | `WorkspaceId` | PK part `workspaceId: String` | implicit (path) |
| `role` | `WorkspaceRole` | `String` | `String` |
| `status` | `WorkspaceMemberStatus` | `String` | `String` |
| `displayName` | `String` | `String` | `String` |
| `email` | `String?` | `String?` | `String?` |
| `addedByUserId` | `UserId?` | `String?` | `String?` |
| `createdAt` | `Instant` | `Long` | `Long` |
| `updatedAt` | `Instant` | `Long` | `Long` |
| `leftAt` | `Instant?` | `Long?` | `Long?` |
| `removedAt` | `Instant?` | `Long?` | `Long?` |
| `deletedAt` | — | — | `Long?` *(back-compat tombstone)* |
| `clientVersionCode` | — | — | `Int` |

### `workspaces/{wid}/invites/{inviteId}` — **WorkspaceInvite**

| Field | Domain | Room | Firestore |
|---|---|---|---|
| `id` | `WorkspaceInviteId` | PK | doc id |
| `workspaceId` | `WorkspaceId` | `String` | implicit (path) |
| `email` | `String` | `String` | `String` |
| `targetUserId` | `UserId?` | `String?` | `String?` |
| `role` | `WorkspaceRole` | `String` | `String` |
| `status` | `InviteStatus` | `String` | `String` |
| `invitedByUserId` | `UserId` | `String` | `String` |
| `createdAt` | `Instant` | `Long` | `Long` |
| `updatedAt` | `Instant` | `Long` | `Long` |
| `expiresAt` | `Instant` | `Long` | `Long` |
| `respondedAt` | `Instant?` | `Long?` | `Long?` |
| `deletedAt` | — | — | `Long?` |
| `clientVersionCode` | — | — | `Int` |

### `workspaces/{wid}/accounts/{aid}`

| Field | Domain | Room | Firestore |
|---|---|---|---|
| `id` | `AccountId` | PK | doc id |
| `workspaceId` | `WorkspaceId` (FK) | `String` (FK) | implicit (path) |
| `name` | `String` | `String` | `String` |
| `type` | `AccountType` | `String` | `String` |
| `currencyCode` | `CurrencyCode` | `String` (col `currency`) | `String` (col `currency`) |
| `balance` | `Money` | `Long` (minor units) | `Long` |
| `updatedAt` | `Instant` | `Long` | `Long` |
| `deletedAt` | — | — | `Long?` |
| `clientVersionCode` | — | — | `Int` |

### `workspaces/{wid}/categories/{cid}`

| Field | Domain | Room | Firestore |
|---|---|---|---|
| `id` | `CategoryId` | PK | doc id |
| `workspaceId` | `WorkspaceId` (FK) | `String` (FK) | implicit (path) |
| `name` | `String` | `String` | `String` |
| `type` | `CategoryType` | `String` | `String` |
| `parentId` | `CategoryId?` (FK self) | `String?` (FK self) | `String?` |
| `createdAt` | `Instant` | `Long` | `Long` |
| `updatedAt` | `Instant` | `Long` | `Long` |
| `deletedAt` | — | — | `Long?` |
| `clientVersionCode` | — | — | `Int` |

### `workspaces/{wid}/transactions/{tid}` — **Transaction**

`timestamp` (legacy) is gone. Time fields:
- `operationAt: Instant` — absolute moment of the operation (sortable to ms).
- `operationDate: LocalDate` — business date the user assigned to the
  transaction. The budget treats it as living on this day. Stored explicitly
  so backdating intent survives timezone swings (a 23:30 entry "for yesterday"
  doesn't drift into "today" when the device zone moves).
- `createdAt: Instant` — when the row was created locally. Differs from
  `operationAt` for backdated entries; serves as audit + stable tiebreaker.
- `updatedAt: Instant` — last LWW write.

| Field | Domain | Room | Firestore |
|---|---|---|---|
| `id` | `TransactionId` | PK | doc id |
| `workspaceId` | `WorkspaceId` (FK) | `String` (FK) | implicit (path) |
| `accountId` | `AccountId` (FK) | `String` (FK) | `String` |
| `categoryId` | `CategoryId?` (FK) | `String?` (FK) | `String?` |
| `money` (`amount`) | `Money` | `Long` (col `amount`) | `Long` (col `amount`) |
| `currencyCode` | `CurrencyCode` | `String` | `String` |
| `note` | `String` | `String` | `String` |
| `merchant` | `String` | `String` | `String` |
| `tags` | `List<String>` | `String` (CSV col) | `List<String>` |
| `operationAt` | `Instant` | `Long` | `Long` |
| `operationDate` | `LocalDate` | `String` (ISO-8601) | `String` (ISO-8601) |
| `type` | `TransactionType` | `String` | `String` |
| `status` | `TransactionStatus` | `String` | `String` |
| `createdAt` | `Instant` | `Long` | `Long` |
| `updatedAt` | `Instant` | `Long` | `Long` |
| `transferId` | `TransferId?` | `String?` | `String?` |
| `recurringRuleId` | `RecurringRuleId?` | `String?` | `String?` |
| `deletedAt` | — | — | `Long?` |
| `clientVersionCode` | — | — | `Int` |

UI grouping is by `operationDate`. Sort within a group:
`(operationDate DESC, operationAt DESC, createdAt DESC)`. `createdAt` is the
stable tiebreaker for entries with identical `operationAt`.

Detail fields (issue #260):

- `merchant` — who the money went to, kept apart from `note` so both can be
  shown and searched. Indexed in `transactions_fts` alongside `note`, so one
  `MATCH` covers either.
- `tags` — an embedded list in one CSV column, same shape as
  `Budget.categoryIds`. Not a join table: tags have no identity of their own
  yet (nothing renames, merges or counts them across transactions). Normalize
  through `TransactionTags.normalize` before storing — a tag carrying the
  separator would otherwise read back as two.
- `recurringRuleId` — a link to `RecurringRule`, deliberately not an
  `isRecurring` boolean, which would be a second copy of a fact the rule
  already owns. Not a Room FK: `recurring_rules` is not synced yet, so a
  pulled transaction can name a rule this device has never seen.
- `reference` (`TX-8A13`) is **derived**, not stored — see
  `TransactionReference.kt`. It is a display label, not a key. A
  bank-supplied reference surviving a statement import would be a separate,
  stored field.
- Receipt attachments are **out of scope** for this model. Attachment storage
  is its own epic (blob storage, quotas, offline cache); no boolean stands in
  for it here.

### `workspaces/{wid}/budgets/{bid}` — **Budget**

| Field | Domain | Room | Firestore |
|---|---|---|---|
| `id` | `BudgetId` | PK | doc id |
| `name` | `String` | `String` | `String` |
| `categoryIds` | `List<CategoryId>` | `String` (serialized) | `List<String>` |
| `amount` | `Money` | `Long` | `Long` |
| `period` | `BudgetPeriod` (enum) | `String` | `String` |
| `startDate` | `LocalDate` | `String` (ISO-8601) | `String` (ISO-8601) |
| `alertPercent` | `Int` | `Int` | `Int` |
| `isActive` | `Boolean` | `Boolean` | `Boolean` |
| `createdAt` | `Instant` | `Long` | `Long` |
| `updatedAt` | `Instant` | `Long` | `Long` |
| `deletedAt` | — | — | `Long?` |
| `clientVersionCode` | — | — | `Int` |

`BudgetPeriod` is `WEEKLY | MONTHLY | YEARLY`. For monthly reports/limits use
`YearMonth` derived from `startDate` rather than a separate stored field.

### `workspaces/{wid}/recurringRules/{rid}` — **RecurringRule**

| Field | Domain | Room | Firestore |
|---|---|---|---|
| `id` | `RecurringRuleId` | PK | doc id |
| `title` | `String` | `String` | `String` |
| `amount` | `Money` | `Long` | `Long` |
| `categoryId` | `CategoryId` (FK) | `String` (FK) | `String` |
| `schedule.frequency` | `RecurringFrequency` | `String` (`scheduleFrequency`) | `String` |
| `schedule.interval` | `Int` | `Int` (`scheduleInterval`) | `Int` |
| `schedule.daysOfWeek` | `Set<DayOfWeek>` | `String` (serialized) | `List<String>` |
| `schedule.daysOfMonth` | `Set<Int>` | `String` (serialized) | `List<Int>` |
| `schedule.missingDayPolicy` | `MissingDayPolicy` | `String` | `String` |
| `startDate` | `LocalDate` | `String` (ISO-8601) | `String` (ISO-8601) |
| `nextRunAt` | `Instant?` | `Long?` | `Long?` |
| `autoCreate` | `Boolean` | `Boolean` | `Boolean` |
| `isActive` | `Boolean` | `Boolean` | `Boolean` |
| `createdAt` | `Instant` | `Long` | `Long` |
| `updatedAt` | `Instant` | `Long` | `Long` |
| `deletedAt` | — | — | `Long?` |
| `clientVersionCode` | — | — | `Int` |

## Sync metadata fields

Every Firestore entity DTO carries:

- `updatedAt: Long` — LWW key for cursor-based pull.
- `deletedAt: Long?` — soft-delete tombstone.
- `clientVersionCode: Int` — app-version gate, validated by Firestore rules
  (see [app-version-gate.md](app-version-gate.md)).

Room mirrors do not carry `deletedAt` / `clientVersionCode` — Room is local
truth, deletes are physical.

## Mappers

- Domain ↔ Room: per-repository in
  [data-local/.../data/repository/](../../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/).
- Room ↔ Firestore DTO: [SyncDtoMappers.kt](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/SyncDtoMappers.kt)
  (1:1 by field name — no field renames in this layer).
- Time helpers should live in one shared mapper file in `data` (Long ↔ Instant,
  ISO-8601 ↔ LocalDate, ISO-8601 ↔ YearMonth) and never in `domain`.
