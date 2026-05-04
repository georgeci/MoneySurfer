# 2. Budgets / Бюджеты — Implementation Plan

## Сценарии (user-facing)

- Create budget on single category
- Create budget across multiple categories
- Create general budget (no category filter — all expenses)
- View budget progress (spent / limit / %)
- View remaining amount
- Get warning at `alertPercent` threshold (default 80%)
- Drill into transactions inside budget window
- Edit limit
- Toggle rollover (carry leftover to next period)
- Disable / archive budget

## Экраны

- `BudgetsScreen` — list, per-row progress
- `BudgetDetailsScreen` — header with progress ring + transaction list
- `EditBudgetScreen` — create / edit form

## Decisions (locked)

| # | Question | Decision |
|---|---|---|
| 1 | Workspace scope | **Per-workspace**, like categories/transactions. Budget belongs to one workspace, dual-written + synced. |
| 2 | Currency | **Workspace base currency only** for v1. Mixed-currency tx converted with `Workspace.baseCurrency` rate at calc time — out of scope; v1 simply sums tx whose `currencyCode == workspace.baseCurrency`, skips rest with a "mixed currency" badge. |
| 3 | Category filter shape | **`categoryIds: List<CategoryId>`** in domain (not `List<String>`). Empty list = "all expense categories" (general budget). Income categories never counted. |
| 4 | "Spent" definition | Sum of `Transaction.money` where `type == REGULAR`, `category ∈ budget.categoryIds` (or any expense if empty), `timestamp ∈ [periodStart, periodEnd)`, in workspace base currency. Transfers excluded. |
| 5 | Period boundaries | Computed client-side from `startDate + period`. WEEKLY = startDate.dayOfWeek as anchor. MONTHLY = `startDate.dayOfMonth` as anchor (clamped on short months). YEARLY = (month, day) anchor. Period rolls forward automatically — no stored `endDate`. |
| 6 | Rollover semantics | Leftover (limit − spent, if positive) added to next period's effective limit. Stored as derived state, NOT a separate ledger entity. Computed from history of tx in prior periods at read time. |
| 7 | Soft-delete vs archive | **`isActive: Boolean`** stays — archived budgets hidden from main list, kept in db (no physical delete). Mirrors Firestore `delete: false` rule (rules already in place at `firestore.rules:126`). |
| 8 | Alert mechanism | Pure UI signal in v1. No push, no scheduled work. ViewModel emits `Effect.AlertCrossed(budgetId)` once per session when computed `progress >= alertPercent`. |
| 9 | `createdAt` / `updatedAt` | Stored as `Long` epoch-ms (codebase convention — see [WorkspaceMember](domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/model/WorkspaceMember.kt), [Category](domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/model/Category.kt)). User draft used `Instant` — rejected for consistency. |
| 10 | Rollover persistence | New field `rollover: Boolean` (default `false`), additive Room migration. |

Implication: scope = workspace-scoped CRUD with dual-write + sync, base-currency arithmetic, in-app progress + threshold UI. Multi-currency conversion, push notifications, predictive forecasts deferred.

---

## Status quo (audit)

Domain model + Room layer exist as scaffolding; everything else missing.

