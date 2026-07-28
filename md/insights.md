# Insights — Implementation Plan

Spending analytics: by category, income vs expense trend, daily heatmap, top
merchants, and the rule-driven `SurferInsightsWidget` (#295).

Status: draft. Nothing here has shipped. The dashboard widget tasks this plan
feeds are #286–#296; the enabler (#282–#284, registry + customize screen +
card-style picker) is already merged.

## Scenarios (user-facing)

1. "Where did July go?" — expenses split by category for a period, largest first.
2. "Am I spending more than usual?" — this period vs the previous one, per
   category and in total.
3. "What does a year look like?" — income vs expense columns per month.
4. "Which days do I spend on?" — calendar heatmap of daily expense.
5. "Who takes my money?" — top merchants for a period.
6. "Tell me what matters" — two or three generated sentences on the dashboard.

## Decisions (locked)

1. **One definition of spend.** Insights, budgets and the dashboard must agree
   on which rows count. The canonical predicate is the one budgets already use
   (`Budget.counts`, [domain/.../model/BudgetProgress.kt](../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/model/BudgetProgress.kt)):
   `type = EXPENSE` and `status = ACTUAL` and `transferId IS NULL` and
   `operationDate` in the half-open window and `currencyCode = workspace base`.
   See [Status quo](#status-quo) — today three different definitions coexist.
2. **Transfers never count.** Moving money between own accounts is not spending.
   Transfer legs *do* carry a category (`CreateTransferUseCase` assigns a
   `CategoryType.TRANSFER` category), so filtering by category type is not enough —
   the predicate must test `transferId IS NULL`.
3. **Aggregate in SQL, not in memory.** Every rollup is a `GROUP BY` in SQLite
   returning tens of rows, following the
   [CategorySpendRepository](../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/repositories/CategorySpendRepository.kt)
   precedent. No Insights code may call `getByWorkspaceId(...)` and fold.
4. **Historical series stay in base currency; balances still convert.** A
   balance is a present-day quantity, so converting it at today's rate is
   correct — that is what `ConvertAccountsTotalUseCase` does for the Balance
   widget. A *historical* series is not: only the latest FX table is cached
   (`replaceForBase` wipes the previous one) and `Transaction` carries no rate
   snapshot, so converting a 12-month trend would silently reshape history every
   time the rate moves. v1 therefore counts base-currency rows only and reports
   what it left out, the same contract `ConvertedTotal.unconverted` already uses
   on the dashboard. Revisit when the FX snapshot from
   [md/total_calc.md](total_calc.md) §10 lands.
5. **Uncategorized is a bucket, not a gap.** `Transaction.categoryId` is
   nullable. `categoryId IS NULL` groups into one explicit "Uncategorized" slice.
6. **No result cache in v1.** Room `Flow` already invalidates on write and the
   queries are index-covered. Caching is added only against a measurement — but
   see [Risks](#risks) for the sync-storm caveat.

## Status quo

Three incompatible answers to "how much was spent" ship today:

| Where | Filters | Currency | Transfers |
| --- | --- | --- | --- |
| `Budget.counts` (in-memory fold) | EXPENSE, ACTUAL, `transferId IS NULL`, category match | base only, rest flagged via `hasMixedCurrency` | excluded |
| `TransactionDao.getMonthlyTotalsByCategory` (SQL) | EXPENSE, ACTUAL, `categoryId IN (...)` | **sums minor units across currencies** | **included** |
| `calculatePeriodTotalsFromList` | ACTUAL/PLANNED split | **sums minor units across currencies** | **included** |

`calculatePeriodTotalsFromList` also derives the date from `operationAt` plus a
timezone rather than reading the stored `operationDate` column, so it can book a
transaction into a different month than the budget engine does for the same row.

`CalculatePeriodTotalsUseCase` is referenced by no feature yet, which is why the
disagreement has not surfaced. Wiring it into a dashboard widget (#287) without
Phase 0 below would ship a dashboard number that contradicts the budget screen.

What already exists and should be reused:

- Schema: `transactions.operationDate` is an indexed ISO `YYYY-MM-DD` **string**,
  covered by `(workspaceId, operationDate DESC, operationAt DESC, createdAt DESC)`.
  A plain string range is a correct date range; rows predating the column hold
  `''` and sort below every real date.
- `TransactionPeriodWindow` + `periodWindow(mode, anchor)` + `shiftPeriod` in
  [domain/util](../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/util/TransactionPeriodWindow.kt).
  Do not introduce a second period type for #296.
- UI: `SurferCategoriesDonutWidget` (Canvas `drawArc`), `SurferSpentMonthWidget`,
  `SurferInsightsWidget`, `SurferRecurringWidget` are built in `uikit` and wired
  nowhere. `SurferCategoryTrendCard` is a working month-column bar chart.
  KoalaPlot (`XYGraph` + `AreaPlot2`) is used by exactly one component,
  `SurferBalanceChartCard`.
- `DashboardLayoutCodec` skips unknown widget names on decode and lets
  `normalized()` refill from the default, so adding entries to
  `DashboardWidgetType` is forward- and backward-compatible. No migration needed.

What must not be built on:

- `TransactionDao.getCategorizedWindow` and `getTotals` have **no `workspaceId`
  predicate** — they scope by account or span every workspace on the device.
- `GetBudgetProgressUseCase` loads the entire workspace transaction list on every
  emission. It is the pattern to replace, not to copy.

## Phase 0 — one predicate, one repository

No UI. This is the phase that makes every later number defensible.

**Landed (issue #384)**, with three calls the plan left open:

- `PeriodTotals` / `calculatePeriodTotalsFromList` / `CalculatePeriodTotalsUseCase` were deleted
  rather than re-expressed. Nothing referenced them, and `netByMonth` covers the income/expense
  split they existed for. The planned-income/expense split they also carried has no consumer;
  it belongs in the same query when a screen wants it.
- `topMerchants` skips rows with a blank `merchant` instead of bucketing them. Unlike a missing
  category, an empty label is not a counterparty the user could act on, and it would be the
  largest bar in most workspaces.
- The shared `WHERE` carries one term the predicate below does not:
  `operationDate = date(operationDate)`, which admits only the canonical `YYYY-MM-DD` spelling.
  `operationDate` is plain text, and the month and day series parse it while the category,
  merchant and currency rollups do not — so any string SQLite reads but `LocalDate.parse` refuses
  (`''`, `'2025-3-5'`, `'2025-03-05T10:00'`, a Julian day number) would be counted by one half and
  dropped by the other. `''` is the only spelling seen in the wild, from before the column
  existed, and `MIGRATION_27_28` already backfilled it; the rest guards against an older or
  foreign writer. `getMonthlyTotalsByCategory` carries the same term, so all six queries agree.

Still open from the [status quo](#status-quo) table: the SQL predicate tests the *stored* type,
so the legacy `REGULAR` spelling that `TransactionRepositoryImpl.parseType` resolves by sign is
counted by `Budget.counts` and by nothing in Insights. Pre-existing — `getMonthlyTotalsByCategory`
always behaved this way — and best fixed by a backfill migration, not by a fourth predicate.

Two contract notes for the widget tasks:

- `netByMonth` groups *inside* the window, so a window whose ends are not month boundaries returns
  part-months under a full month's name — `MonthlyNet.month` cannot express the difference. #296's
  Week mode belongs on `daily`, not here.
- `GetCategorySpendHistoryUseCase` observes the workspace base currency rather than reading it
  once, because the aggregate filters on it: a device that has not pulled the workspace row yet
  resolves `null`, which matches nothing, and a latched `null` would leave an empty trend on
  screen for as long as the caller stayed subscribed. Any use case built on `SpendScope` needs the
  same treatment.

New aggregation-only interface, alongside `CategorySpendRepository` rather than
inside `TransactionRepository`:

```kotlin
// domain/repositories/SpendAnalyticsRepository.kt
interface SpendAnalyticsRepository {
    fun byCategory(scope: SpendScope): Flow<List<CategorySpendSlice>>
    fun netByMonth(scope: SpendScope): Flow<List<MonthlyNet>>
    fun daily(scope: SpendScope): Flow<List<DailySpendPoint>>
    fun topMerchants(scope: SpendScope, limit: Int): Flow<List<MerchantSpend>>
    fun excludedByCurrency(scope: SpendScope): Flow<List<CurrencyTotal>>
}

data class SpendScope(
    val workspaceId: WorkspaceId,
    val baseCurrency: CurrencyCode,
    val window: TransactionPeriodWindow,
)
```

Every query carries the same `WHERE` clause — extract it as a shared SQL
fragment in the DAO KDoc so a future sixth query cannot drift:

```sql
WHERE workspaceId = :workspaceId
  AND type = 'EXPENSE' AND status = 'ACTUAL'
  AND transferId IS NULL
  AND currencyCode = :baseCurrency
  AND operationDate >= :fromDate AND operationDate < :toDateExclusive
```

`netByMonth` is the exception: it needs income too, so it groups by
`substr(operationDate, 1, 7)` **and** `type`, dropping only the `type` predicate.

Row models live in `domain/model/`, mirroring `CategoryMonthlyTotal`:
`CategorySpendSlice(categoryId: CategoryId?, total: Money, transactionCount: Int)`,
`MonthlyNet(month: YearMonth, income: Money, expense: Money)`,
`DailySpendPoint(date: LocalDate, total: Money)`,
`MerchantSpend(merchant: String, total: Money, transactionCount: Int)`.

Impl in `data-local/repository/SpendAnalyticsRepositoryImpl.kt`, injecting
`TransactionDao` directly — exactly as `CategorySpendRepositoryImpl` does, with
the same defensive `mapNotNull` on unparseable date strings.

Also in this phase:

- Align `getMonthlyTotalsByCategory` with decisions 1–2 (add `transferId IS NULL`
  and the currency predicate) — otherwise the category detail trend keeps
  disagreeing with the new screens. This changes numbers on a shipped screen, so
  it needs its own note in the PR body.
- Either delete `CalculatePeriodTotalsUseCase`/`PeriodTotals` or re-express them
  on `netByMonth`. Leaving an unused second definition in `domain` is what caused
  this in the first place.

**Indexing.** The workspace/date composite covers all five queries. `merchant`
is unindexed, so `topMerchants` scans the window — acceptable while the window is
bounded, and a `(workspaceId, merchant)` index is the fallback if a year-wide
range measures badly. Do not reach for `transactions_fts`; it is a search index.

Tests: kotest `StringSpec` in `data-local` against an in-memory Room DB, one spec
per decision — a transfer pair contributes zero, a foreign-currency expense is
absent from `byCategory` and present in `excludedByCurrency`, a `PLANNED` row is
absent, a null category lands in the uncategorized bucket, a window boundary is
inclusive at the start and exclusive at the end.

## Phase 1 — dashboard widgets on the new repository

Wires the orphan widgets. Each is one `DashboardWidgetType` entry plus one branch
in the `when` in `DashboardScreen.kt`, plus its use case.

- #287 SpentMonth — `GetPeriodSpendUseCase` over `netByMonth`, plus vs-last-month
  delta. The delta is why the query returns a series rather than one number.
- #288 CategoriesDonut — `byCategory` joined with `GetCategoriesUseCase` for name
  and `hue`. Segments already exist as `SurferDonutSegment(label, percent, color)`.
- #290 SpentByCategory — same query, five variants (bar/ring/gauge/chips/multi);
  the over/near-limit states come from `GetBudgetProgressUseCase`, so this one
  genuinely depends on budgets.
  **As built, the caps do not go through `GetBudgetProgressUseCase`.** That use
  case folds the whole workspace transaction list per emission, which is the
  pattern this repository exists to replace, so `buildSpentByCategory` measures a
  category against its budget's own `amount` using the shared `budgetStatusOf`
  thresholds. The cost is a rollover carry the widget does not see, and a budget
  anchored off the 1st whose status can differ from the Budgets screen's.

`Content` already carries `layout: DashboardLayoutConfig`; the new data joins the
existing `combine` in `DashboardViewModel.observeDashboard()`. Watch the arity —
it combines five flows today.

## Phase 2 — shared period state (#296)

`PeriodSwitch` (Week / Month) lifted into `DashboardState.Content`, driving every
spend widget through one `SpendScope`. Built on `periodWindow(mode, anchor)` and
`shiftPeriod`, not a new type. Per decision 6 this is device-local UI state, not
a preference — unless the user asks for it to stick.

**The insights engine does not take a period mode, and its guard is month-shaped.**
`GenerateInsightsUseCase` (#295, Phase 5) hard-codes month-to-date against the same
stretch of the previous month, and stands its comparison rules down below
`MIN_COMPARISON_DAYS = 7` elapsed days. Wiring the Insights widget to a Week period
would silence it for six days of every seven: in Week mode `elapsedDays` runs 1..7
and only ever reaches the threshold on the last day of the week. The guard has to
become a *share* of the period (a quarter of it, say) or a per-mode minimum before
the switch can reach that widget. #296's own scope does not list Insights, so this
is a trap for whoever extends it later rather than a blocker for #296 itself.

## Phase 3 — `feature/insights` module

New module `feature/insights` (nothing exists under `feature/` for it today),
following `feature/budget`'s shape: `InsightsViewModel` on `MviViewModel` with a
sealed `Loading`/`Content` state and one `inFlight` flag, `InsightsScreen`,
`InsightsNavGraph`, `di/InsightsModule.kt`.

Content: the category breakdown from Phase 1 at full size, the income-vs-expense
month columns (`SurferCategoryTrendCard`'s bar treatment generalised), top
merchants, and the period picker. Filters beyond period — account, category,
currency — are deliberately deferred; `TransactionFilters` already exists for the
transaction list and should be reused rather than forked when they land.

## Phase 4 — heatmap

The only genuinely new drawing work. `SurferSpendHeatmap` in `uikit`: a 7×N grid
over `List<DailySpendPoint>`, quantised into four or five buckets.

Constraints from [uikit/README.md](../uikit/README.md): no `Color(0xFF...)`, no
`MaterialTheme.colorScheme.*` — the scale is derived from `AppTheme.materialColors`.
Quantise rather than interpolate continuously: a continuous ramp is unreadable
and fails contrast checks at the low end. Empty days must be visibly distinct
from zero-spend days.

## Phase 5 — rule engine + Insights widget (#295)

`GenerateInsightsUseCase` over the Phase 0 aggregates, producing
`List<Insight>` mapped to `SurferInsightItem` / `SurferInsightTone`.

Rules are pure functions, each returning at most one insight:

| Rule | Fires when | Tone |
| --- | --- | --- |
| CategoryUp | category total > previous period × threshold **and** absolute delta clears a floor | Warn |
| CategorySaving | category total < previous period × threshold | Good |
| PeriodNet | net income vs previous period | Good/Warn |
| BudgetRisk | `BudgetProgress.projectedTotal > effectiveLimit` | Warn |
| Subscriptions | count of active `RecurringRule` + monthly total | Neutral |

The absolute floor matters: without it a category that went from €2 to €5 reads
as "up 150%" and crowds out the €300 one.

Ids must be stable across recomputation (`"category-up:${categoryId}:${period}"`),
otherwise the "N new" badge counts the same insight forever and dismissal cannot
be persisted later. Rules are table-driven kotest specs in `domain`.

**Landed (issue #295)**, with four calls the plan left open:

- **The baseline is month-to-*date*, not last month whole.** Comparing three days of July with
  all of June reads as a 90% fall in every category, so the engine would spend the first week of
  every month congratulating the user for not having spent the money yet. Both windows are the
  same number of days, and the previous one is clamped to its own month's last day so a 31st
  compares against a whole 28-day February. `periodWindow(Month, ...)` is deliberately *not* used
  — it always spans a whole calendar month, and the point here is a matched pair of part-months.
- **The comparison rules wait a week.** Matched windows remove the length bias but not
  small-sample volatility: on the 1st the baseline is a *single day*, and one bill landing a day
  either side of the boundary swings it 100% — a user who pays rent on the 1st and has not been
  billed yet would be told "Rent is down 100%" every month. The relative floor cannot filter that,
  because the floor is a share of the same one-day baseline. Below seven elapsed days the category
  and period rules stand down; the subscription count still fires, since it reads the schedules
  rather than the window.
- **The date is a flow, not a value.** `clock.now()` read once would freeze both windows for the
  life of the subscription — the workspace pointer does not re-emit at midnight, so a desktop app
  left open would still be calling July "this month" on 2 August, with that day's transactions
  outside every window. The use case sleeps to the next local midnight and re-derives.
- **The floor is relative, not absolute.** A fixed "€20" means something different in every
  currency the app supports; the floor is 5% of the *previous period's total spend*, which reads
  the same everywhere and does the job the plan wanted it for (2 → 5 is filtered, 300 → 380 is
  not).
- **CategoryUp and CategorySaving are one rule.** They are one finding read in two directions, so
  `Insight.CategoryChange` carries both and `isIncrease` is the direction. PeriodNet became
  `PeriodSpend` — expense against expense rather than net income, since `byCategory` already
  answers it and `netByMonth`'s part-month caveat does not apply.
- **BudgetRisk is not built.** It is the one rule that genuinely needs Budgets (#243), and #295's
  scope did not include it. It slots in as a fourth rule with no change to `InsightInput` beyond
  the progress list.

The widget also gained a `list` / `carousel` card-style variant, keyed the way
`SurferBalanceVariant` already is, so the picker offers it without a persistence change.

Left open, deliberately:

- **The base-currency empty state.** [Phase 3](#phase-3--featureinsights-module) requires an empty
  state that *names* the reason when the currency filter hides everything — a blank card in a
  mixed-currency workspace reads as a bug, not as a policy. The dashboard widget does not do this
  yet: it ignores `excludedByCurrency` and falls back to "Nothing notable this period." Fixing it
  means a fifth flow into the use case's `combine` and a signal on `Insight` that the copy can
  render, so it belongs with the screen that owns the wording (#385).
- **The seven-day guard is month-shaped** — see [Phase 2](#phase-2--shared-period-state-296).

## Phase 6 — reuse

#294 BurnRate and #293 SafeToSpend both want a daily expense series and are
already specified as `GetDailySpendSeriesUseCase`; Phase 0's `daily()` is that
query. They should land after Phase 5 rather than growing Phase 1.

## Risks

- **Sync storms.** Room's invalidation tracker fires on any write to
  `transactions`, so a pull applying thousands of rows re-runs every aggregate
  flow per batch. Insights flows need `conflate()` (or a debounce) before the UI
  collects them. This is not hypothetical — `TransactionSyncPlugin.applyDoc`
  writes row by row.
- **Hard delete.** There is no local soft delete (#346), so a deleted transaction
  silently rewrites history. Correct for aggregates, surprising in a trend the
  user is staring at. No action, but do not promise immutability in copy.
- **`DashboardWidgetType` growth.** Five new constants means five `when` branches
  in one composable; the enum's KDoc already calls the two-step change out. Keep
  each branch a one-line call into a private `@Composable` per widget so the file
  stays reviewable, and check the exhaustive `when` compiles before the codec
  test suite runs.
- **Duplication.** Five near-identical DAO queries will trip `cpdCheck`. Expect
  it, and prefer one shared `WHERE` fragment documented in KDoc over five
  independently drifting copies — see [ai/skills/cpd-rules.md](../ai/skills/cpd-rules.md).

## Open questions

1. Does the period picker persist across app launches, or reset to the current
   month? Phase 2 assumes reset.
2. Should `byCategory` roll subcategories into their parent by default?
   `CategorySpendHistory` rolls a whole subtree into the parent and breaks out
   direct children; the donut probably wants parents only, with drill-down.
3. Is a foreign-currency-only workspace (base RUB, all cards EUR) realistic
   enough that decision 4 leaves it with empty charts? If so, Phase 0 needs an
   explicit empty state naming the reason, not a blank donut.
