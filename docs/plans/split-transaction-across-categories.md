---
title: Split one transaction across several categories
created: 2026-07-28
status: backlog
---

# Split one transaction across several categories

<!-- DOCS:TOC -->
## Contents
- [Split one transaction across several categories](#split-one-transaction-across-several-categories)
- [Decision](#decision)
- [Why not a child splits table](#why-not-a-child-splits-table)
- [Costs variant A carries](#costs-variant-a-carries)
  - [Transaction counts stop meaning receipts](#transaction-counts-stop-meaning-receipts)
  - [List collapsing has to survive the paging window](#list-collapsing-has-to-survive-the-paging-window)
- [Implementation outline](#implementation-outline)
- [Open question to settle before coding](#open-question-to-settle-before-coding)
<!-- DOCS:END -->

One supermarket receipt is one payment but several spending categories —
groceries plus household chemicals. Today a transaction carries exactly one
category: `categoryId` is a single nullable column on
[TransactionEntity](../../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/db/entity/TransactionEntity.kt),
and every analytic hangs directly off it — `getMonthlyTotalsByCategory` and
`getCategorizedWindow` in
[TransactionDao](../../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/db/dao/TransactionDao.kt),
`BudgetProgress`, `CategorySpendRepository`, the category filter, CSV
export/import, and the transaction sync plugin.

Nothing is implemented yet.

## Decision

**Variant A — a split is N sibling transaction rows sharing a `splitId`**,
mirroring the existing `transferId` grouping.

The rejected alternative, variant B, is a child table
`transaction_splits(transactionId, categoryId, amount)`: one transaction row
with N allocations.

| | A: sibling rows | B: child table |
| --- | --- | --- |
| Migration | one nullable column | new table, new DAO, `sum == amount` invariant |
| Budgets / monthly totals / spend history — sums | **unchanged** | every aggregate rewritten onto a JOIN |
| Spend-history transaction *counts* | count legs, not receipts — see below | count receipts |
| Account balance | sum of legs, correct by construction | correct by construction |
| Sync | same plugin, one more field on `TransactionDoc` | new collection + rules + LWW for child docs |
| CSV | one more column (like `TransferId`) | new format |
| List / counters | 3 rows per receipt — needs UI collapsing | untouched |
| FTS | note duplicated per leg — dedup by group | untouched |

## Why not a child splits table

Sync decides it. The architecture is per-entity LWW with tombstones
([TransactionSyncPlugin](../../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/plugin/TransactionSyncPlugin.kt)).
Under variant B the parent and its splits are separate documents pulled
independently: the parent can arrive with a new amount while its splits have
not arrived yet, which breaks `sum(splits) == amount` with nothing available to
repair it. Under variant A each leg is a self-contained transaction, and LWW is
already correct for it.

Variant A also keeps every analytic *sum* working on day one — a leg
categorized as "Household chemicals" is indistinguishable from an ordinary
transaction to a budget. Variant B would mean rewriting
`getMonthlyTotalsByCategory`, `GetTransactionsByCategoryUseCase`,
`GetCategorySpendHistoryUseCase`, `BudgetProgress`, the category filter and the
dashboard category widgets, then auditing each one separately for double
counting.

## Costs variant A carries

### Transaction counts stop meaning receipts

`getMonthlyTotalsByCategory` returns `COUNT(*)`
([TransactionDao.kt](../../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/db/dao/TransactionDao.kt)),
and `CategorySpendHistory.rollUp`
([CategorySpendHistory.kt](../../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/model/CategorySpendHistory.kt))
sums that across a whole subtree. Split a 3 400 ₽ receipt into
Food > Groceries 3 000 ₽ and Food > Snacks 400 ₽, and the Food details screen
reports **2 transactions**, with `averagePerTransaction` at 1 700 ₽ rather than
3 400 ₽.

Per-leaf-category counts stay right; only a subtree that swallows two legs of
the same receipt inflates. Money totals are unaffected either way.

Accept this as the defined semantics — "a category was charged N times" is a
defensible reading, and the alternative is a `COUNT(DISTINCT COALESCE(splitId,
id))` that then disagrees with the leg rows listed right below the counter.
Decide it explicitly when implementing step 1 rather than discovering it from a
screenshot.

### List collapsing has to survive the paging window

A list must render one row — "Pyaterochka · 3 400 ₽ · 3 categories" — not
three. The complication is that the list is a growing `LIMIT` window, not a
full result set: `getCategorizedWindow` is called with `limit + 1` rows and cut
back to `limit`
([TransactionsByAccountViewModel.kt](../../feature/transaction/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/transaction/list/TransactionsByAccountViewModel.kt)).

Two consequences a naive `groupBy(splitId)` gets wrong:

- **A group can straddle the boundary.** If the first leg of a 3-leg receipt is
  the last row of the window, the other two are simply not fetched, and the
  collapsed row renders 1 200 ₽ instead of 3 400 ₽ — a figure that silently
  changes when the user taps "load more". Either the window query completes any
  trailing group before cutting, or the last group in a page is held back until
  the next page proves it complete.
- **`canLoadMore` counts raw rows.** `page.rows.size > page.limit` stops
  matching the number of *visible* rows once groups collapse, so a page made
  mostly of one receipt's legs can render as three visible rows with no
  indication more exist.

Sites:

- [TransactionsListMapping.kt:54](../../feature/transaction/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/transaction/list/TransactionsListMapping.kt) —
  already carries `isTransferLeg`; same technique.
- `SurferRecentTransactionsWidget`
- `AccountDetailsViewModel`
- FTS search — the note is duplicated across legs, so hits dedup by group.

Account details and category details deliberately do **not** collapse: there a
leg is its own row under its own category.

## Implementation outline

1. **Data + domain** — `splitId` on entity and model; migration modelled on
   `AccountArchivedAtMigration`; `getBySplitId` on the DAO;
   `CreateSplitTransactionUseCase`; group-aware edit and delete (the group
   logic already exists in `DeleteTransactionUseCase` for transfers and is
   reused); a column in the CSV codec.
2. **Sync** — field on `TransactionDoc`; a `typeNullableStrOk(data, 'splitId')`
   line in `hasValidTransactionShape` in
   [firestore.rules](../../firestore.rules) plus the version bump in the header
   comment. The shape check uses no `hasOnly`, so older clients are unaffected.
3. **Entry UI** — a "Split" action on the creation screen; a sheet of
   category + amount rows with the remainder auto-assigned to the last one;
   converting an existing single transaction into a group.
4. **Read UI** — collapsed row with a badge; the breakdown on the details
   screen; the two paging rules above (complete a trailing group before cutting
   the window, count visible rows for `canLoadMore`).

## Open question to settle before coding

**Are differing dates or accounts across legs forbidden?** They should be —
otherwise a group stops being one receipt. That is an invariant to validate on
write and to protect when a single leg is edited through the ordinary edit
path.
