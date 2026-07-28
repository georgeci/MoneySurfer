# Total Calc — Transactions × Balance × Totals

Offline-first KMP. Source of truth — `transactions`. `Account.balance` is a cache. Money is `Money` (backed by `Long minor`). No `Double/Float`.

This document piggybacks on the sync architecture in [sync.md](sync.md). Read §4.3 (Room schema) and §4.4 (Firestore schema) first — most of the cross-cutting infra (`updatedAt`, outbox, LWW conflict resolver, hard-delete locally + tombstone remotely) is already planned there.

**Scope v1:** INCOME / EXPENSE / OPENING_BALANCE only. **TRANSFER**, **ADJUSTMENT**, **multi-currency** — out of scope. **PENDING** — deferred to recurring (see §10).

**Naming:** existing fields are NOT renamed. `Transaction.money`, `Transaction.timestamp`, `Account.balance` stay as-is. Only fields strictly required for total calc are added.

---

## 1. Domain model

### Transaction

`type` is extended; `status` is added.

```kotlin
enum class TransactionType {
    INCOME, EXPENSE, OPENING_BALANCE
    // INITIAL_BALANCE is aliased to OPENING_BALANCE during migration
}
enum class TransactionStatus { ACTUAL, PLANNED }

data class Transaction(
    val id: TransactionId,
    val workspaceId: WorkspaceId,
    val accountId: AccountId,
    val money: Money,                          // existing, always >= 0
    val currencyCode: CurrencyCode,
    val categoryId: CategoryId?,
    val note: String,
    val timestamp: Long,                       // existing — business time of the operation
    val type: TransactionType,                 // extended
    val status: TransactionStatus,             // new
)
```

Invariants:
- `money.minor >= 0`. Sign is a function of `type`.
- `OPENING_BALANCE` is always `ACTUAL` and unique per account.
- `currencyCode == account.currencyCode`.

**Delete model — aligned with sync.md §4.3:**
- Locally a deleted tx is hard-deleted from the Room `transactions` table. There is no `DELETED` status and no local `deletedAt` column.
- Remotely (Firestore) a deleted tx is a tombstone (`deletedAt: Long?` on the doc, set by the push step). Tombstones drive replication so that other devices observe the delete (sync.md §4.4).
- Pull sees a tombstoned doc → hard-delete locally + decrement balance by the old impact (see §6.3).

Therefore in domain logic a deleted transaction is represented by **absence**, not by status. `TransactionStatus.DELETED` is intentionally not part of the enum.

### Account

Existing `balance: Money` stays as the cache. **No new fields** are required for total calc. Race with `recalculate` is handled by run policy (only when outbox is empty), not extra columns.

The `updatedAt: Long` column on accounts/transactions/etc. is added by sync.md §4.3 and used by the LWW conflict resolver. Total-calc logic does not depend on it directly, but the push step (§6.2) updates it whenever the tx or the account row is rewritten.

---

## 2. balanceImpact()

Pure, no IO. Returns `Money`.

```kotlin
fun Transaction.balanceImpact(): Map<AccountId, Money> {
    if (status != TransactionStatus.ACTUAL) return emptyMap()
    val signed: Money = when (type) {
        INCOME          -> money
        EXPENSE         -> -money
        OPENING_BALANCE -> money
    }
    return mapOf(accountId to signed)
}
```

`PLANNED → emptyMap()`. Deleted txs don't reach this function — they're absent.

`Money` already supports `+`, `-`, unary minus.

---

## 3. calculateBalanceDelta()

```kotlin
fun calculateBalanceDelta(
    old: Transaction?,
    new: Transaction?,
): Map<AccountId, Money> {
    val a = old?.balanceImpact().orEmpty()
    val b = new?.balanceImpact().orEmpty()
    return (a.keys + b.keys)
        .associateWith { (b[it] ?: Money.zero()) - (a[it] ?: Money.zero()) }
        .filterValues { !it.isZero() }
}
```

Cases:

