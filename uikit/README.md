# uikit

MoneySurfer design system. Reusable Compose Multiplatform components, theme, tokens.

## Conventions

- **Atoms are `internal`** — base building blocks live inside the module. Public composables wrap them.
- **Pure container slots** — atoms don't carry `selected` / `onClick`. They're styled `Box` wrappers with a `content: @Composable () -> Unit` slot. Callers add `Modifier.clickable` if needed.
- **State chosen via tokens** — each variant has a `*Tokens` data class with a single visual state. `*Defaults.default()` / `*Defaults.selected()` factories return the right object; callers pass whichever matches current state.
- **Theme via `AppTheme` only** — no direct `MaterialTheme` access. Use `AppTheme.materialColors`, `AppTheme.typography`, `AppTheme.shapes`.
- **Content color via `LocalContentColor`** — token's `contentColor` is propagated with `CompositionLocalProvider`. `Text` / `Icon` inside the slot pick it up automatically.

## Components

Atoms (internal, container slots):

- [SurferContainer.kt](src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/atom/SurferContainer.kt) — list-item containers: `SurferFilledContainer`, `SurferOutlinedContainer`, `SurferContainer`, `SurferAddActionContainer` (dashed CTA).

Public composables (pick the right atom + add ripple on click):

- [SurferCard.kt](src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/atom/SurferCard.kt) — `SurferCard(selected, onClick?)`. Picks the variant based on `AppTheme.containerStyle` and `selected`. Optional `onClick` adds a ripple-bounded click area.
- [SurferActionCard.kt](src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/atom/SurferActionCard.kt) — `SurferActionCard(onClick?)`. Wraps the dashed `SurferAddActionContainer`. Optional `onClick` adds ripple.

Savings goals ([components/goal](src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/components/goal)) — the
one part of the system where emoji are allowed alongside workspace icons:

- `SurferGoalIcon` — emoji on a hue-tinted squircle; `goalTileColor(hue)` / `goalAccentColor(hue)` derive both tints per theme.
- `SurferGoalStatusPill` — `SurferGoalStatus.Active` (outlined + dot) / `Completed` (filled + check) / `Paused` / `Archived` (neutral). Label text comes from the caller; uikit does not depend on `domain`.
- `SurferGoalProgressBar`, `SurferGoalProgressRing`, `SurferGoalRingArc` — the arc is shared with the dashboard `SurferGoalsWidget` so the two rings cannot drift.
- `SurferGoalCard` — list card; dims to 70% for `Paused` / `Archived`.
- `SurferGoalContributionRow` — history line; `automatic = true` styles a future autopay row.
- `SurferEmojiPicker` + `SurferGoalHueRow` — icon and tint input for the goal editor.

Theme switch: `AppTheme(containerStyle = SurferContainerStyle.Filled | Outlined | Card)` — all `SurferCard` instances downstream resolve to that variant.

## State placeholders

Use these for the three non-content states a screen or section can be in. All three are theme-only
(`AppTheme` tokens) and ship with `@Preview` composables.

- [SurferEmptyState.kt](src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/components/SurferEmptyState.kt) — **loaded, but nothing to show** (empty list, no search results). Icon medallion + `title` + optional `subtitle` + optional CTA (`actionLabel` / `onActionClick`).
- [SurferErrorState.kt](src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/components/SurferErrorState.kt) — **load failed.** Same layout as the empty state with `icon` / `title` / `subtitle`, but the medallion uses `errorContainer`; pass `onRetry` (and optionally `retryLabel`) to render a retry button.
- [SurferSkeleton.kt](src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/components/SurferSkeleton.kt) — **still loading.** `SurferSkeleton(shape)` is a single shimmering placeholder block; `SurferSkeletonRow()` is a ready-made list-row skeleton. Size skeletons to match the eventual layout.

Decision: not loaded yet → `SurferSkeleton` · loaded with content → real UI · loaded empty → `SurferEmptyState` · failed → `SurferErrorState`.
