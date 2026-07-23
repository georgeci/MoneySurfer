# Design mirror — Budgets, Goals & Settings

Local partial mirror of the **MoneySurfer** Claude Design project:
<https://claude.ai/design/p/019dd9e9-bcad-78f3-a4b9-49cde06a75ac>

## What's here

| File | Source path in design project | Pulled |
|---|---|---|
| `budget-data.jsx` | `budget-data.jsx` — seed budgets + per-budget tx | 2026-07-21 |
| `goals-data.jsx` | `goals-data.jsx` — seed goals, contributions, status/forecast tone maps | 2026-07-21 |
| `settings-screens.jsx` | `settings-screens.jsx` — the 8 Settings screens, row-by-row | 2026-07-22 |

Settings has no `*-data.jsx`: the row inventory *is* the model, so the screen file is
mirrored instead. `settings-components.jsx` is summarised below rather than copied.

## What's NOT here (and why)

The screen files (`Budgets.html`, `Goals.html`) are React-UMD pages that load a shared
runtime: `design-canvas.jsx`, `android-frame.jsx`, `tokens.jsx`, `icons.jsx`, `data.jsx`,
`components.jsx`, `shared-components.jsx`, `screens-1.jsx`, plus the per-domain
`budget-components.jsx` / `screens-budgets.jsx` / `goals-components.jsx` / `screens-goals.jsx`.

The MCP only reads one file at a time, so mirroring all 13 is expensive. To render locally,
export the project as a zip from the web UI, drop it in this folder, then:

```
python3 -m http.server 8000   # file:// won't work — babel fetches the .jsx over XHR
```

Artboard width in the mockups is 412×892 (`AndroidDevice`), scaled 0.78 on the canvas.

---

## Budgets — screen inventory (from `Budgets.html`)

| # | Artboard | Component |
|---|---|---|
| 01 | Budgets list | `BudgetsScreen` |
| 02 | Details — warn state (b1) | `BudgetDetailsScreen` |
| 03 | Details — over budget (b3) | `BudgetDetailsScreen` |
| 04 | Details — on track (b4) | `BudgetDetailsScreen` |
| 05 | Edit | `EditBudgetScreen mode="edit"` |
| 06 | Create | `EditBudgetScreen mode="create"` |

### Budget components (`budget-components.jsx`)

- `budgetStatus(spent, limit, alertPercent)` → `ok` / `warn` (≥ alertPercent) / `over` (> 100%)
- `statusColor(state, scheme)` — ok → `primary`; warn → amber `oklch(56%|78% 0.13 68)`; over → `error`
- `BudgetProgressBar` — fill capped at 100%, tick mark drawn at `alertPercent`
- `BudgetRing` — donut, same status colors
- `BudgetCard` — stacked category bubbles (max 3 + `+N`), name, status pill, `spent of limit`,
  remaining, progress bar, footer `period · periodLabel · N days left`
- `PeriodSegmented` — Weekly / Monthly / Yearly
- `CategoryChipGrid` — multi-select + an "All categories" chip (general budget)
- `PercentStepper` — alert threshold, 50..100 step 5
- `StatTile`, `AlertBanner` (warn / over)
- `periodDays(p)` — WEEKLY 7 / MONTHLY 30 / YEARLY 365, used for daily avg + forecast

Design seed data carries `rollover: Boolean` and `alertPercent` per budget — matches the
decisions locked in [../budgets.md](../budgets.md).

---

## Goals — screen inventory (from `Goals.html`)

| # | Artboard | Component |
|---|---|---|
| 01 | Goals · all | `SavingsGoalsScreen` |
| 01b | Goals · active filter | `SavingsGoalsScreen filter="active"` |
| 01c | Goals · paused / archived | `SavingsGoalsScreen filter="archived"` |
| 02 | Details · active | `SavingsGoalDetailsScreen` |
| 02b | Details · actions sheet (pause / archive / delete) | `showActions` |
| 02c | Details · completed | `showCompleted` |
| 02d | Details · paused | `showPaused` |
| 03 | New goal | `EditSavingsGoalScreen mode="create"` |
| 03b | Edit goal | `EditSavingsGoalScreen mode="edit"` |
| 04 | Add money | `GoalContributionScreen mode="add"` |
| 04b | Withdraw | `GoalContributionScreen mode="withdraw"` |

