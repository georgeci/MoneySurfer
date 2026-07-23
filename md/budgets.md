# Budgets — Implementation Plan

Rewritten 2026-07-21 against the actual codebase. The previous revision (May 2026) was
written for a module layout and sync design that no longer exist; every path and every
sync-related step in it was wrong. See "What changed since the May revision" at the end.

Design source: `Budgets.html` in the Claude Design project — inventory mirrored in
[design/README.md](design/README.md).

## Scenarios (user-facing)

- Create a budget on one category, several categories, or all expenses (general budget)
- See progress: spent / limit / %, remaining, days left
- See daily average and end-of-period forecast
- Get a warning at `alertPercent` (default 80%) and an over-budget state
- Drill into the transactions inside the budget window
- Edit limit, period, categories, alert threshold
- Toggle rollover (carry leftover into the next period)
- Archive a budget (hidden from the main list, kept in the DB)

## Screens

- `BudgetsScreen` — list of budget cards
- `BudgetDetailsScreen` — progress ring, stat tiles, alert banner, transaction list
- `EditBudgetScreen` — create / edit form

## Decisions (locked)

| # | Question | Decision |
|---|---|---|
| 1 | Workspace scope | Per-workspace, like categories/transactions. Dual-written + synced. |
| 2 | Currency | Workspace base currency only for v1. Transactions in other currencies are skipped and surfaced with a "mixed currency" badge. No conversion. |
| 3 | Category filter | `categoryIds: List<CategoryId>`, stored as CSV. Empty = all expense categories. Income categories never counted. |
| 4 | "Spent" definition | Sum of `Transaction.money` where `type == EXPENSE`, `transferId == null`, `status == ACTUAL`, `currencyCode == workspace.baseCurrency`, `categoryId ∈ budget.categoryIds` (or any expense category when the list is empty), and `operationDate ∈ [windowStart, windowEnd)`. |
| 5 | Period boundaries | Computed client-side from `startDate + period`; no stored `endDate`. WEEKLY anchors on `startDate.dayOfWeek`, MONTHLY on `startDate.dayOfMonth` (clamped on short months), YEARLY on (month, day). |
| 6 | Rollover | Leftover (`limit − spent`, when positive) is added to the next period's effective limit. Derived at read time from the previous window, not a stored ledger entity. |
| 7 | Archive vs delete | `isActive: Boolean` for archive. `delete()` stays as a real delete for the account-purge path — `firestore.rules` allows delete only for the workspace owner. |
| 8 | Alerts | Pure UI signal in v1. No push, no scheduled work. |
| 9 | Timestamps | Domain keeps `Instant`, entity keeps `Long` epoch-ms — exactly what `Category` does today. **The May revision's decision to switch the domain to `Long` was wrong** and is dropped. |
| 10 | Forecast in v1 | **In scope.** The mockup's stat tiles (daily average, projected end-of-period total, per-day remaining) ship in v1. |
| 11 | Sync timing | **Sync lands in the same batch as the feature**, not as a follow-up. |

## Status quo

| Layer | State | File |
|---|---|---|
| Domain model | partial — no `workspaceId`, no `rollover` | [Budget.kt](../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/model/Budget.kt) |
| Domain ID | done | [BudgetId.kt](../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/primitives/BudgetId.kt) |
| Domain repo | partial — no `getByWorkspaceId`, no `setActive` | [BudgetRepository.kt](../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/repositories/BudgetRepository.kt) |
| Use cases | missing — all of them | — |
| Period helpers | missing | — |
| Room entity | partial — no `workspaceId`, no FK, no index, no `rollover` | [BudgetEntity.kt](../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/db/entity/BudgetEntity.kt) |
| Room DAO | partial — no `getByWorkspaceId`, no `upsertAll` | [BudgetDao.kt](../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/db/dao/BudgetDao.kt) |
| Repo impl | no outbox dual-write — plain DAO calls | [BudgetRepositoryImpl.kt](../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/BudgetRepositoryImpl.kt) |
| `SyncCollection.BUDGETS` | **already exists** | [SyncCollection.kt](../sync/api/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/api/SyncCollection.kt) |
| `SyncEntityTypes.BUDGET` | missing | [SyncEntityTypes.kt](../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/sync/SyncEntityTypes.kt) |
| `BudgetDoc` wire DTO | missing | [RemoteDtos.kt](../data-remote/src/commonMain/kotlin/com/georgeci/moneysurfer/data/remote/RemoteDtos.kt) |
| DTO mappers | missing | [SyncDtoMappers.kt](../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/SyncDtoMappers.kt) |
| `BudgetSyncPlugin` | missing | `sync-surfer/.../data/sync/plugin/` |
| Firestore rules | collection wired, but **no shape guard** | [firestore.rules](../firestore.rules) around line 397 |
| Feature module | missing entirely — no `feature/budget` | — |
| Navigation routes | missing | `navigation/` |
| UIKit components | `SurferBudgetsWidget` exists but is unused and does not match the design — **delete it** | [SurferBudgetsWidget.kt](../uikit/src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/widgets/SurferBudgetsWidget.kt) |