| Layer | State | Files |
|---|---|---|
| Domain model | ⚠️ partial — missing `workspaceId`, `createdAt`, `updatedAt`, `rollover` | [domain/.../model/Budget.kt](domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/model/Budget.kt) |
| Domain ID | ✅ `BudgetId` value class with `Companion.uuid()` | [domain/.../primitives/BudgetId.kt](domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/primitives/BudgetId.kt) |
| Domain repo iface | ⚠️ no `getByWorkspaceId`, no soft-delete (`setActive`) | [domain/.../repositories/BudgetRepository.kt](domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/repositories/BudgetRepository.kt) |
| Domain errors | ❌ | — |
| Use cases | ❌ all of CRUD + GetBudgetProgress + GetBudgetTransactions | [domain/.../usecase/](domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/usecase/) |
| Period helpers | ❌ no `BudgetPeriodWindow` util | — |
| Room entity | ⚠️ no `workspaceId`, no FK, no `updatedAt`, no `rollover`, no indices | [data/.../entity/BudgetEntity.kt](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/db/entity/BudgetEntity.kt) |
| Room DAO | ⚠️ no `getByWorkspaceId`, no `upsertAll` | [data/.../dao/BudgetDao.kt](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/db/dao/BudgetDao.kt) |
| DB version | 14 → bump to 15 (additive migration) | [data/.../db/MoneySurferDatabase.kt](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/db/MoneySurferDatabase.kt) |
| Repo impl | ⚠️ NO outbox dual-write — pure DAO calls | [data/.../repository/BudgetRepositoryImpl.kt](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/BudgetRepositoryImpl.kt) |
| Sync DTO | ❌ `BudgetDoc` | [data/.../sync/SyncDtos.kt](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/SyncDtos.kt) |
| Sync mappers | ❌ `toDoc` / `toEntity` | [data/.../sync/SyncDtoMappers.kt](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/SyncDtoMappers.kt) |
| `SyncEntityType` | ❌ `BUDGET` enum value (explicitly excluded — see comment lines 4-8) | [sync/.../api/SyncEntityType.kt](sync/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/api/SyncEntityType.kt) |
| Sync steps | ❌ `PUSH_BUDGETS`, `PULL_BUDGETS`, `CLEAR_BUDGETS` | [domain/.../model/SyncProgress.kt](domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/model/SyncProgress.kt) |
| Sync push/pull | ❌ in `WorkspaceSyncRepositoryImpl` | [data/.../repository/WorkspaceSyncRepositoryImpl.kt](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/WorkspaceSyncRepositoryImpl.kt) |
| Outbox upload | ❌ `WORKSPACE_INVITE`-style branch | [data/.../sync/UploadPendingChangesUseCaseImpl.kt](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/UploadPendingChangesUseCaseImpl.kt) |
| Pull integration | ❌ in `PullRemoteChangesUseCaseImpl` | [data/.../sync/PullRemoteChangesUseCaseImpl.kt](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/PullRemoteChangesUseCaseImpl.kt) |
| Firestore rules | ✅ `/budgets/{bid}` already wired (lines 126-130) | [firestore.rules](firestore.rules) |
| Firestore indexes | ❌ | [firestore.indexes.json](firestore.indexes.json) |
| Shared (feature) layer | ❌ no screens, VMs, navigation | [shared/.../](shared/src/commonMain/kotlin/com/georgeci/moneysurfer/shared/) |
| UIKit components | ❌ no `SurferBudgetRow`, `SurferProgressBar`, `SurferBudgetCard` | [uikit/.../components/](uikit/src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/components/) |
| Dual-write spec | ❌ | — |

---

## Phase 0 — Domain model upgrade

Goal: align `Budget` with workspace-scoped + dual-write conventions before touching sync.

### domain
- [domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/model/Budget.kt](domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/model/Budget.kt) — add fields:
  ```kotlin
  data class Budget(
      val id: BudgetId = BudgetId.uuid(),
      val workspaceId: WorkspaceId,
      val name: String,
      val categoryIds: List<CategoryId>,   // empty = all expense categories
      val amount: Money,                   // limit per period, in workspace base currency
      val period: BudgetPeriod = BudgetPeriod.MONTHLY,
      val startDate: LocalDate,
      val rollover: Boolean = false,
      val alertPercent: Int = 80,
      val isActive: Boolean = true,
      val createdAt: Long,
      val updatedAt: Long,
  )
  ```
- New file `domain/util/BudgetPeriodWindow.kt`:
  ```kotlin
  data class BudgetPeriodWindow(val startInclusive: LocalDate, val endExclusive: LocalDate)
  fun Budget.currentWindow(today: LocalDate): BudgetPeriodWindow
  fun Budget.windowContaining(date: LocalDate): BudgetPeriodWindow
  fun Budget.previousWindow(window: BudgetPeriodWindow): BudgetPeriodWindow
  ```
  Anchor day = `startDate.dayOfMonth` for MONTHLY, clamp to `min(anchor, lastDayOfMonth)`. WEEKLY uses `startDate.dayOfWeek`. Pure functions, no `Clock` dep.

