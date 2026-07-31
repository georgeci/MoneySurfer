# Design notes — Sign in, Budgets, Goals, Settings, Transactions & Accounts

Notes taken from the **MoneySurfer** Claude Design project:
<https://claude.ai/design/p/019dd9e9-bcad-78f3-a4b9-49cde06a75ac>

## No design sources in this repo

Source files from the design project — `*.jsx`, `*.html`, exported assets — are **not
committed here**. They are someone else's authored content, they are large, and they go
stale the moment the design moves. `md/design/*.jsx` and `*.html` are gitignored.

What lives in this folder is our own reading of the mockups: screen inventories, component
behaviour, and the implied domain model — enough to implement against without opening the
canvas, and small enough to review in a diff.

To work with the sources, pull them into an untracked scratch folder (the design MCP reads
one file at a time), or export the project as a zip from the web UI and serve it:

```
python3 -m http.server 8000   # file:// won't work — babel fetches the .jsx over XHR
```

The screens are React-UMD pages over a shared runtime: `design-canvas.jsx`,
`android-frame.jsx`, `tokens.jsx`, `icons.jsx`, `data.jsx`, `components.jsx`,
`shared-components.jsx`, `screens-1.jsx`, plus the per-domain `*-components.jsx` /
`*-data.jsx` / `screens-*.jsx`.

Artboard width in the mockups is 412×892 (`AndroidDevice`), scaled 0.78 on the canvas.

---

## Sign in — screen inventory (from `Sign In.html`)

`Sign In.html` loads `design-canvas.jsx`, `android-frame.jsx`, `tokens.jsx`, `icons.jsx`,
`components.jsx`, `screens-signin.jsx`. One component, `SignInScreen`, with two boolean props.

| # | Artboard | Frame | Component |
|---|---|---|---|
| 01 | Sign in · phone | 412 × 892 | `SignInScreen` |
| 01b | Sign in · short phone | 412 × 740 | `SignInScreen compact` |
| 01c | Sign in · tablet | 834 × 1040 | `SignInScreen` |
| 01d | Sign in · small tablet, **landscape** | 1024 × 700 | `SignInScreen landscape` |

The screen deliberately opts out of the seed picker — its greens are the fixed brand palette
(`AuthColors` in the repo), which is why the canvas' Tweaks panel says so out loud.

### The two layouts

**Stacked** (01, 01b, 01c) — brand block at the top, flexible spacer, wave along the bottom,
white sheet pinned above it. Note that the *tablet-portrait* artboard uses this layout unchanged:
width alone does not earn the split.

**Split** (01d) — a `flex` row over a `120deg` gradient instead of the stacked `180deg` one:

- left column `flex: 1`, vertically centred, padding `48px 40px`; logo 44 px + wordmark 21 px,
  hero title 52 px / 1.02 / 800 weight, subtitle 16 px capped at 380 px
- right column `width: 46%`, `max-width: 460`, padding `24px 28px 24px 0`, vertically centred,
  holding the same white sheet as a card (radius 28, the same shadow)
- the wave still spans the full width along the bottom, so it runs *behind* the left column and
  *under* the card rather than being covered by it

Shipped as the `SignInSplitLayout` branch in
[SignInScreen.kt](../../feature/login/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/login/SignInScreen.kt),
selected by `isSplitLayout(width, height)` — at least `SurferWindowSize.Expanded` **and** wider
than tall. The height half has no counterpart in the mockups: the canvas only ever draws fixed
artboards, so nothing there distinguishes a 1024 dp tablet lying down from the same tablet stood
up, and splitting the upright one would leave two tall, half-empty columns.

Deliberate departures from the mockup:

- **A max width of 1120 dp on the split row**, centred (`surferContentContainer`). The design's
  percentage columns were drawn at 1024 dp; left unbounded they keep growing, and at 2560 dp the
  brand text and the card end up a screen apart.
- **The sheet scrolls independently** of the brand column. A phone on its side is ~410 dp tall,
  which is expanded-width and landscape, and the form is taller than that.
- **No `compact` variant.** 01b drops the hero subtitle and shrinks the title on short phones; the
  shipped stacked layout scrolls instead, so nothing is unreachable. Still open.
- The hero keeps its own line breaking. The mockup hard-wraps `Ride your<br/>money wave.`; the
  string resource carries no break, so wide layouts wrap it as `Ride your money / wave.`

The gradient direction is a `SurferAuthGradient` parameter on `SurferAuthBackground` (uikit), not
a sign-in local: on a short, wide window a vertical ramp is compressed into no travel and reads as
a flat fill. Onboarding, the other caller, keeps the vertical default.

Reference frames: `feature/login/screenshots/sign_in_{compact,expanded,large}_{light,dark}.png`.

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

## Transactions — period pager (`screen-chrome.jsx`, `period-pager.html`)