### Goals components (`goals-components.jsx`)

- `GoalIcon` — squircle `32% / 38%`, emoji on hue-tinted fill
- `StatusPill` — ACTIVE (outlined + dot) / COMPLETED (filled + check) / PAUSED / ARCHIVED (neutral)
- `ForecastChip` — on-track / ahead / behind / paused / done, each with icon + hue
- `ProgressBar` — with an **expected-today tick**: fill = money, tick = elapsed time
- `ProgressRing` — hero on details, inner stack `pct → current → of target`
- `GoalCard` — list row; dims to 0.7 opacity when PAUSED/ARCHIVED
- `ContributionRow` — auto (tertiary + Sparkle) vs manual (secondary + Plus)
- `Sparkline` — cumulative saved curve + dashed forecast to target

### Implied Goals domain model (from `goals-data.jsx`)

```
Goal:         id, title, emoji, hue, target, current, currency,
              startDate, targetDate, accountId,
              status: ACTIVE | COMPLETED | PAUSED | ARCHIVED,
              autopay: { amount, cadence, nextOn }?,
              forecast: on-track | ahead | behind | paused | done, etaLabel
Contribution: id, goalId, amount, date, note, auto: Boolean
```

Note `forecast` / `etaLabel` are **derived**, not stored — they need a pace calculation
(elapsed vs saved vs remaining days) that does not exist anywhere in the codebase yet.

---

## Settings — screen inventory (from `Settings.html`)

`Settings.html` loads `design-canvas.jsx`, `android-frame.jsx`, `tokens.jsx`, `icons.jsx`,
`data.jsx`, `components.jsx`, `settings-components.jsx`, `screen-chrome.jsx`,
`settings-screens.jsx`. Only the Android frame is rendered (`Pair` drops the iOS device) —
there is **no iOS variant** of these mockups.

| # | Artboard | Component |
|---|---|---|
| 01 | Settings (hub) | `SettingsHubScreen` |
| 01b | User profile | `UserProfileScreen` |
| 02 | Appearance | `AppearanceScreen` |
| 03 | Preferences | `PreferencesScreen` |
| 04 | Sync | `SyncScreen` |
| 04b | Sync — in progress | `SyncScreen syncing` |
| 05 | Backup | `BackupScreen` |
| 06 | About & legal | `AboutLegalScreen` |

`BackupSyncScreen` is a back-compat alias for `SyncScreen`.

### Settings components (`settings-components.jsx`)

- `SettingsGroup` — primary-tinted section title + gap-stacked cards + optional `footnote`
- `SettingsRow` — 36 px icon tile (radius 10) · title · supporting · trailing slot;
  `danger` recolors title+icon to `error`; `divider` is a **no-op** (separation is the 8 px gap)
- Trailing controls: `SettingsSwitch` (51×31 pill, iOS-style knob), `SettingsRadio` (22 px),
  `SettingsChevron` (rotated `I.Back`), `SettingsValuePill` (value text + chevron)
- `NameBlock` — `primaryContainer` hero: 56 px avatar initial, name, email, trailing chevron
- `ColorPicker` — 5-column seed swatch grid, check on selected, dims + `pointerEvents:none`
  when `disabled` (i.e. while Dynamic colors is on)
- `StatusHeroCard` — tinted 48 px icon + title + supporting; `tone` = primary/secondary/tertiary
- `SyncStatusCard` — `StatusHeroCard` wrapper, two states: "Up to date" / "Syncing…"
- `LiveSchemePreview` — Appearance preview tile, primary/secondary/tertiary dots
- `PendingBadge` — dot + label pill for attention items ("2 pending")

Card radii: rows 16, heroes 20, profile hero 28. Shadow is uniform
`0 2px 6px rgba(16,24,40,.05), 0 1px 2px rgba(16,24,40,.04)`.

The design assumes a **single, online, signed-in app**. See
[../settings_online_offline.md](../settings_online_offline.md) for the mapping onto the
repo's two build variants (`:composeApp` vs `:composeAppOffline`).