| Scenario | `old` | `new` | delta |
|---|---|---|---|
| create income 100 on A | null | tx{A,+100,ACTUAL} | {A:+100} |
| create expense 100 on A | null | tx{A,-100,ACTUAL} | {A:-100} |
| update expense 100→150 (A) | tx{A,-100} | tx{A,-150} | {A:-50} |
| move expense A→B | tx{A,-100} | tx{B,-100} | {A:+100, B:-100} |
| planned→actual expense 100 | tx{A,-100,PLANNED} | tx{A,-100,ACTUAL} | {A:-100} |
| actual→planned expense 100 | tx{A,-100,ACTUAL} | tx{A,-100,PLANNED} | {A:+100} |
| delete actual income 100 | tx{A,+100,ACTUAL} | null | {A:-100} |
| repeat delete | null | null | ∅ (idempotent at the boundary) |
| create planned | null | tx{A,…,PLANNED} | ∅ |

---

## 4. applyTransactionChange()

Local-first writer. Updates Room and enqueues an outbox row in one DB transaction.

```kotlin
suspend fun applyTransactionChange(
    workspaceId: WorkspaceId,
    old: Transaction?,
    new: Transaction?,
)
```

Steps (within a Room transaction):
1. `delta: Map<AccountId, Money> = calculateBalanceDelta(old, new)`.
2. Mutate the tx row:
   - `old == null && new != null` → insert.
   - `old != null && new != null` → update (`updatedAt = now`).
   - `old != null && new == null` → hard delete.
3. For each `(accountId, d)` in `delta`: `account.balance += d`, `account.updatedAt = now`.
4. Enqueue an outbox row (sync.md §4.3 `pending_mutations`). See §6.

Guarantees:
- No partial state — both rows are written under the same Room transaction.
- Local cache reflects the change immediately (offline-first UX).
- Remote convergence happens later via outbox push (§6).

The use case calls into `TransactionRepository` and `AccountRepository`; both wrap the same `withTransaction { … }` block. `Money` lives in domain; minor units only at the column boundary.

---

## 5. recalculateAccountBalance()

```kotlin
suspend fun recalculateAccountBalance(
    workspaceId: WorkspaceId,
    accountId: AccountId,
): Money
```

1. Query: all `transactions` where `workspaceId = ws`, `accountId = A`, `status = ACTUAL`. Deleted rows are absent — no extra filter needed.
2. `sum: Money = transactions.fold(Money.zero()) { acc, tx -> acc + (tx.balanceImpact()[A] ?: Money.zero()) }`.
3. `account.balance = sum` via `set()` (NOT increment), `account.updatedAt = now`.
4. Run **only when the outbox is empty** for the workspace. Otherwise a parallel `set` and pending `increment` lose commutativity.

When: manual "Repair", post-migration, post-sync if a divergence detector fires, one-time after rolling out the feature.

---

## 6. Sync — outbox, push, pull

This section extends sync.md with transaction-specific semantics. The general outbox infra (`PendingMutationQueue`, `MutationOperation`, `pending_mutations` table, `ConflictResolver` LWW) is the same.

### 6.1. Outbox row for a transaction change

We reuse the existing `PendingMutation` shape. No new columns.

| Field | Value |
|---|---|
| `entityType` | `TRANSACTION` |
| `entityId` | `tx.id` |
| `workspaceId` | `tx.workspaceId` |
| `operation` | `INSERT` / `UPDATE` / `DELETE` |
| `payload` | JSON-serialized `TransactionDto` of the new state (for INSERT/UPDATE); for DELETE — the **last-known state** of the tx before deletion (so the push step has the workspace and account ids it needs). Tombstone `deletedAt` is set by the push step, not by the enqueuer. |

The payload does **not** carry a precomputed delta. Delta is recomputed at push time inside a Firestore transaction (§6.2). This makes the row a pure snapshot, which gives us coalescing for free (§6.4) and natural idempotency (§6.5).

### 6.2. Push step (Firestore client transaction)

Most outbox rows in the project use plain `set()` because pure CRUD is idempotent. **Transaction rows are special**: each push must atomically rewrite both the tx doc and the account doc(s), and the account write is a relative `FieldValue.increment`. Idempotency is reclaimed by recomputing delta server-side from the current Firestore state.

A specialized push handler (`FirestoreTransactionPusher`) runs:

```kotlin
firestore.runTransaction { ftx ->
    val txRef = workspaces[wsId].transactions[entityId]
    val currentDoc = ftx.get(txRef)

    val oldTx = currentDoc.toDomainOrNull()       // null if absent or tombstoned
    val newTx = when (operation) {
        INSERT, UPDATE -> payload.toDomain()      // new state
        DELETE         -> null                    // deletion
    }
    val delta = calculateBalanceDelta(oldTx, newTx)   // domain function reused as-is

    when (operation) {
        INSERT, UPDATE -> ftx.set(txRef, payload.copy(updatedAt = now))
        DELETE         -> ftx.update(txRef, mapOf(
            "deletedAt" to now,
            "updatedAt" to now,
        ))
    }
    delta.forEach { (accountId, d) ->
        ftx.update(accountRef(accountId), mapOf(
            "balance"   to FieldValue.increment(d.minor),
            "updatedAt" to now,
        ))
    }
}
```

