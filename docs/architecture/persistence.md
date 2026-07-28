# Persistence

<!-- DOCS:TOC -->
## Contents
- [Persistence](#persistence)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
<!-- DOCS:END -->

## TL;DR for agents

- Keep persistence implementations behind domain or sync-facing interfaces.
- Do not leak Room, DataStore, Firebase, or Firestore types into `domain`.
- Firestore rules and schema must evolve together.
- Read this when changing storage, wire models, rules, or migrations.
- For per-entity field shape see [data-models.md](data-models.md). For time
  type policy see [data-models.md#time-policy](data-models.md#time-policy)
  and [../../md/time.md](../../md/time.md).

READ WHEN:
- changing Room schema
- changing Firestore schema
- editing DataStore usage
- changing persistence repositories

<!-- AI:SECTION id=persistence-rules task=persistence,room,firestore,datastore -->
## Rules

- Domain models use rich time types (`kotlin.time.Instant`,
  `kotlinx.datetime.LocalDate`, `kotlinx.datetime.YearMonth`).
  Storage and wire keep primitive forms (`Long epochMillis` for moments,
  ISO-8601 `String` for calendar dates). Conversion is in mappers, not domain.
- Standard time-field names: `operationAt`, `createdAt`, `updatedAt`,
  `deletedAt`. No legacy synonyms.
- Room column names and Firestore document field names match 1:1.
- Every entity DTO has `deletedAt: Long?` and `clientVersionCode: Int`.
  Firestore is always soft-delete. Room is soft-delete for **transactions**
  only (issue #346): `transactions.deletedAt` mirrors the wire field, every
  read query in `TransactionDao` carries `deletedAt IS NULL`, and tombstones
  are purged after a retention window — see
  [Local tombstone retention](sync-pull-lww.md#local-tombstone-retention).
  Accounts, categories, budgets and goals still hard-delete locally; a pulled
  tombstone drops their row.
- Rules bug log: see [firestore-rules-bugs.md](firestore-rules-bugs.md).
- App-version gate behavior: see [app-version-gate.md](app-version-gate.md).
<!-- AI:END -->
