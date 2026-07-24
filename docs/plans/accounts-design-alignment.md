---
title: Accounts — align mockups and implementation
created: 2026-07-24
status: backlog
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
need to be built. Roughly 80% of the work is finishing what is already there.

Three items require a domain-model migration and therefore count as features
rather than fixes. The rest are small corrections. A separate design pass is
needed first, because parts of the mockups contradict the shipped model and
would otherwise force a schema guess.

## Current model

`domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/model/Account.kt:10`

```
Account(id, workspaceId, name, type, currencyCode, balance, archived, updatedAt)
type ∈ { CASH, BANK, CARD, SAVINGS }
```

No `icon`, `kind`, `last4`, `archivedAt` or sort order. The mockups render all
of them.

## A. Features (require model migration)

### A1. Persist "Extra details"

The creation screen already renders chips for IBAN, Description, BIC/SWIFT,
Card last 4, Bank URL and Branch phone, but `saveAccount` in
`AccountCreationViewModel.kt:154` ignores `extraFields` entirely. The user fills
the form and the data silently disappears.

Needs a key–value store (the design also has a "Custom field…" chip with a
user-supplied name, so fixed columns will not do), a sync DTO, a Room migration,
and a place on the details screen to actually display the values — currently
nothing ever shows them again.

### A2. Sort order / drag-to-reorder

Edit mode already draws `SurferDragHandle` and the "Drag to reorder" label in
`AccountsManageScreen.kt:314`, but no reorder is implemented. Needs a
`sortOrder` field, a DAO reorder operation, ordering in sync, and the drag
interaction.

### A3. Real 30-day balance chart

`SurferBalanceChartCard` on the details screen is a placeholder fed by static
strings (`AccountDetailsScreen.kt:180`). Needs a use case producing a daily
balance series plus the period delta.

## B. Small fixes

