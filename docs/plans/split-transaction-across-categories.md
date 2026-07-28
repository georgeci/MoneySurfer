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
- [The one cost variant A carries](#the-one-cost-variant-a-carries)
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
| Budgets / monthly totals / spend history | **unchanged** | every aggregate rewritten onto a JOIN |
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

Variant A also keeps every analytic working on day one — a leg categorized as
"Household chemicals" is indistinguishable from an ordinary transaction to a
budget. Variant B would mean rewriting `getMonthlyTotalsByCategory`,
`GetTransactionsByCategoryUseCase`, `GetCategorySpendHistoryUseCase`,
`BudgetProgress`, the category filter and the dashboard category widgets, then
auditing each one separately for double counting.

## The one cost variant A carries

UI collapsing. A list must render one row — "Pyaterochka · 3 400 ₽ · 3
categories" — not three. Sites:

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
   screen.

## Open question to settle before coding

**Are differing dates or accounts across legs forbidden?** They should be —
otherwise a group stops being one receipt. That is an invariant to validate on
write and to protect when a single leg is edited through the ordinary edit
path.