Notes:
- `currentDoc.toDomainOrNull()` returns `null` if the doc doesn't exist or has `deletedAt != null` (tombstoned). That's why a deleted tx contributes `emptyMap()` to impact on this side as well — symmetric with the local domain rule that "deleted ≡ absent".
- `FieldValue.increment` is commutative across concurrent pushes for **different** transactions — this gives offline-burst correctness when many devices flush in parallel.
- Concurrent edits on the **same** tx serialize via the Firestore transaction (one wins, the other retries against the new state and recomputes its own delta — natural LWW with consistent balance).

### 6.3. Pull side — applying remote tx changes to the local cache

The existing pull pipeline writes tx rows via `upsertWithoutOutbox` (sync.md §4.3). For transactions we add one extra step inside the same Room transaction: maintain the local `Account.balance` cache.

```kotlin
db.withTransaction {
    pulledDocs.forEach { remote ->
        val local = txDao.findById(remote.id)
        val localTx = local?.toDomain()
        val remoteTx = if (remote.deletedAt != null) null else remote.toDomain()

        // LWW already chose remote — just apply.
        when {
            remoteTx != null -> txDao.upsertWithoutOutbox(remoteTx.toEntity())
            localTx  != null -> txDao.deleteById(remoteTx.id)            // remote tombstone
        }

        val delta = calculateBalanceDelta(localTx, remoteTx)
        delta.forEach { (acc, d) ->
            accountDao.applyDelta(acc, d.minor, now)                     // no outbox
        }
    }
    syncMeta.setCursor(...)
}
```

Important:
- This path **bypasses the outbox** — the change is already on the server. Enqueuing would loop.
- The `Money → Long minor` conversion happens at the Room column boundary inside `applyDelta`.

### 6.4. Coalescing (offline burst)

When the user makes multiple offline edits to the same tx, multiple outbox rows accumulate. Before push, the queue may collapse them: keep only the latest row for that `(entityType, entityId)` and drop earlier unpushed ones.

This is safe because:
- Each row is a full snapshot of the new state.
- The server recomputes delta from server-state to incoming-state. The path taken doesn't matter.

Coalescing is an optimization, not a correctness requirement. Absent it, replaying every row in order yields the same final server state — every `delta_k = impact(state_k) - impact(state_{k-1})` increment, telescoping to `impact(final) - impact(initial)`.

### 6.5. Idempotency

There is **no** dedicated `mutationId` ledger. Idempotency is structural:

- Push always recomputes `delta` from `currentDoc` vs incoming state inside the Firestore transaction.
- Replay of the same outbox row pushes the same target state. After the first push, `currentDoc` already matches the target → `delta == ∅` → the increment side-effect is a no-op. The `set/update` side is also a no-op state-wise (LWW resolves identically).
- For DELETE: first push sets `deletedAt`. Second push reads the tombstone (`oldTx == null` because we treat tombstones as absent) and tries to compute delta against `newTx == null` → `delta == ∅` → no-op. The `update(deletedAt = now)` is overwritten with the same wall clock value or a slightly later one — harmless under LWW.
- The `currentDoc.exists() == false` case for an UPDATE op (e.g., the doc was wiped or never existed) is treated as a CREATE and the full impact is applied. Safe.

This relies on the Firestore client transaction guaranteeing read-write atomicity. The `gitlive` Firebase SDK exposes it via `firestore.runTransaction { … }`.

### 6.6. Failure modes

| Failure | Behavior |
|---|---|
| Network down | Outbox row stays `PENDING`, retried next sync cycle. Local cache already updated — UX unaffected. |
| Firestore tx aborted (concurrent write) | SDK retries automatically; if it ultimately fails, outbox marks the row failed and bumps `attempts`. |
| Local Room tx fails before outbox enqueue | Domain change rolled back; no outbox row created. No partial state. |
| Push committed but local "mark completed" lost | Next retry pushes the same payload; server-side recompute yields `delta == ∅`. Outbox is then marked completed. |
| Tx doc missing on server but cache has stale balance | Push for an UPDATE op where `currentDoc.exists() == false` is treated as create; full impact applied. Safe. |
| Pull sees a tombstone for a tx we have no local record of | Hard delete is a no-op; `oldTx == null && newTx == null` → `delta == ∅`. No balance change. Cursor advances. |