| What | Where | Issue | Status |
|---|---|---|---|
| Default account type | `AccountNavGraph.kt:91` | code `SAVINGS`, mockup `Bank` | done (#307) |
| Currency selector on creation | `AccountCreationScreen.kt:249` | absent from the mockups — decide: drop it or keep as first-run only | open — needs the design pass |
| Hardcoded "synced" label | `AccountDetailsScreen.kt:170` | `Synced 2 min ago` is a literal; no sync state behind it | done (#307) — label removed, hero param now optional |
| Details overflow menu | `AccountDetailsScreen.kt:130` | `/* wires later */` — also undesigned | open — needs the design pass |
| Details hero meta line | `AccountDetailsScreen.kt` | mockup shows `CURRENT · •• 4021`, code shows the currency code | partly done (#307) — kind shows, `last4` still has no field |
| Archived rows | `AccountsManageScreen.kt:227` | mockup shows "Archived Nov 2024"; no `archivedAt` field | done (#307) — `archivedAt` persisted and synced, DB v29 |
| Chooser sheet | `AccountChooserContent.kt` | missing the "Total across N accounts" subhead, the "Add new account" row and the "Transfer between accounts instead" footer | done (#307) — footer added; the other two already existed |
| Multi-currency total | `DashboardViewModel.kt:86` | sums only accounts matching the first account's currency — silently wrong | done (#307) — one total per currency |
| Missing use cases | ViewModels | create/update/delete call `AccountRepository` directly, unlike archive/restore | done (#307) |

## C. Design questions (blocking A1/A2)

- `kind` ("Current", "Credit") duplicates `type` ("Bank", "Card") and the two
  disagree per row. Drop it, or make it an enterable field.
- `last4` is rendered on CASH and SAVINGS accounts in the seed data. Which types
  can carry it, and is it the same thing as the "Card last 4" extra field?
- No currency control anywhere, yet the model is per-account and the seed data
  contains a USD account; `fmt()` hardcodes `€`.
- No empty state for the manage list.
- No archive-confirm dialog in the design, but one exists in code
  (`ArchiveAccountDialog`).
- Three details-hero variants (`hero`, `stack`, `split`) — one must be chosen.
- No edit-account frame, no "Custom field…" naming step, no offline-build
  variant (extra details are hidden offline).

## Design prompt

Sent to the design project to resolve section C:

```
Revise the Account screens (Accounts.html — screens-1.jsx, screens-2.jsx,
account-components.jsx, account-data.jsx) to remove divergences with the shipped
data model, and add the frames that are currently missing. Do not redesign the
visual language — keep the existing tokens, rows and layout. This is a
consistency pass.

## 1. The account model is the source of truth

The implemented model is exactly:

  Account(id, workspaceId, name, type, currencyCode, balance, archived, updatedAt)
  type ∈ { CASH, BANK, CARD, SAVINGS }

Everything the mockups render must either map onto this, or be explicitly
introduced as a new field with a stated purpose. Please resolve each of these:

- `kind` ("Current", "Savings", "Cash", "Credit") duplicates `type`
  ("Bank", "Savings", "Cash", "Card") and the two disagree per row. Pick one:
  either drop `kind` and render the `type` label everywhere, or define `kind` as
  a distinct user-editable sub-label and add a control for it on the creation
  screen. Right now it appears in details hero, manage rows and the chooser
  sheet with no way to enter it.
- `last4` is shown on every account including CASH and SAVINGS in the seed data.
  State which types can carry it. If it is the "Card last 4" optional extra
  field, show it only when that field is filled, and update account-data.jsx so
  the Cash / Savings rows have `last4: null`.
- `archivedAt` ("Archived Nov 2024") is display-only in the mockups. Confirm it
  should be persisted, and specify the date format for both EN and RU.
- Drag-to-reorder implies a stored sort order. Add a frame showing the mid-drag
  state (lifted row + drop indicator + where the row lands), so the interaction
  is specified, not just hinted at by the handle.

## 2. Currency

The mockups have no currency control anywhere, but the model has a per-account
`currencyCode` and the seed data contains a USD account. Meanwhile `fmt()` and
AccountRowLarge hardcode "€" (AccountRowLarge only special-cases USD → "$"), so
the Amex Gold row renders a USD balance with a euro sign in some places.

Decide and reflect it in the frames:
- If currency is per account: add the currency control to the New account screen
  (where in the order? before or after the opening balance?) and make every
  amount render its own account's symbol.
- If currency is workspace-level: remove `currencyCode` from account rows and
  show it once, in the aggregate strip.
Also specify what the "Total balance" strip shows when accounts have mixed
currencies — the current design shows a single summed number, which is wrong.

## 3. Missing frames — please add

- **Edit account.** The mockups only have "New account", and the details screen
  has a pencil that leads nowhere. Add the edit variant: title, which fields are
  editable vs locked (the implementation currently locks currency and opening
  balance in edit mode), and where "Delete account" lives.
- **Overflow menu on Account details.** The `I.More` icon in the top bar has no
  menu attached in any file. Design the menu and its items.
- **Extra details after creation.** The New account screen collects IBAN,
  Description, BIC/SWIFT, Card last 4, Bank URL, Branch phone — and no screen
  ever displays them again. Add the section that surfaces them on Account
  details (and specify masking for IBAN, and whether it is copyable).
- **"Custom field…" flow.** The dashed chip exists but the naming step does not.
  Show the sheet/dialog where the user names the field, plus its validation
  (max length, duplicate names) and how a custom field renders in the list.
- **Empty state for Manage accounts** (zero accounts, and zero archived
  accounts) — neither exists today.
- **Archive confirmation.** The implementation shows a confirm dialog before
  archiving; there is only a Delete dialog in the design. Either design the
  archive confirm, or state that archive is immediate with Undo in a snackbar
  (and design that snackbar).
- **Offline build variant of New account.** In the offline build the entire
  "Extra details" section is hidden. Show that frame.

## 4. Pick one details hero

AccountDetailsScreen has three hero variants (`hero`, `stack`, `split`). Choose
the canonical one, delete the other two, and note the choice in the frame label.

## 5. Sync status is a fiction

"Synced 2 min ago · Tap for full history", "Synced 2m" and the green "LIVE"
badge are hardcoded strings with no state behind them. Either specify the full
set of states — never synced, syncing, synced, offline, error — as designed
chips, or remove the sync affordance from the account screens entirely (and say
which, so the copy can be deleted from the implementation).

## 6. Copy

Provide the RU translation alongside every EN string on these screens, and show
at least one row with a long account name and a long custom-field name so the
truncation behaviour is specified.

Keep the changes inside the account files; do not touch the shared design-system
components unless a fix is genuinely shared.
```

## Suggested order

1. Design alignment pass (section C) — unblocks the schema.
2. A1 persist extra details — a finished UI that currently lies to the user.
3. A2 sort order.
4. A3 balance chart.
5. Section B fixes — one PR.