### data
- [data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/db/entity/BudgetEntity.kt](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/db/entity/BudgetEntity.kt) — extend:
  ```kotlin
  @Entity(
      tableName = "budgets",
      foreignKeys = [ForeignKey(WorkspaceEntity, parentColumns=["id"], childColumns=["workspaceId"])],
      indices = [Index("workspaceId")],
  )
  data class BudgetEntity(
      @PrimaryKey @ColumnInfo("id") val id: String,
      @ColumnInfo("workspaceId") val workspaceId: String,
      @ColumnInfo("name") val name: String,
      @ColumnInfo("categoryIds") val categoryIds: String,   // CSV
      @ColumnInfo("amount") val amount: Long,
      @ColumnInfo("period") val period: String,
      @ColumnInfo("startDate") val startDate: String,        // ISO LocalDate
      @ColumnInfo("rollover", defaultValue = "0") val rollover: Boolean = false,
      @ColumnInfo("alertPercent") val alertPercent: Int,
      @ColumnInfo("isActive") val isActive: Boolean,
      @ColumnInfo("createdAt", defaultValue = "0") val createdAt: Long = 0L,
      @ColumnInfo("updatedAt", defaultValue = "0") val updatedAt: Long = 0L,
  )
  ```
- [data/.../db/dao/BudgetDao.kt](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/db/dao/BudgetDao.kt) — add:
  - `fun getByWorkspaceId(wid: String): Flow<List<BudgetEntity>>`
  - `@Upsert suspend fun upsertAll(entities: List<BudgetEntity>)`
