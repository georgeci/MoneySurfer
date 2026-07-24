# Categories — Refactoring Plan

Written 2026-07-21. Unlike [budgets.md](budgets.md) and [goals.md](goals.md), this is not a
greenfield plan: `feature/category` ships today with a manage screen, a creation screen and
a chooser sheet. This document covers closing the gap between what the UI pretends to do
and what it actually persists, and bringing both screens in line with `Categories.html`.

## The core defect

[`CategoryCreationViewModel.saveCategory()`](../feature/category/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/category/creation/CategoryCreationViewModel.kt)
builds:

```kotlin
Category(id, workspaceId, name, type, parentId = null, createdAt)
```

`CategoryCreationState`, meanwhile, holds `selectedIconIndex`, `selectedColorIndex`,
`monthlyCap`, `parent` and `note`, and the screen renders a full icon grid, colour grid and
cap field for them. None of these are read on save, and the domain `Category` has nowhere to
put them. Concretely:

- Picking an icon or a colour and saving discards the choice silently.
- `parentId` is hard-coded to `null`, so a category tree cannot be built through the UI at all.
- `loadCategory()` restores only `name` and `type`, so opening an existing category for edit
  resets the icon and colour pickers to index 0.
- The manage screen does not nest rows by `parentId` either, so even correctly-stored
  parents would render as a flat list.

`CategoryDoc` already carries `parentId` on the wire — only the UI path is missing.

## Decisions (locked)

| # | Question | Decision |
|---|---|---|
| 1 | Who owns a per-category monthly limit? | **Budgets.** No `cap` field on `Category` — a single-category cap is a single-category budget, and two competing limit mechanisms is a product bug waiting to happen. |
| 2 | Quick cap from the category form | Kept as an affordance: the control creates or edits a `Budget` with `categoryIds = [thisCategory]` behind the scenes. The user gets two taps; the system keeps one mechanism. Depends on the Budgets work landing first. |
| 3 | Icon and colour storage | `iconKey: String` (e.g. `"cart"`) and `hue: Int` (0..360). **Not palette indices** — an index is positional, so inserting one icon into the middle of the list silently recolours and re-icons every user's categories. |
| 4 | Phantom fields | Fixed as part of this refactoring, not filed as a separate defect. |
| 5 | `note` field | Dropped. It is not in the design, not in the domain, and not persisted today. |
| 6 | Detail screen | Separate task — it is a new feature, not a refactor. |
| 7 | `CategoryType.TRANSFER` / `systemKind` | Stays as-is. System categories remain non-editable and are not part of the redesign. |

## Screens

| Screen | Mockup | Local state |
|---|---|---|
| `CategoryManageScreen` — view + edit modes, tree | Manage · view, Manage · edit | exists, flat list only |
| `CategoryCreationScreen` — type, name, parent, icon, colour, cap | New category | exists, most fields non-functional |
| `CategoryDetailScreen` — hero, 6-month bars, subcategories, recent transactions | 4 detail variants | **does not exist** |

The chooser sheet (`picker/`) has no mockup counterpart but consumes the same icon and
colour data, so it inherits the change.

---

## Phase 0 — Domain and storage

Add `iconKey: String` and `hue: Int` to `Category` and `CategoryEntity`. Additive Room
migration, `MONEY_SURFER_DB_VERSION` bump.

Existing rows have neither. A `NULL`/empty backfill would render every pre-existing category
in one identical colour, which looks broken. Backfill deterministically instead — derive a
starting hue and icon from a stable hash of the category id against the eight-hue palette, so
existing data comes out varied and stays stable across devices.

## Phase 1 — Sync, deployed in the right order

Add `iconKey` and `hue` to `CategoryDoc`, update the mappers, and extend
`hasValidCategoryShape()` at [firestore.rules:163](../firestore.rules).

**Sequencing matters here.** If the rules still validate the old shape when an updated client
first writes a category carrying `iconKey`, the write is rejected and category sync breaks for
that user. Deploy the rules change before the app build that writes the new fields, and keep
the fields optional in the guard so older clients continue to validate.

Extend the `firestore-tests` suite for both the old and the new shape.

## Phase 2 — Make the creation screen honest

- Persist `iconKey`, `hue` and `parentId` in `saveCategory()`, for both the create and the
  edit branch.
- Restore all of them in `loadCategory()`.
- Replace the free-text parent field with a real category picker, filtered to the same type
  and excluding the category itself and its descendants — a category cannot be its own
  ancestor.
- Remove the `note` field and the `cap` field from the UI and from `CategoryCreationState`.
- Update `CategoryCreationViewModelTest` to cover the round-trip: pick icon, colour and
  parent, save, reload, assert they survive.

## Phase 3 — Manage screen tree

Render children nested under their parent, per the mockup, with the view/edit mode split.
Deleting a parent needs a defined outcome — reparent children to the root or block the delete
while children exist. `DeleteCategoryDialog` currently assumes a flat world.

## Phase 4 — One icon and colour resolver

`SurferCategoryBubble` already ships an eight-tint palette whose hues match the design exactly
(162, 35, 258, 68, 303, 340, 8, 90) — no palette work is needed. What is missing is a single
resolver from stored `iconKey` + `hue` to an icon and a tint, used by every call site.

Today only `CategoryCreationScreen` indexes into the palette, and it does so for its own
preview. The transaction creation and details screens receive `icon` and `tint` from their
callers and therefore cannot reflect any per-category choice. All three must go through the
shared resolver once the fields exist.

## Phase 5 — Strings and tests

Russian and English for any new copy. Unit coverage on the resolver, the backfill hash and
the parent-cycle guard.

---

## Quick cap — what the control does (issue #248)

Shipped. The category form's monthly-cap field writes a `Budget` with
`categoryIds = [thisCategory]`, `period = MONTHLY` and `startDate = today`, named after the
category. Typing an amount creates it, changing the amount updates it, clearing the field deletes
it. Nothing is stored on `Category`.

Which budget the control speaks for is decided by `resolveCategoryCapCoverage()` in
[domain/model/CategoryCapCoverage.kt](../domain/src/commonMain/kotlin/com/georgeci/moneysurfer/domain/model/CategoryCapCoverage.kt),
so the screen and the write path cannot disagree:

| Budgets naming this category | Control |
|---|---|
| none | editable, empty — saving an amount creates the backing budget |
| exactly one, naming only this category | editable, prefilled — that budget *is* the cap |
| one naming other categories too, or more than one | **read-only**, naming the budget: "Set by the … budget" |

**The read-only case is the decision the issue asked for.** A category already inside a shared
budget must not also get a cap from the shortcut — that is two limits on one category with no
defined winner, exactly what decision 1 exists to prevent. The form says which budget owns the
limit and sends the user to Budgets to change it, rather than silently creating a second one.
The write path re-resolves coverage instead of trusting the screen, so a budget created while the
form was open cannot be overwritten by a stale state.

Two kinds of budget deliberately do **not** count as coverage:

- **archived** ones (`isActive = false`) — they limit nothing, so a cap set now is a fresh budget
  rather than a resurrection;
- the **all-categories** budget (empty `categoryIds`) — it is the global spending envelope that
  per-category budgets already live inside, not a competing per-category limit. Counting it would
  leave the shortcut permanently dead for anyone who keeps a general budget.

The control is offered for expense categories only, since a budget caps spending. Converting an
expense category to income on the form deletes its backing budget along with it.

## Follow-up work, tracked separately

- **`CategoryDetailScreen`** — hero, six-month trend bars, subcategory breakdown and recent
  transactions. Needs per-month aggregate queries that do not exist yet, plus a new route.
