# Persistence

<!-- DOCS:TOC -->
## Contents
- [Persistence](#persistence)
- [TL;DR for agents](#tldr-for-agents)
- [Rules](#rules)
- [Room schema versioning](#room-schema-versioning)
<!-- DOCS:END -->

## TL;DR for agents

- Keep persistence implementations behind domain or sync-facing interfaces.
- Do not leak Room, DataStore, Firebase, or Firestore types into `domain`.
- Firestore rules and schema must evolve together.
- Read this when changing storage, wire models, rules, or migrations.
- For per-entity field shape see [data-models.md](data-models.md). For time
  type policy see [data-models.md#time-policy](data-models.md#time-policy)
  and [../../md/time.md](../../md/time.md).
- Room schema version is frozen at 36 for the first release; every step above it needs
  a hand-written migration and no release build may fall back to a destructive one —
  see [Room schema versioning](#room-schema-versioning).

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

<!-- AI:SECTION id=room-schema-versioning task=persistence,room,migration -->
## Room schema versioning

**Frozen release baseline: schema version 36.** That is the version of
`MoneySurferDatabase` shipping with the first MVP release, recorded in code as
`MONEY_SURFER_DB_RELEASE_BASELINE_VERSION`. It is a historical marker, not a moving
floor — never raise it. The live version stays in `MONEY_SURFER_DB_VERSION`, which is
the single source of truth the `@Database` annotation reads.

Policy:

- **Hand-written migrations only.** Every schema step at or above the baseline is
  carried by a `Migration` object under
  `data-local/src/commonMain/.../data/db/migration/`, one file per change, registered
  in `addMigrations(...)` in `DatabaseBuilder.kt`. Auto-migrations are not used: the
  changes so far (FTS content tables, backfills, tombstones) need explicit SQL.
- **No destructive fallback in release builds.** `getRoomDatabase` takes
  `allowDestructiveMigration`, defaulting to the release-safe `false`. Only debuggable
  hosts pass `true` — Android via `Context.isDebuggableBuild()` (`FLAG_DEBUGGABLE`),
  iOS via `Platform.isDebugBinary`, and the JVM desktop host unconditionally while it
  remains developer-only. A release build that hits an unmigrated step throws rather
  than silently dropping the user's ledger.
- **Versions below the baseline are unreachable in the field.** Nothing under 36 ever
  shipped, so the pre-baseline gaps (`< 25`, plus `26 → 27` and `28 → 29`) have no
  migration and never will. They are only reachable on a developer machine, where the
  destructive fallback handles them.
- **Exported schemas are committed.** `data-local/build.gradle.kts` points Room's
  `schemaDirectory` at `data-local/schemas/`, and those JSON files are checked in.
  Bumping `MONEY_SURFER_DB_VERSION` without committing the newly exported
  `<version>.json` is a build failure.

Enforcement: `./gradlew :data-local:verifyRoomMigrations` (wired into `check` and run
as its own CI step) fails when the exported schemas and `MONEY_SURFER_DB_VERSION`
disagree, or when a step at/above the baseline has no `MIGRATION_<n>_<n+1>` declared
and registered.

Adding a schema change:

1. Edit the entities and bump `MONEY_SURFER_DB_VERSION`.
2. Build the module so Room exports `schemas/**/<version>.json`; commit it.
3. Add `MIGRATION_<n>_<n+1>` in `data/db/migration/` and register it in
   `DatabaseBuilder.kt`.
4. Run `./gradlew :data-local:verifyRoomMigrations` — it must be green before the PR.
<!-- AI:END -->
