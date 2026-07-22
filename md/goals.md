# Savings Goals — Implementation Plan

Written 2026-07-21. There is no `SavingsGoal` domain model, no table, no repository and no
prior note — the Claude Design project is the only specification, with the inventory mirrored
in [design/README.md](design/README.md).

One piece of goals code does exist:
[SurferGoalsWidget.kt](../uikit/src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/widgets/SurferGoalsWidget.kt)
in `uikit`, with a `SurferGoalItem` view model and a `ProgressRing`. It is referenced nowhere
outside its own previews. See Phase 5 — it is not a blank slate there.

Structure follows [budgets.md](budgets.md); read that first, since Goals reuses the same
sync, dual-write and module conventions.

## Scope — v1 is an MVP

In scope: goals list, goal details, create / edit, manual contribution, manual withdrawal,
and the status lifecycle (pause / archive / delete).

Deferred: `autopay` (scheduled auto-contributions — overlaps the recurring-payments
feature and should be built on top of it, not beside it), forecast / ETA chips, the
sparkline with its projection curve, the "expected today" tick on the progress bar, and
the active/archived list filters. All of these are drawn in the mockup; none ship in v1.

## Scenarios

- Create a goal: title, emoji, target amount, target date, optional linked account
- See progress: saved / target, %, remaining
- Add money to a goal; withdraw money from a goal
- See the contribution history
- Pause, resume, archive, delete a goal
- A goal that reaches its target flips to COMPLETED

## Screens

| Screen | Mockup artboards |
|---|---|
| `SavingsGoalsScreen` | 01 |
| `SavingsGoalDetailsScreen` (+ actions sheet) | 02, 02b |
| `EditSavingsGoalScreen` (create / edit) | 03, 03b |
| `GoalContributionScreen` (add / withdraw) | 04, 04b |

## Decisions (locked)

| # | Question | Decision |
|---|---|---|
| 1 | What is a contribution? | **Its own entity**, `GoalContribution`. A goal is a counter of intent — no money moves, no account balance changes. |
| 2 | Ready for the money-backed model | `GoalContribution` carries a nullable `transferId: TransferId?`, **always `null` in v1**. Later, "Add money" can additionally create a real transfer via the existing [CreateTransferUseCase](../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/usecase/CreateTransferUseCase.kt) and store its id here. `Transaction` is not touched in v1. |
| 3 | Withdrawal | A contribution with a negative amount. Not a separate entity, not a separate flag. |
| 4 | `current` | Derived — `SUM(contributions.amount)`. Never stored on the goal; there is no field to drift. |
| 5 | Workspace scope | Per-workspace, dual-written + synced, exactly like categories. |
| 6 | Currency | Workspace base currency only in v1, matching Budgets. |
| 7 | Linked account | `accountId` is optional and **decorative in v1** — it records where the user intends the money to sit. The app cannot and does not verify it. Document this in the UI copy so the number is not read as a real balance. |
| 8 | Icons | **Emoji**, as drawn in the mockup. This deliberately departs from the design system's own rule that emoji appear only in workspace icons — the goals screens are the second sanctioned exception. Create/edit needs an emoji picker. |
| 9 | Delete vs archive | `status = ARCHIVED` hides a goal from the list. `delete()` is a real delete, for the account-purge path, owner-only in the rules — same shape as Budgets. |
| 10 | Sync timing | Same batch as the feature, inherited from the Budgets decision. |

## Domain model

```kotlin
data class SavingsGoal(
    val id: GoalId = GoalId.uuid(),
    val workspaceId: WorkspaceId,
    val title: String,
    val emoji: String,
    val hue: Int,                       // 0..360, tints the icon tile
    val target: Money,
    val currencyCode: CurrencyCode,
    val startDate: LocalDate,
    val targetDate: LocalDate?,         // null = no deadline
    val accountId: AccountId?,          // decorative in v1 — see decision 7
    val status: GoalStatus = GoalStatus.ACTIVE,
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
)

enum class GoalStatus { ACTIVE, COMPLETED, PAUSED, ARCHIVED }

data class GoalContribution(
    val id: GoalContributionId = GoalContributionId.uuid(),
    val workspaceId: WorkspaceId,
    val goalId: GoalId,
    val amount: Money,                  // signed — negative is a withdrawal
    val occurredOn: LocalDate,
    val note: String,
    val transferId: TransferId? = null, // always null in v1 — see decision 2
    val createdAt: Instant,
    val updatedAt: Instant = createdAt,
)
```