---

## Phase 0 — Domain + Room

`Budget` gains `workspaceId: WorkspaceId` and `rollover: Boolean = false`; timestamps stay
`Instant`. New `domain/util/BudgetPeriodWindow.kt`:

```kotlin
data class BudgetPeriodWindow(val startInclusive: LocalDate, val endExclusive: LocalDate)
fun Budget.currentWindow(today: LocalDate): BudgetPeriodWindow
fun Budget.windowContaining(date: LocalDate): BudgetPeriodWindow
fun Budget.previousWindow(window: BudgetPeriodWindow): BudgetPeriodWindow
```

Pure functions, no `Clock` dependency — `today` is passed in.

`BudgetEntity` mirrors `CategoryEntity`: add `workspaceId: String` with a `ForeignKey` to
`WorkspaceEntity` and `Index("workspaceId")`, plus `rollover: Boolean` with
`defaultValue = "0"`. `createdAt` / `updatedAt` are already `Long` — leave them.

`BudgetDao` adds `getByWorkspaceId(wid: String): Flow<List<BudgetEntity>>` and
`@Upsert suspend fun upsertAll(entities: List<BudgetEntity>)`.

DB: bump `MONEY_SURFER_DB_VERSION`, additive migration. Existing rows get `workspaceId = ''`
— they belong to no workspace and will not appear in any list. Acceptable: budgets have
never been reachable from the UI, so no user has any.

## Phase 1 — Repository dual-write

`BudgetRepository` adds `getByWorkspaceId(workspaceId: WorkspaceId): Flow<List<Budget>>` and
`setActive(id: BudgetId, isActive: Boolean)`.

`BudgetRepositoryImpl` is rewritten to mirror
[CategoryRepositoryImpl](../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/CategoryRepositoryImpl.kt)
one-to-one: inject `OutboxEnqueuer` + `ClockUseCase`, call `enqueueUpsert` after every
insert/update, `enqueueDelete` after delete, preserve `createdAt` on update and stamp
`updatedAt = clock.now()`.

## Phase 2 — Sync

Everything here follows the plugin architecture, using
[CategorySyncPlugin](../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/plugin/CategorySyncPlugin.kt)
as the reference implementation.

1. `SyncEntityTypes.BUDGET = "BUDGET"`.
2. `SyncPullPriorities.BUDGETS = 50` — after `CATEGORIES` (30), since budgets reference
   category ids. There is no Room FK on `categoryIds` (it is a CSV column), so this is
   ordering hygiene rather than a hard constraint.
3. `BudgetDoc` in `RemoteDtos.kt`: `name`, `categoryIds` (list), `amount`, `period`,
   `startDate`, `rollover`, `alertPercent`, `isActive`, `createdAt`, `updatedAt`,
   `deletedAt`, `clientVersionCode`. Note the wire shape uses a real list for
   `categoryIds` even though Room stores CSV — the mapper converts.
4. `toDoc()` / `toEntity()` in `SyncDtoMappers.kt`.
5. `BudgetSyncPlugin`, annotated `@Single(binds = [SyncEntityPlugin::class])`, with
   `entityType = SyncEntityTypes.BUDGET`, `firestoreCollectionName = SyncCollection.BUDGETS`,
   tombstone push on delete, and LWW conflict resolution through `ConflictResolver`.
6. `firestore.rules`: add `hasValidBudgetShape()` and wire it into `/budgets/{bid}` for
   create/update. Delete the stale comment above that block stating budgets have no wire DTO.