- [data/.../db/MoneySurferDatabase.kt](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/db/MoneySurferDatabase.kt) — bump `version = 15`, add additive `Migration(14, 15)` (add `workspaceId` NOT NULL with default '' for existing rows — orphan rows stay queryable but won't match any workspace; document as intended for the dev-only state).

### tests
- New `domain/src/commonTest/.../util/BudgetPeriodWindowTest.kt`:
  - month-anchor clamp on Feb of leap vs non-leap years
  - YEARLY anchor on Feb 29
  - week boundary across DST (period anchored to dayOfWeek, not absolute hours)
  - `previousWindow` returns previous month/week/year correctly

---

## Phase 1 — Sync wiring

Goal: get `BUDGET` into the sync pipeline mirroring `CATEGORY`.

### sync api
- [sync/.../api/SyncEntityType.kt](sync/src/commonMain/kotlin/com/georgeci/moneysurfer/sync/api/SyncEntityType.kt) — add `BUDGET` (delete the explicit-omission comment for budgets, leave it for `RECURRING_RULE`):
  ```kotlin
  enum class SyncEntityType {
      USER, WORKSPACE, WORKSPACE_MEMBER, WORKSPACE_INVITE,
      WORKSPACE_REF, ACCOUNT, CATEGORY, TRANSACTION, BUDGET,
  }
  ```
  Find every `when (type)` over the enum (push dispatch in [UploadPendingChangesUseCaseImpl](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/UploadPendingChangesUseCaseImpl.kt), pull dispatch in [PullRemoteChangesUseCaseImpl](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/PullRemoteChangesUseCaseImpl.kt), any per-type rate limiter / metrics) — exhaustive `when` will fail compile, fix each.

### sync DTO
- [data/.../sync/SyncDtos.kt](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/SyncDtos.kt) — append:
  ```kotlin
  @Serializable
  internal data class BudgetDoc(
      val name: String = "",
      val categoryIds: List<String> = emptyList(),    // proper array, not CSV
      val amount: Long = 0L,                          // minor units
      val period: String = "MONTHLY",
      val startDate: String = "",                     // ISO LocalDate
      val rollover: Boolean = false,
      val alertPercent: Int = 80,
      val isActive: Boolean = true,
      val createdAt: Long = 0L,
      val updatedAt: Long = 0L,
      val deletedAt: Long? = null,
      val clientVersionCode: Int = 1,
  )
  ```
  Note: `categoryIds` is a Firestore array — do NOT carry the Room CSV form across the wire.

### sync mappers
- [data/.../sync/SyncDtoMappers.kt](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/SyncDtoMappers.kt) — append `BudgetEntity.toDoc()` and `BudgetDoc.toEntity(id, workspaceId)` (CSV ↔ List<String> conversion happens inside the mappers).

### sync steps
- [domain/.../model/SyncProgress.kt](domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/model/SyncProgress.kt) — extend `SyncStep` enum with `PUSH_BUDGETS`, `PULL_BUDGETS`, `CLEAR_BUDGETS`. Order:
  ```
  PUSH_TRANSACTIONS, PUSH_BUDGETS,
  ...
  PULL_TRANSACTIONS, PULL_BUDGETS,
  ...
  CLEAR_TRANSACTIONS, CLEAR_BUDGETS, CLEAR_INVITES, CLEAR_MEMBERS, CLEAR_WORKSPACE
  ```
  Budgets after transactions = read-side correctness: progress UI prefers fresh tx over fresh budgets if a partial pull happens. No FK gating either direction (budget references categories but Firestore rules don't enforce, and orphaned categoryIds are tolerated by spent-calc).
- [domain/src/commonTest/.../model/SyncStepOrderTest.kt](domain/src/commonTest/kotlin/com/georgeci/moneysurfer/domain/model/SyncStepOrderTest.kt) — update golden order, add the three new entries.

### sync push/pull
- [data/.../repository/WorkspaceSyncRepositoryImpl.kt](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/WorkspaceSyncRepositoryImpl.kt):
  - DI: add `private val budgetDao: BudgetDao` to ctor.
  - In `syncWorkspace`: emit + run `PUSH_BUDGETS` after `PUSH_TRANSACTIONS`, `PULL_BUDGETS` after `PULL_TRANSACTIONS`.
  - In `clearWorkspace`: emit + run `CLEAR_BUDGETS` between `CLEAR_TRANSACTIONS` and `CLEAR_INVITES`.
  - Implementations mirror `pushCategories` / `pullCategories` / `deleteCollection(wid, "budgets")`. Firestore path: `workspaces/{wid}/budgets/{bid}`.

### outbox + pull integration
- [UploadPendingChangesUseCaseImpl](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/UploadPendingChangesUseCaseImpl.kt) — add `BUDGET` branch (deserialize `BudgetDoc`, write to `workspaces/{wid}/budgets/{id}`).
- [PullRemoteChangesUseCaseImpl](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/PullRemoteChangesUseCaseImpl.kt) — add `BUDGET` branch (read `budgets` subcollection, decode `BudgetDoc`, upsert `budgetDao`).

### firestore.indexes.json
Add composites — only what queries actually need:
- `budgets`: `workspaceId ASC, isActive ASC, startDate DESC` (list active budgets in a workspace, newest first).

Skip `(workspaceId, period)` and per-category composites until a query that needs them lands. v1 reads all budgets per workspace and filters client-side.

### firestore.rules
**No change** — existing block at [firestore.rules:126](firestore.rules) already gates `/budgets/{bid}` correctly:
```
allow read: if isMember(wid);
allow create, update: if isMember(wid) && hasValidClientVersion();
allow delete: if false;
```

---

## Phase 2 — Repo dual-write

Goal: every mutating call writes Room then enqueues outbox, mirroring [CategoryRepositoryImpl](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/CategoryRepositoryImpl.kt).

### domain repo iface
- [domain/.../repositories/BudgetRepository.kt](domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/repositories/BudgetRepository.kt) — extend:
  ```kotlin
  fun getAll(): Flow<List<Budget>>
  fun getByWorkspaceId(wid: WorkspaceId): Flow<List<Budget>>
  suspend fun getById(id: BudgetId): Budget?
  suspend fun insert(budget: Budget)
  suspend fun update(budget: Budget)
  suspend fun setActive(id: BudgetId, active: Boolean)   // archive / restore
  suspend fun delete(id: BudgetId)                       // local-only purge, no remote (rules forbid)
  ```
  v1 keeps `delete` for the wipe-demo path and tests; production UI calls `setActive(false)` instead.

### data repo impl
- [data/.../repository/BudgetRepositoryImpl.kt](data/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/BudgetRepositoryImpl.kt) — rewrite:
  ```kotlin
  @Single(binds = [BudgetRepository::class])
  class BudgetRepositoryImpl(
      private val dao: BudgetDao,
      private val outboxEnqueuer: OutboxEnqueuer,
  ) : BudgetRepository {
      override suspend fun insert(budget: Budget) {
          val entity = budget.toEntity().copy(updatedAt = Clock.System.now().toEpochMilliseconds())
          dao.insert(entity)
          enqueueUpsert(entity, MutationOperation.INSERT)
      }
      override suspend fun update(budget: Budget) { ... INSERT → UPDATE }
      override suspend fun setActive(id: BudgetId, active: Boolean) {
          val existing = dao.getById(id.value) ?: return
          val updated = existing.copy(isActive = active, updatedAt = now())
          dao.update(updated)
          enqueueUpsert(updated, MutationOperation.UPDATE)
      }
      override suspend fun delete(id: BudgetId) {
          val existing = dao.getById(id.value) ?: return
          dao.delete(id.value)
          // No outbox — Firestore rule denies delete. Local-only purge is dev/test only.
      }
      private suspend fun enqueueUpsert(entity: BudgetEntity, op: MutationOperation) { ... }
  }
  ```
  CSV ↔ `List<CategoryId>` conversion stays in the entity-↔-domain mappers (bottom of file, already there).

### tests
- New `data/src/commonTest/.../repository/BudgetRepositoryDualWriteSpec.kt` cloned from [WorkspaceMemberRepositoryDualWriteSpec.kt](data/src/commonTest/kotlin/com/georgeci/moneysurfer/data/repository/WorkspaceMemberRepositoryDualWriteSpec.kt). Cases:
  - `insert` → DAO row + outbox INSERT with payload JSON shape
  - `update` → DAO row updated + outbox UPDATE
  - `setActive(false)` → DAO row flipped + outbox UPDATE
  - `delete` → DAO row gone + NO outbox row (rules forbid)
  - demo session (outbox disabled) → DAO write only, no enqueue

---

## Phase 3 — Domain use cases

Files under `domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/usecase/`. Pattern: `Either<BudgetError, T>` with `arrow.core.raise.either`.

### errors
- `usecase/BudgetError.kt`:
  ```kotlin
  sealed interface BudgetError {
      data object NoCurrentWorkspace : BudgetError
      data object NameBlank : BudgetError
      data object AmountNotPositive : BudgetError
      data class AlertPercentOutOfRange(val value: Int) : BudgetError    // not in 1..100
      data class StartDateInvalid(val date: LocalDate) : BudgetError      // future-dated > 1y, or pre-1970
      data object BudgetNotFound : BudgetError
      data class LocalWriteFailed(val cause: Throwable) : BudgetError
  }
  ```

### use cases
- `CreateBudgetUseCase` — `Params(name, categoryIds, amount, period, startDate, alertPercent, rollover)`. Reads `currentWorkspaceId` from `SessionPointers`, validates inputs, builds `Budget` with `id = BudgetId.uuid()`, `createdAt = now`, calls `repo.insert`. Returns `Right(BudgetId)`.
- `UpdateBudgetUseCase` — `Params(id, …same fields…)`. Validates, `getById` → not-found error, copies fields, `repo.update`.
- `SetBudgetActiveUseCase` — `Params(id, active)`. Owner-or-editor check via `viewerRoleFor(wid)`; viewers blocked.
- `DeleteBudgetUseCase` — local purge only (dev/test). Not exposed in UI.
- `GetBudgetsForWorkspaceUseCase` — wraps `repo.getByWorkspaceId(currentWid)` (`isActive == true` filter optional flag).
- `GetBudgetByIdUseCase` — `repo.getById`.
- `GetBudgetProgressUseCase` — pure-domain calc. Takes `Budget`, `today: LocalDate`, `transactions: Flow<List<Transaction>>` (from `TransactionRepository.getByWorkspaceId`). Returns `Flow<BudgetProgress>`:
  ```kotlin
  data class BudgetProgress(
      val budget: Budget,
      val window: BudgetPeriodWindow,
      val effectiveLimit: Money,    // amount + rollover-from-previous (if rollover && prevWindow.leftover > 0)
      val spent: Money,
      val remaining: Money,         // can be negative
      val percent: Int,             // (spent / effectiveLimit) * 100, clamped 0..999
      val mixedCurrencyTxCount: Int, // transactions skipped due to base-currency mismatch
  )
  ```
  Filtering: `tx.type == REGULAR && tx.timestamp ∈ window` (timestamp converted to LocalDate via workspace tz, v1 = system default), `tx.categoryId ∈ budget.categoryIds || budget.categoryIds.isEmpty() && tx.categoryType == EXPENSE`, `tx.currencyCode == workspace.baseCurrency`.
- `GetBudgetTransactionsUseCase` — `Params(budgetId)`. Returns `Flow<List<Transaction>>` filtered to current period for the details screen.

### tests (`domain/src/commonTest/.../usecase/`)
- Per use case: happy path + each error variant. New `BudgetFixtures.kt` in [domain-test-fixtures](domain-test-fixtures/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/fixtures/) — factory for `Budget`, `BudgetProgress`.
- `GetBudgetProgressUseCaseTest` cases: empty tx → 0%; partial → linear %; over-limit → percent > 100, remaining negative; rollover with leftover → effectiveLimit boosted; rollover with overspend → effectiveLimit clamped to base amount (negative leftover not subtracted); mixed-currency tx → counted in `mixedCurrencyTxCount`, not in `spent`; transfers (`type != REGULAR`) excluded; income tx in expense budget excluded.

---

## Phase 4 — Shared (feature) layer

Module path: `shared/src/commonMain/kotlin/com/georgeci/moneysurfer/shared/budgets/`.

### list — `BudgetsScreen` + `BudgetsViewModel`
- State `sealed interface BudgetsState` with `@optics`:
  - `Loading(workspaceId)`
  - `Content(workspaceId, budgets: List<BudgetUi>, includeArchived: Boolean, busy: Boolean)`
- `BudgetUi(id, name, percent, spentMajor, limitMajor, currency, isActive, isAlert)` — flat record consumed by row composable.
- `observeBudgets()`: `combine(GetBudgetsForWorkspaceUseCase(wid), TransactionRepository.getByWorkspaceId(wid), Workspace.baseCurrency)` → for each budget run `GetBudgetProgressUseCase` synchronously (cheap, list size small) and project to `BudgetUi`.
- Events: `OnAddClick`, `OnRowClick(id)`, `OnToggleArchived`.
- Effects: `OpenEdit(id?)`, `OpenDetails(id)`, `ShowError(BudgetError)`, `AlertCrossed(BudgetId)` (emitted once per session per budget when threshold crossed in this collect cycle — tracked in a `MutableSet<BudgetId>` inside the VM).

### details — `BudgetDetailsScreen` + `BudgetDetailsViewModel`
- State: `Loading(id)` / `Content(progress: BudgetProgress, transactions: List<Transaction>)` / `NotFound`.
- `combine(GetBudgetByIdUseCase, GetBudgetProgressUseCase, GetBudgetTransactionsUseCase)`.
- Events: `OnEdit`, `OnArchive`, `OnRestore`, `OnTransactionClick(txId)`.

### edit — `EditBudgetScreen` + `EditBudgetViewModel`
- State: `Editing(form: BudgetForm, busy: Boolean, error: BudgetError?)`.
- `BudgetForm(name, selectedCategoryIds: Set<CategoryId>, amountInput: String, period, startDate, rollover, alertPercent)`.
- Events: `OnNameChange`, `OnAmountChange`, `OnCategoryToggle(id)`, `OnPeriodChange`, `OnStartDateChange`, `OnRolloverToggle`, `OnAlertPercentChange`, `OnSubmit`, `OnCancel`.
- On `OnSubmit`: route through `CreateBudgetUseCase` (id == null) or `UpdateBudgetUseCase`. On `Right` → `Effect.Saved(BudgetId)` and pop.

### navigation
- Find existing nav graph (search `NavHost`/`composable(`/`Route` in `shared/`) — register routes:
  - `budgets/list`
  - `budgets/details/{budgetId}`
  - `budgets/edit?budgetId={budgetId}` (null = create)
- Wire bottom-nav / dashboard tile → `budgets/list`.

### MVI
- Follow existing `MviViewModel<State, Event, Effect>` base — see [WorkspaceMembersViewModel](shared/src/commonMain/kotlin/com/georgeci/moneysurfer/shared/workspace/members/WorkspaceMembersViewModel.kt) for the canonical shape (`@optics`, `updateState`, `postSideEffect`, `launch(onError = …)`). Annotate with `@KoinViewModel`.

### tests
- VM tests mirror the convention of `domain/src/commonTest/.../usecase/`. Drive use cases with fakes; assert state transitions + emitted effects.

---

## Phase 5 — UIKit components

Add under `uikit/src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/components/`:

- `SurferProgressBar.kt` — linear progress, 4 color states: ok (<alertPercent), warn (≥alertPercent, <100), over (≥100), inactive (archived).
- `SurferBudgetRow.kt` — name, spent / limit, % chip, progress bar, archived badge. Trailing chevron.
- `SurferBudgetCard.kt` — details-screen header: large progress ring, period range label (`Apr 1 – Apr 30`), remaining amount, alert badge.
- `SurferCategoryMultiSelect.kt` — multi-pick variant of [SurferCategoryBottomSheet](uikit/src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/components/SurferCategoryBottomSheet.kt) with chips + "All expense categories" toggle.
- `SurferPeriodSelector.kt` — segmented control for `BudgetPeriod`.
- `SurferDatePickerField.kt` — only if not already present; otherwise reuse.

Strings (shared `composeResources/values/strings.xml`) — add labels for create/edit/details/archive/rollover/alert-percent.

---

## Sequencing

| Order | Phase | Reason |
|---|---|---|
| 1 | Phase 0 | model + Room migration unblock everything; pure-additive, no behavior change |
| 2 | Phase 1 | sync wiring needs Phase 0's `workspaceId` field on entity |
| 3 | Phase 2 | repo dual-write needs Phase 1's `SyncEntityType.BUDGET` |
| 4 | Phase 3 | use cases need Phase 2's repo; pure-domain, no UI needed |
| 5 | Phase 4 | feature layer needs Phase 3 use cases |
| 6 | Phase 5 | uikit parallelizable with Phase 4 |

Each phase = own PR. `BudgetRepositoryDualWriteSpec` MUST be green before merging Phase 2. `SyncStepOrderTest` MUST be green before merging Phase 1.

---

## Out of scope (post-v1)

- **Multi-currency conversion** — v1 skips foreign-currency tx. When FX rates land, swap `currencyCode == base` filter for `convertTo(base, rate)` in `GetBudgetProgressUseCase`.
- **Push notifications on threshold cross** — needs background job + token plumbing. Today the alert is a one-shot UI effect.
- **Predictive forecasts** — "at current spend rate you will exceed by X". Easy add atop `GetBudgetProgressUseCase` once UX wants it.
- **Recurring budgets with end date** — current model auto-rolls forever. End-date support = additive nullable `endDate: LocalDate?` field.
- **Per-budget currency override** — design space exists (BudgetDoc has no `currencyCode`); add later as additive nullable.
- **Owner-only mutation gating** — Firestore rules let any member create/update budgets. If product wants role-gated budgets (editor minimum), tighten the rule + add `viewerRole` check in use cases.
- **Hard delete via tombstone** — not supported; rules forbid client deletes. Future cleanup needs a Cloud Function.