`GoalId` and `GoalContributionId` are value classes with `Companion.uuid()`, mirroring
[BudgetId](../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/primitives/BudgetId.kt).

Domain timestamps are `Instant`, entity columns are `Long` epoch-ms — the `Category`
convention.

---

## Phase 0 — Domain + Room

Two new entities in `data-local`, both following `CategoryEntity`:

- `goals` — FK `workspaceId` → `WorkspaceEntity`, nullable FK `accountId` → `AccountEntity`,
  indices on both.
- `goal_contributions` — FK `workspaceId`, FK `goalId` → `GoalEntity` with
  `onDelete = CASCADE`, indices on `goalId` and `workspaceId`.

DAOs expose `getByWorkspaceId`, `getById`, `getByGoalId` (contributions, newest first),
`insert`, `update`, `delete`, `@Upsert upsertAll`, and a `SUM(amount)` aggregate per goal.

Bump `MONEY_SURFER_DB_VERSION`; the migration is purely additive (two new tables).

## Phase 1 — Repositories with dual-write

`SavingsGoalRepository` and `GoalContributionRepository`, both implemented against
[CategoryRepositoryImpl](../data-local/src/commonMain/kotlin/com/georgeci/moneysurfer/data/repository/CategoryRepositoryImpl.kt)
as the reference: inject `OutboxEnqueuer` + `ClockUseCase`, enqueue an upsert after every
insert/update and a delete after every delete, preserve `createdAt`, stamp `updatedAt`.

Deleting a goal must enqueue delete mutations for its contributions too — Room's
`CASCADE` removes the local rows silently, and the outbox will not learn about them
otherwise, leaving orphans in Firestore.

## Phase 2 — Sync

Two entity types, two collections, two plugins. Reference implementation:
[CategorySyncPlugin](../sync-surfer/src/commonMain/kotlin/com/georgeci/moneysurfer/data/sync/plugin/CategorySyncPlugin.kt).

1. `SyncEntityTypes.GOAL = "GOAL"`, `SyncEntityTypes.GOAL_CONTRIBUTION = "GOAL_CONTRIBUTION"`.
2. `SyncCollection.GOALS = "goals"`, `SyncCollection.GOAL_CONTRIBUTIONS = "goalContributions"`
   — unlike `BUDGETS`, these constants do **not** exist yet and must be added.
3. `SyncPullPriorities`: `GOALS = 60` (after `ACCOUNTS` = 20, because of the nullable
   account FK), `GOAL_CONTRIBUTIONS = 70` (after goals, hard Room FK).
4. `GoalDoc` and `GoalContributionDoc` in `RemoteDtos.kt`, plus `toDoc()` / `toEntity()`
   mappers in `SyncDtoMappers.kt`.
5. `SavingsGoalSyncPlugin` and `GoalContributionSyncPlugin`, each
   `@Single(binds = [SyncEntityPlugin::class])`, with tombstone push and LWW resolution.
6. `firestore.rules`: new `/goals/{gid}` and `/goalContributions/{cid}` blocks under the
   workspace, each with a `hasValid*Shape()` guard from the start — do not repeat the
   budgets mistake of shipping an unguarded collection.
7. Rules tests in `firestore-tests/` (`npm test`, JDK 21 on PATH).

## Phase 3 — Use cases

`domain/usecase/`: create, update, delete, `SetGoalStatusUseCase`, `GetGoalsUseCase`,
`GetGoalDetailsUseCase`, `AddContributionUseCase`, `WithdrawFromGoalUseCase`,
`GetGoalContributionsUseCase`.

`GoalProgress` carries `saved`, `target`, `remaining`, `percent` (capped at 1.0 for
display, uncapped for logic), and `isReached`.

