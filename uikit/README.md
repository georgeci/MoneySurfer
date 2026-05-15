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

Theme switch: `AppTheme(containerStyle = SurferContainerStyle.Filled | Outlined | Card)` — all `SurferCard` instances downstream resolve to that variant.

## State placeholders

Use these for the three non-content states a screen or section can be in. All three are theme-only
(`AppTheme` tokens) and have previewable `icon` / `title` / `body` / `action` parameters.

- [SurferEmptyState.kt](src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/components/SurferEmptyState.kt) — **loaded, but nothing to show** (empty list, no search results). Centered icon + title + optional body + optional `SurferStateAction` CTA.
- [SurferErrorState.kt](src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/components/SurferErrorState.kt) — **load failed.** Same layout as empty state but the icon is tinted with `materialColors.error`; pass `onRetry` to render a retry button.
- [SurferShimmer.kt](src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/components/SurferShimmer.kt) — **still loading.** `Modifier.surferShimmer(shape)` paints an animated shimmer on any node; `SurferShimmerBox(height, shape)` is a ready-made clipped skeleton block. Size skeletons to match the eventual layout.

Decision: not loaded yet → shimmer · loaded with content → real UI · loaded empty → `SurferEmptyState` · failed → `SurferErrorState`.