---

## 7. Period totals

> **Superseded (issue #384).** `PeriodTotals`, `calculatePeriodTotalsFromList` and
> `CalculatePeriodTotalsUseCase` are gone. They summed minor units across currencies, counted
> transfer legs, and derived the period from `operationAt` plus the caller's timezone — three
> disagreements with the predicate budgets apply, in code no feature had wired up yet.
> `SpendAnalyticsRepository.netByMonth` is the replacement: same income/expense split, one
> `GROUP BY` in SQLite, `operationDate` as the date. The planned income/expense split has no
> consumer and was not carried over; add it back to that query when a screen asks for it. See
> [md/insights.md](insights.md).

Pure domain return shape. NOT persisted, NOT serialized, NOT pushed. Slim — only what the dashboard needs. `byCategory` / `byAccount` are separate use cases (charts, accounts breakdown).

```kotlin
data class PeriodTotals(
    val income: Money,
    val expense: Money,
    val net: Money,                            // income - expense
    val plannedIncome: Money,
    val plannedExpense: Money,
)

suspend fun calculatePeriodTotals(
    workspaceId: WorkspaceId,
    fromDate: LocalDate,
    toDate: LocalDate,
): PeriodTotals
```

Per-tx, with period filter (deleted rows are absent — no extra filter needed):

| type | status | contribution |
|---|---|---|
| INCOME | ACTUAL | `income += money` |
| EXPENSE | ACTUAL | `expense += money` |
| INCOME | PLANNED | `plannedIncome += money` |
| EXPENSE | PLANNED | `plannedExpense += money` |
| OPENING_BALANCE | ACTUAL | skip (not income) |

`net = income - expense`.

**Period filter:** `transaction.timestamp` (business time of the operation). At the boundary — `Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(workspaceTz).date in [fromDate..toDate]`. No separate `date` field.

---

## 8. Invariants (verifiable)

| # | Invariant | Test |
|---|---|---|
| I1 | `sum(impacts)` over all ACTUAL rows for an account == `Account.balance` after a series of `applyTransactionChange` | property test (fuzz) |
| I2 | planned→actual: `delta == new.impact` | unit |
| I3 | actual→planned: `delta == -old.impact` | unit |
| I4 | delete actual: `delta == -old.impact` | unit |
| I5 | repeat delete (no local row) → empty delta, no balance change | unit |
| I6 | OPENING_BALANCE behaves as a regular ACTUAL for cache | unit |
| I7 | dashboard income/expense excludes OPENING_BALANCE | unit |
| I8 | dashboard ignores rows that no longer exist (deleted) — naturally satisfied | unit |
| I9 | `recalculate(account)` ≡ cache after a series of `applyTransactionChange` | property test (fuzz) |
| I10 | Push-step idempotency: replaying the same outbox row leaves balance unchanged | integration |
| I11 | Pull-side delta application: applying a remote tx delta yields the same balance as a local apply of the same change | integration |

---

## 9. Layering

- **domain** (pure):
  - `Transaction`, `TransactionType`, `TransactionStatus`, `balanceImpact()`, `calculateBalanceDelta()`, `PeriodTotals`, `calculatePeriodTotalsFromList(list, from, to)`.
  - `usecase/ApplyTransactionChangeUseCase`.
  - `usecase/RecalculateAccountBalanceUseCase`.
  - `usecase/CalculatePeriodTotalsUseCase`.
- **data** (Firestore + Room):
  - `TransactionRepositoryImpl` — Room CRUD + outbox enqueue. Two write paths: user-facing (enqueues) and sync-applier (`upsertWithoutOutbox`, applies pull-side delta as in §6.3).
  - `AccountRepositoryImpl.applyDelta(accountId, delta: Money, now: Long)` — atomic with the tx write (Room tx). Unwraps `Money → Long minor` at the column boundary.
  - `FirestoreTransactionPusher` — runs the Firestore client transaction described in §6.2. Reuses domain `calculateBalanceDelta` directly.
  - Outbox payload type: `TransactionDto` (a snapshot of the new state).
- **forbidden**: domain does not import Firestore/Room. `Money` lives in domain; `Long minor` only at persistence boundaries.

---

## 10. Deferred

### TRANSFER (out of scope v1)

When we revisit — add to `TransactionType`, plus `fromAccountId/toAccountId`; make `accountId` nullable. `balanceImpact()` for TRANSFER returns two entries. `PeriodTotals` gets `transferIn/transferOut`; transfers don't enter income/expense.

### ADJUSTMENT (out of scope v1)

When we revisit — add `ADJUSTMENT` to `TransactionType` and an `adjustmentSign: Int` field. `balanceImpact() → money * adjustmentSign`. Not in income/expense.

### Multi-currency (out of scope v1)

Currently invariant `tx.currencyCode == account.currencyCode`. When FX is added — snapshot the FX rate on the tx and convert in `balanceImpact`.

### PENDING (revisit with recurring)

Use cases:
- card authorization before settlement;
- recurring rule generated a tx the user has not yet confirmed.

Decisions when we get there:
- Add `TransactionStatus.PENDING`. `balanceImpact() → emptyMap()` (same as PLANNED — `Account.balance` is not touched).
- Optional second cache `Account.pendingBalance: Money` if UI needs a projected balance. Driven by the same delta machinery in a separate column.
- `PeriodTotals` gets `pendingIncome: Money`, `pendingExpense: Money`.
- PENDING→ACTUAL goes through `applyTransactionChange`; the delta automatically applies the impact to `Account.balance`.
- Recurring: status produced by the rule's policy (auto-confirm → ACTUAL, manual-confirm → PENDING).

---

## 11. Test plan

```
domain/test/
  BalanceImpactTest
    - income / expense / opening_balance — single account, returns Money
    - planned → empty
  CalculateBalanceDeltaTest
    - create / update money / update account / update status / delete
    - idempotent repeat delete (null/null)
    - planned → actual / actual → planned
    - delta values are Money, sums and signs correct
  PeriodTotalsTest
    - income/expense aggregation (Money)
    - opening_balance skipped
    - planned in plannedIncome/plannedExpense
    - net = income - expense
    - boundary dates (from/to inclusive) with timezone conversion
  ApplyTransactionChangeUseCaseTest (in-memory repo)
    - create income → cache += money
    - update money → cache += diff
    - update account → unwind on old, apply on new
    - planned→actual / actual→planned
    - delete → unwind, hard-delete tx row
    - delete missing tx → no-op
  RecalculateAccountBalanceTest
    - reproduce from a set of tx, returns Money
    - ignores planned
    - opening_balance included
    - after a series of applyTransactionChange equals recalc (fuzz)

sync/test/
  TransactionPushTest (with Firebase emulator)
    - INSERT push: tx doc created, balance incremented by impact
    - UPDATE push: server delta applied, idempotent on replay
    - DELETE push: deletedAt set, balance decremented by old impact, idempotent on replay
    - concurrent push for same tx: serialized, balance consistent
    - coalescing: multiple pending rows for same entityId collapse to latest
  TransactionPullTest
    - pulled INSERT updates Account.balance via delta
    - pulled UPDATE applies (new.impact - local.impact) to balance
    - pulled tombstone (deletedAt != null) → hard-delete row + decrement balance
    - pulled tombstone for tx we don't have → no-op
```

---

## 12. Migration

- `TransactionType { REGULAR, INITIAL_BALANCE }` → `TransactionType { INCOME, EXPENSE, OPENING_BALANCE }` + new `TransactionStatus { ACTUAL, PLANNED }`.
- `INITIAL_BALANCE` → `OPENING_BALANCE / ACTUAL`.
- `REGULAR` → backfill: `type = INCOME` if `category.type == INCOME`, else `EXPENSE`.
- `Account.balance` stays. One-time `recalculate` for all accounts on first launch of the new version.

New columns in Room (`transactions`):
- `status` (string, default `ACTUAL`).

`updatedAt: Long` is added by sync.md §4.3 across all sync entities, not by this feature.

Backfill:
- `status = ACTUAL` for all existing rows.
- Existing `timestamp` already carries the business time of the operation — no separate `date` field.

Firestore schema: same fields as Room plus `deletedAt: Long?` (added by sync.md §4.4 across all collections). Non-breaking — new fields are optional, old documents are read with defaults.

Schema bump policy follows sync.md §4.3 (`fallbackToDestructiveMigration` in dev) — no manual migrations until prod.