Rules to enforce in use cases, each with a kotest `StringSpec`:

- A withdrawal cannot take `saved` below zero.
- A contribution cannot be zero.
- Reaching the target flips `ACTIVE → COMPLETED`; a later withdrawal that drops `saved`
  below the target flips it back to `ACTIVE`.
- A `PAUSED` or `ARCHIVED` goal rejects new contributions.
- The currency must match the workspace base currency.

## Phase 4 — `feature/goal` module

New Gradle module modelled on `feature/account`. MVI view models for list, details, edit,
and contribution. Navigation routes: `Goals`, `GoalDetails(id)`, `GoalCreation`,
`GoalEdit(id)`, `GoalContribution(id, mode)`.

## Phase 5 — UIKit components

Start by triaging the existing `SurferGoalsWidget`. It is unused, but unlike the budgets
widget it is not obviously wrong: its `ProgressRing` is close to what the details hero needs,
and `SurferGoalItem` already models the saved/target/progress triple. Decide reuse versus
delete before writing the ring a second time — and note it is a compact dashboard row, not a
list card, so the card work is genuinely new either way.

Then, per the mockup:

- `SurferGoalIcon` — squircle `32% / 38%`, emoji centred on a hue-tinted fill
- `SurferGoalStatusPill` — ACTIVE (outlined + dot) / COMPLETED (filled + check) / PAUSED / ARCHIVED (neutral)
- `SurferGoalProgressBar` — plain fill in v1; the "expected today" tick is deferred
- `SurferGoalProgressRing` — details hero, inner stack `% → saved → of target`
- `SurferGoalCard` — dims to 70% opacity when PAUSED or ARCHIVED
- `SurferGoalContributionRow` — auto vs manual styling (the auto branch is unused in v1 but the styling belongs with the component)
- `SurferEmojiPicker` — new, no equivalent exists in the codebase

## Phase 6 — Strings and tests

Russian and English from the start. Heaviest unit-test coverage on the status-transition
rules and the withdrawal floor.

---

## Open risk

The mockup leans hard on `forecast` / `etaLabel` / the sparkline to make a goal feel alive,
and v1 ships without all of it. The list and detail screens will look noticeably emptier
than the design. Worth deciding, before Phase 4 starts, whether the layout should be
adjusted for the missing chips or left with the gaps so the deferred work drops straight
in later.

---

## As built (phases 3–6, issue #245)

Decisions taken while implementing, that the plan left open:

- **`SurferGoalsWidget`: kept, not deleted.** Its `ProgressRing` moved into
  `components/goal` as the public `SurferGoalRingArc`, which the widget and the details-hero
  `SurferGoalProgressRing` now share — one arc, two framings. The widget itself stays the
  compact dashboard row it always was.
- **Entry point: the dashboard, not the nav suite.** The nav bar already carries five
  top-level destinations; `Route.Goals` is a plain route reached from the (previously unused)
  goals widget's "See all", which is also what artboard 01 implies. The widget is now wired to
  `GetGoalsUseCase` and shows the top two goals.
- **Layout with the deferred chips left out.** No filler was invented for the missing
  forecast / ETA / sparkline: the card and the hero are laid out for what v1 has, and the
  widget's `captionLine` is passed empty. The deferred work adds rows rather than reflowing
  existing ones.
- **Emoji + hue live together.** The plan only named `SurferEmojiPicker`; the goal model also
  carries `hue`, so `SurferGoalHueRow` ships beside it, sharing the curated
  `SurferGoalEmojis` set.
- **COMPLETED is not settable by hand.** `SetGoalStatusUseCase` rejects it and derives it
  instead, so pause/resume on an over-target goal lands on COMPLETED rather than ACTIVE.
- **Withdrawals are allowed on a PAUSED or ARCHIVED goal.** Only *new money* is refused —
  the plan's invariant is about contributions, and blocking a withdrawal would trap funds in
  an archived goal.
- **`SavingsGoalRepository.observeById`** was added (with a matching `GoalDao` query) so the
  details screen closes itself when the goal is deleted here or by a sync from another device.