7. Rules tests in `firestore-tests/` (`npm test`, needs JDK 21 on PATH).
8. `firestore.indexes.json` — add an index only if a query demands one.

## Phase 3 — Use cases

`domain/usecase/`: create, update, delete, archive, `GetBudgetsUseCase`,
`GetBudgetProgressUseCase`, `GetBudgetTransactionsUseCase`.

`BudgetProgress` carries: `spent`, `effectiveLimit` (limit + rollover carry), `remaining`,
`percent`, `state` (`OK` / `WARN` / `OVER`), `daysLeft`, `dailyAverage`,
`projectedTotal`, `perDayRemaining`, `hasMixedCurrency`.

Forecast maths, per the mockup: `dailyAverage = spent / elapsedDays`,
`projectedTotal = dailyAverage * periodDays`, `perDayRemaining = remaining / daysLeft`.
`periodDays` is WEEKLY 7 / MONTHLY 30 / YEARLY 365. Guard every division against zero.

Rollover reads the previous window's leftover via the same progress calculation.

## Phase 4 — `feature/budget` module

New Gradle module, modelled on `feature/account`. Screens + MVI view models for list,
details, and edit. Navigation routes in `navigation/`: `Budgets`, `BudgetDetails(id)`,
`BudgetCreation`, `BudgetEdit(id)`.

## Phase 5 — UIKit components

Delete `SurferBudgetsWidget.kt` — unused, and its row layout does not match the design.
Add, per the mockup:

- `SurferBudgetProgressBar` — fill capped at 100%, tick at `alertPercent`
- `SurferBudgetRing` — donut, same status colours
- `SurferBudgetCard` — stacked category bubbles (max 3, then `+N`), status pill, `spent of limit`, remaining, progress, footer
- `SurferPeriodSegmented`, `SurferCategoryChipGrid` (with an "All categories" chip)
- `SurferPercentStepper` (50..100, step 5), `SurferStatTile`, `SurferAlertBanner`

Status colours: OK → `primary`; WARN → amber; OVER → `error`.

## Phase 6 — Strings and tests

All user-facing strings ship in Russian and English from the start. Unit tests are kotest
`StringSpec`; period-window and progress maths get the heaviest coverage (short months,
leap years, zero-length windows, rollover chains).

---

## What actually shipped in phases 3–6 (issue #243)

Deltas from the plan above, all deliberate:

- **One create/edit route, not two.** `Route.BudgetCreation(budgetId: String?)` covers both,
  matching `AccountCreation` / `CategoryCreation`. A separate `BudgetEdit(id)` would have been
  a second serializer entry pointing at the same screen.
- **No `SurferPeriodSegmented`.** `SurferSegmentedControl` already does exactly this; the edit
  screen passes it `BudgetPeriod.entries`.
- **Entry point is Settings → Budgets.** The navigation suite already carries five top-level
  destinations, so `Route.Budgets` is a pushed route rather than a sixth tab.
- **`BudgetProgress` also carries `rolloverCarry` and `currency`** — the carry so the details
  screen can say where the extra headroom came from, the currency so the UI formats money
  without a second workspace lookup.
- **Rollover is one window deep.** Chaining every window back to `startDate` would make opening
  the list cost more as a budget ages, for a number the user stopped tracking periods ago.
- **Amber warn colour** lives in `AppColors.Warning` / `SemanticColors.warning`, light and dark.

## What changed since the May revision

- Module `data/` no longer exists — it is `data-local/`, with `data-remote/` and
  `sync-surfer/` split out.
- `sync/.../api/SyncEntityType.kt` and the `PUSH_BUDGETS` / `PULL_BUDGETS` sync-step
  enums never existed in the current tree. Sync is plugin-based: implement
  `SyncEntityPlugin`, register it with Koin, done.
- The DB version is the constant `MONEY_SURFER_DB_VERSION`, not a literal.
- Decision 4 was written against `TransactionType.REGULAR`. The enum is
  `INCOME | EXPENSE | OPENING_BALANCE`. Transfers are not a type — they are pairs of
  transactions sharing a `transferId`, so budget spend must exclude `transferId != null`.
- Decision 9 (domain timestamps → `Long`) contradicted `Category`, which uses `Instant`
  in the domain and `Long` in the entity. Dropped.
- Forecast tiles moved from post-v1 into v1.
