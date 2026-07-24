---
title: Accounts — align mockups and implementation
created: 2026-07-24
updated: 2026-07-24
status: design pass — round 2
---

# Accounts — align mockups and implementation

Review of the Account creation / edit / view / manage sections against the
Claude Design project `MoneySurfer` (`Accounts.html`).

- Screen and component inventory: [md/design/README.md](../../md/design/README.md#accounts--screen-inventory-from-accountshtml).
  The design sources themselves (`Accounts.html`, `screens-1.jsx`, `screens-2.jsx`,
  `account-components.jsx`, `account-data.jsx`) are deliberately not committed.
- Design project:
  https://claude.ai/design/p/019dd9e9-bcad-78f3-a4b9-49cde06a75ac?file=Accounts.html

## Verdict

Every screen already exists in code — creation (`AccountCreationScreen`), edit
(reuses creation), details (`AccountDetailsScreen`), manage
(`AccountsManageScreen`), chooser (`AccountChooserBottomSheet`). No new screens
need to be built.

Round 1 of the design prompt has landed: the canvas grew from 6 artboards to 13
and the currency question is settled and shipped. What is left is a smaller,
sharper set — four contradictions that still have no answer, and seven new
artboards whose *content* nobody has read into the repo yet. Both are listed
below, and section [Design prompt — round 2](#design-prompt--round-2) is the
text to send back.

Until those four answers exist, the extra-details and sort-order schema work
stays blocked: it would have to guess at `kind`, at `last4`, and at what a
custom field is allowed to be.

## Current model

`domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/model/Account.kt:10`

```
Account(id, workspaceId, name, type, currencyCode, balance, archived, updatedAt, archivedAt)
type ∈ { CASH, BANK, CARD, SAVINGS }
```

`archivedAt` was added by PR #317 (DB v29), which closed issue #307. Still absent: `icon`, `kind`, `last4`,
sort order, per-account sync state, and any store for the "Extra details" set
(IBAN, description, BIC/SWIFT, card last 4, bank URL, branch phone, custom
fields).

## What shipped since the first review (PR #308)

| Item | Where | State |
|---|---|---|
| Default account type `BANK` | `AccountNavGraph.kt` | PR #317 (closed issue #307) |
| `archivedAt` persisted + synced, "Archived Nov 2024" row | `AccountsManageScreen.kt:396` | PR #317 |
| Hardcoded "Synced 2 min ago" removed; `syncedLabel` now optional | `SurferAccountDetailsHeroCard.kt:60` | PR #317 |
| Chooser footer "Transfer between accounts instead" | `AccountChooserContent.kt:166` | PR #317 |
| Per-currency dashboard totals (was: summed across currencies) | `DashboardViewModel.kt` | PR #317 |
| Create/update/delete moved behind use cases | `domain/usecase/*AccountUseCase.kt` | PR #317 |
| Currency picker field + bottom sheet | `SurferCurrencyPickerField.kt`, `SurferCurrencyBottomSheet.kt` | PR #312, resolves issue #277 |
| Real 30-day balance chart (was static strings) | `GetAccountBalanceSeriesUseCase.kt`, `AccountDetailsScreen.kt:179` | PR #313 |
| Manage-list empty state | `AccountsManageScreen.kt:170` | done |
| Extra-details section hidden in the offline build | `AccountCreationViewModel.kt:63` (`extraDetailsEnabled`) | done |
| WCAG AA semantic accents | uikit | PR #315 |

So sections A3 and most of B from the first review are closed. A1 (persist
extra details) and A2 (sort order) remain, both behind the design questions.

## Still open in code

### A1. Persist "Extra details"

`AccountCreationScreen.kt:227` renders the chips and
`AccountCreationViewModel.kt:140-155` maintains them in state, but `saveAccount`
(`AccountCreationViewModel.kt:180-215`) constructs `Account(...)` without them —
the values are dropped on save, and no screen would display them anyway.

`AccountExtraFieldKind` (`AccountCreationViewModel.kt:295`) is a closed enum of
six kinds and lives in the feature module. There is **no `CUSTOM` kind and no
"Custom field…" chip in the shipped code** — that part of the mockup was never
built, which is why the naming step has never been specified.

Needs: a key–value store (fixed columns will not do if custom fields survive),
a sync DTO, a Room migration, and a display section on details.

### A2. Sort order / drag-to-reorder

`AccountsManageScreen.kt:320` draws `SurferDragHandle` and the "Drag to reorder"
label; no `sortOrder` symbol exists anywhere in the Kotlin sources. Needs a `sortOrder`
field, a DAO reorder operation, ordering in sync, and the drag interaction.

## Contradiction ledger — mockups vs shipped model

| # | Contradiction | State | Recommendation |
|---|---|---|---|
| C1 | `kind` ("Current"/"Credit") duplicates `type` ("Bank"/"Card") and disagrees per row | **open** | drop `kind`; render the `type` label |
| C2 | `last4` rendered on CASH and SAVINGS rows, with no way to enter it | **open** | make it the CARD-only projection of the "Card last 4" extra field |
| C3 | No currency control; `fmt()` hardcodes `€` while seed data has a USD account | **resolved** — per-account picker shipped (PR #312), `MoneyFormatter.format(money, currencyCode)` everywhere | design seed data still needs the euro sign fixed |
| C4 | Three details-hero variants (`hero` / `stack` / `split`) | **open** | code already implements `hero` (`SurferAccountDetailsHeroCard.kt:51`) — confirm and delete the other two |
| C5 | "Synced 2 min ago" / "LIVE" are hardcoded strings | **half-resolved** — labels removed from code; real state exists app-wide (`SurferSyncStatus` = Hidden/Syncing/Synced/Failed, fed by `SyncStatusProvider`, rendered in the toolbar) but is **workspace-level, not per-account** | drop the per-account affordance; the toolbar badge is the sync surface |
| C6 | `archivedAt` display-only in the mockups | **resolved** — persisted, DB v29; rendered as `{type} · Archived {Mon} {YYYY}` | design should state the RU date format |
| C7 | Account `icon` is per-account in the mockups | **open, minor** | code derives the icon from `type` (`AccountsManageScreen.kt:411`), except the details hero which hardcodes `SurferIcons.Wallet` (`AccountDetailsScreen.kt:165`) — either confirm type-derived (then fix the hero) or specify a picker |
| C8 | Manage-list empty state absent from the mockups | **resolved in code** (`AccountsManageScreen.kt:170`); artboard 07 now exists in the canvas but has not been read | transcribe and reconcile |
| C9 | Archive confirm exists in code (`ArchiveAccountDialog`), was absent from the design | **resolved in the canvas** — artboard 12; content not transcribed | transcribe; if the design says "immediate + Undo snackbar", that is a code change |

C1, C2, C4 and the custom-field question are the four that block the schema.
C1/C2 decide whether the migration carries two extra columns or none; the
custom-field answer decides key–value versus fixed columns.

## Frame inventory — what round 1 produced

The canvas went from 6 artboards to 13. Only artboard 02 has been read into the
repo (the currency picker note in `md/design/README.md`); the rest are listed in
the inventory table but their content is untranscribed.

| # | Artboard | Was missing? | Read into the repo? |
|---|---|---|---|
| 01 | New account | no | yes |
| 02 | New account · currency sheet | yes | **yes** — shipped as `SurferCurrencyPickerField` / `SurferCurrencyBottomSheet` |
| 03 | New account · name custom field | yes | no |
| 04 | Manage accounts — edit mode | no | yes |
| 05 | Manage accounts — view mode | no | yes |
| 06 | Manage · reorder (mid-drag) | yes | no — needed by A2 |
| 07 | Manage · empty state | yes | no |
| 08 | Account details | no | yes |
| 09 | Account details · ⋮ overflow menu | yes | no — `AccountDetailsScreen.kt:129` is still `/* wires later */` |
| 10 | Edit account | yes | no |
| 11 | Account chooser sheet | no | yes |
| 12 | Archive confirm alert | yes | no |
| 13 | Delete confirm alert | no | yes |

Two asks from round 1 have **no artboard at all** and must be repeated:

- **Extra details displayed after creation** — nothing in the inventory surfaces
  IBAN / BIC / phone on Account details. Without it, A1 has no target screen.
- **Offline-build variant of New account** — the section is hidden when
  `OfflineBuildFlags.isOffline`; no frame shows what the screen looks like then.

Also still unconfirmed from round 1: the RU copy table (`ACCT_COPY` exists on the
canvas but has not been checked for coverage of the new frames) and the
long-name / long-custom-field truncation rows.

## Decisions to make

Each of these needs a yes/no from the design side before the migration is
written. The recommendation is what the implementation will assume if the answer
does not come back.

1. **`kind`** — drop it, or promote it to a user-editable sub-label with a
   control on creation? *Assume: drop.* It has no entry point, contradicts
   `type` in the seed data, and `type` already drives icon, tint and label.
2. **`last4`** — which types may carry it, and is it the same value as the
   "Card last 4" extra field? *Assume: same value, CARD only, shown in the row
   sub-line only when filled.*
3. **Custom fields** — do they survive? If yes: max name length, duplicate-name
   rule, max count, and how they render on details. *Assume: yes, hence a
   key–value store rather than columns.* This is the single most expensive
   answer — fixed columns are cheaper and irreversible.
4. **Details hero** — confirm `hero`, delete `stack` and `split`.
5. **Per-account sync** — confirm the affordance is gone for good (the toolbar
   badge stays). *Assume: gone.*
6. **Extra-details display** — masking rule for IBAN, copy-to-clipboard or not,
   and whether the section is hidden or shown-empty when nothing is filled.
7. **Archive** — confirm dialog (current code) or immediate + Undo snackbar?
8. **Icon** — type-derived (then the details hero is a bug) or per-account?

## Design prompt — round 2

Round 1 is answered by the current canvas; this is the follow-up. Send as-is.

```
Second consistency pass on the Account screens (Accounts.html — screens-1.jsx,
screens-2.jsx, account-components.jsx, account-data.jsx). The first pass landed:
the currency picker (artboard 02) is shipped, and artboards 03, 06, 07, 09, 10
and 12 now exist. Thank you. This round is narrower — four decisions and two
frames that are still missing. Again: no visual redesign, keep the existing
tokens, rows and layout.

## 1. Four decisions the schema is waiting on

The implemented model is exactly:

  Account(id, workspaceId, name, type, currencyCode, balance, archived,
          updatedAt, archivedAt)
  type ∈ { CASH, BANK, CARD, SAVINGS }

A migration for extra details and sort order cannot be written until these are
settled. Please answer each explicitly, in the frame labels or a notes block:

- `kind` ("Current", "Savings", "Cash", "Credit") still duplicates `type`
  ("Bank", "Savings", "Cash", "Card"), and the two still disagree row by row in
  account-data.jsx. There is no control anywhere that enters it. Either delete
  `kind` and render the `type` label in the details hero, the manage rows and
  the chooser sheet, or add the control that sets it and say what it means next
  to `type`. We would prefer deleting it.
- `last4` is still rendered on CASH and SAVINGS rows. State which types may
  carry it. If it is the "Card last 4" optional extra field, show it only when
  that field is filled, and set `last4: null` on the Cash / Savings rows in
  account-data.jsx.
- Custom fields: does the "Custom field…" chip survive? If yes, artboard 03
  needs to specify max name length, what happens on a duplicate name, the
  maximum number of custom fields per account, and how one renders in the
  details list. If no, remove the chip — a fixed set of six kinds is much
  cheaper to store. This answer decides key–value storage versus columns, so it
  is the one we most need.
- Account icon: is it derived from `type` (as the implementation does
  everywhere except the details hero), or is it a per-account choice? If
  per-account, design the picker; otherwise we will fix the hero.

## 2. Two frames that are still missing

- **Extra details on Account details.** New account collects IBAN, Description,
  BIC/SWIFT, Card last 4, Bank URL, Branch phone — and no artboard displays
  them afterwards. Add the section: order of rows, IBAN masking (which digits
  are shown), whether values are copyable, and what the section looks like when
  nothing is filled (hidden, or shown empty).
- **Offline variant of New account.** In the offline build the whole "Extra
  details" section is hidden. Show that frame so the spacing below the balance
  field is specified.

## 3. Confirmations

- **Details hero.** The implementation uses the `hero` variant. Please confirm
  it as canonical and delete `stack` and `split` from the file.
- **Per-account sync.** "Synced 2 min ago · Tap for full history", "Synced 2m"
  and the green "LIVE" badge have been removed from the implementation. Real
  sync state exists, but it is workspace-level (syncing / synced / failed /
  hidden) and lives as a badge in the top bar. Please confirm the account
  screens carry no sync affordance of their own, and remove those strings from
  the mockups.
- **Archive.** Artboard 12 now exists. Confirm whether archive shows a confirm
  dialog (which is what the implementation does today) or is immediate with an
  Undo snackbar — if the latter, design the snackbar.
- **Reorder.** Artboard 06 shows the mid-drag state. Confirm that the order is
  persisted per workspace and applies everywhere accounts are listed (manage,
  chooser sheet, dashboard), not just in the manage screen.
- **Archived date.** The row reads "{Type} · Archived Nov 2024". Give the RU
  form of that string and the month abbreviation set for both languages.

## 4. Copy and truncation

Round 1 asked for RU alongside every EN string and for long-name rows; the
copy table (ACCT_COPY) does not yet cover the new artboards (03, 06, 07, 09,
10, 12). Please extend it, and include one row with a long account name and one
with a long custom-field name so truncation is specified.

Keep the changes inside the account files; do not touch the shared design-system
components unless a fix is genuinely shared.
```

## Suggested order

1. Send the round-2 prompt; transcribe artboards 03, 06, 07, 09, 10, 12 into
   [md/design/README.md](../../md/design/README.md) as they are answered.
2. A1 persist extra details — a finished UI that currently drops user input.
   Blocked on decisions 1–3 and 6.
3. A2 sort order. Blocked on artboard 06 only.
4. Overflow menu on Account details (artboard 09) — the last `/* wires later */`
   on these screens.