Reference for [issue #261](https://github.com/georgeci/MoneySurfer/issues/261): the transactions
list gained a period pager and a period-scoped summary strip. Shipped as
`uikit/.../components/base/SurferPeriodPager.kt`.

`PeriodPager` — a 40dp pill on `surfaceContainerLow`, radius 20, 6dp horizontal padding, with
32dp circular arrow slots tinted `onSurfaceVariant` (40% alpha when disabled) and a centred
`titleSmall`/600 label plus a `labelSmall`/400 sub-label, baseline-aligned, 6dp apart.

| Mode | Label | Sub-label | Arrows |
|---|---|---|---|
| Month | `March` | `2025` | both |
| Week | `Mar 25 – 31` | `W13 · 2025` | both |
| All time | `All time` | `No date filter` | disabled |

Two deliberate departures from the design in the shipped screen:

- `TransactionsScreen` **hides** the pager in all-time mode; the app keeps it visible with
  disabled arrows (the `period-pager.html` all-time variant) because the pill's label is also
  the period-mode menu — hiding it would strand the user with no way back.
- The design never drew a mode switcher at all. Tapping the label opens a `DropdownMenu`
  (Month / Week / All time); the choice persists via `UiPreferences.transactionsPeriodMode`.

`SummaryStrip` is the existing Income · Expenses · Net strip, unchanged visually — the change is
that its numbers now come from a SQL aggregation over the selected period rather than a fold over
the whole loaded history.

The design's search field and filter badge (`txn-screens.jsx` lines 642–680) are **not** built yet.

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

---

## Accounts — screen inventory (from `Accounts.html`)

Screens live in `screens-1.jsx` (creation + manage) and `screens-2.jsx` (details + chooser),
with rows in `account-components.jsx` and seed data in `account-data.jsx`.

| # | Artboard | Component |
|---|---|---|
| 01 | New account | `AccountCreationScreen` |
| 02 | New account · currency sheet | `AccountCreationScreen currencyOpen` |
| 03 | New account · name custom field | `AccountCreationScreen customFieldOpen` |
| 04 | Manage accounts — edit mode | `AccountManageScreen` |
| 05 | Manage accounts — view mode | `AccountManageScreen editing={false}` |
| 06 | Manage · reorder (mid-drag) | `AccountManageScreen reorder` |
| 07 | Manage · empty state | `AccountManageScreen empty` |
| 08 | Account details | `AccountDetailsScreen` |
| 09 | Account details · ⋮ overflow menu | `AccountDetailsScreen menuOpen` |
| 10 | Edit account | `AccountEditScreen` |
| 11 | Account chooser sheet | `AccountChooserSheetScreen` |
| 12 | Archive confirm alert | `ArchiveAccountDialog` |
| 13 | Delete confirm alert | `DeleteAccountDialog` |

The canvas also carries a components gallery (`AccountRowLarge`, `AccountRowCompact`,
`TxnTypeFilter`, `TxnLine`, truncation behaviour) and an EN → RU copy table (`ACCT_COPY`).

Artboards 02–13 arrived with the first design-alignment pass. Of the ones that were
missing before it, only **02** has been read into these notes (the currency picker below);
**03, 06, 07, 09, 10 and 12** are listed above but their content is untranscribed — anyone
implementing against them should transcribe the frame here first. What is still unanswered
(`kind` vs `type`, `last4`, custom fields, the account icon) and the round-2 prompt live in
[../../docs/plans/accounts-design-alignment.md](../../docs/plans/accounts-design-alignment.md).

### Account components

- `ACCOUNT_TYPE_META` — CASH → "Cash"/hue 162, BANK → "Bank"/258, CARD → "Card"/303,
  SAVINGS → "Savings"/68; drives icon tile tint everywhere
- `AccountRowCompact` — 40 dp squircle (`32% / 38%`) tile, name, `{type} · •• {last4} · {ccy}`,
  flexible trailing slot; `selected` fills `secondaryContainer`
- `AccountRowLarge` — hero card, 24 dp radius, hue-tinted fill, uppercase eyebrow, big
  `SplitAmount`, optional footer slot
- `DeleteAccountDialog` — M3 basic dialog, error-tinted trash badge, Cancel / Delete

### Currency picker (artboard 02) — resolves [#277](https://github.com/georgeci/MoneySurfer/issues/277)

The design replaced the equal-width segmented control with a field that opens a modal sheet:

- **Trigger** — 56 dp outlined field, 12 dp radius, symbol badge (24 dp, `titleMedium`/600),
  `{name} · {code}` in `bodyLarge`, trailing chevron. The outline turns `primary` while the
  sheet is open. Shipped as `uikit/.../components/SurferCurrencyPickerField.kt`.
- **Sheet** — 28 dp top radii on `surfaceContainerLow`, drag handle, `Currency` title, then one
  card per currency (symbol · name over code · check when selected) at 16 dp side padding with
  a 10 dp gap. Shipped as `SurferCurrencyBottomSheet` + `SurferCurrencyRow`.

One deliberate departure: the mockup has no search field, the shipped sheet keeps one. The
supported list is already eight currencies and still growing — that growth is exactly why the
segmented control was dropped. The list is capped at 420 dp so short lists stay compact.

Used by both account creation and workspace creation, which had the same cramped control.

### Fields the mockups render that the domain model does not have

`Account(id, workspaceId, name, type, currencyCode, balance, archived, updatedAt)` has no
`icon`, `kind` ("Current"/"Credit", which duplicates and contradicts `type`), `last4`,
`archivedAt`, sort order, per-account sync state, or the "Extra details" set
(IBAN, description, BIC, card last 4, bank URL, branch phone, custom fields).

The creation screen collects those extra details and nothing ever persists or displays them.
Full gap analysis and the design-alignment prompt:
[../../docs/plans/accounts-design-alignment.md](../../docs/plans/accounts-design-alignment.md).
