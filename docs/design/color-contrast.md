# Colour contrast (WCAG AA)

<!-- DOCS:TOC -->
## Contents
- [Colour contrast (WCAG AA)](#colour-contrast-wcag-aa)
- [TL;DR for agents](#tldr-for-agents)
- [What was audited](#what-was-audited)
- [Results](#results)
- [Deliberately not changed](#deliberately-not-changed)
- [The gate](#the-gate)
<!-- DOCS:END -->

## TL;DR for agents

- Every text token in the static light/dark schemes clears **4.5:1** against every
  surface it can land on. `ColorContrastTest` (`:uikit:jvmTest`) is the gate.
- If you change a colour in `AppColors` and that test goes red, the colour is the
  bug — do not relax the test.
- The check reads the real `LightColorScheme` / `DarkColorScheme`, so it also covers
  the surface containers Material fills in for us.

READ WHEN:
- adding or changing a token in `uikit/.../tokens/AppColors.kt`
- adding a semantic accent to `SemanticColors`
- drawing text on a low-alpha wash of its own colour

<!-- AI:SECTION id=color-contrast task=uikit,theme,accessibility,contrast,wcag -->
## What was audited

Issue #77 — a contrast pass over the Dashboard, transaction creation and Settings
screens in both themes.

Rather than eyeball three screens, the audit enumerated the token *pairs* those
screens actually paint. Every text colour they use comes from `MaterialTheme`
(`onSurface`, `onSurfaceVariant`, `primary`, `error`, the `on*Container` pairs) or
from `AppTheme.semanticColors`; no shipping composable on those screens hardcodes a
hex. Each foreground was measured against all five surface levels a card or row can
sit on, in both schemes.

Two backgrounds are not literal token values and had to be composited first:

- `SurferRecentTransactionsWidget` draws the income amount as `income` text on a
  14% wash of `income` (Dashboard).
- `TransactionCreationScreen`'s `TypePill` does the same at 18% with the type accent.

Self-tinted washes are the worst case in the whole palette — foreground and
background move together, so a mid-tone accent can never pull far enough away.

## Results

Only `SemanticColors` failed. The Material tonal palette carries its own tonal
guarantees for the `on*` pairs, and all of them passed in both themes; the semantic
accents are the app's own tokens and had nothing behind them.

Worst ratio across the five surface levels, light theme:

| Token | Before | worst | After | worst |
|---|---|---|---|---|
| `semantic.income` | `#2E9A6A` | 2.72 | `#1C5E41` | 5.94 |
| `semantic.expense` | `#B54744` | 4.10 | `#A3403D` | 4.83 |
| `semantic.warning` | `#A97110` | 3.20 | `#83580C` | 4.81 |
| `semantic.transfer` | `#2E5AA8` | 5.15 | `#2E5AA8` | 5.15 |

Self-tinted pills, light theme:

| Pill | Before | After |
|---|---|---|
| income @ 14% over `surface` | 2.96 | 6.06 |
| expense @ 18% over `surface` | 4.03 | 4.66 |
| transfer @ 18% over `surface` | 4.99 | 4.99 |

The dark scheme passed unchanged — its accents are light-on-dark and already sat at
5.5:1 or better everywhere they appear, so they keep the mockup's values. Only the
light accents moved.

Both pill call sites now `compositeOver(surface)` instead of staying translucent.
A translucent wash inherits its contrast from whatever the component is dropped
onto, so the ratio was a property of the *parent* rather than of the pair; pinning
it to the scaffold's own colour makes the guarantee hold if the pill is later moved
onto a card.

## Deliberately not changed

- **`outlineVariant`** sits at 1.3–1.9:1 against every surface in both themes. It is
  M3's hairline-divider token and dividers are decorative, so SC 1.4.11 does not
  apply to it. `outline`, which does bound real controls, is checked at 3:1 and
  passes.
- **`SurferCategoryPalette` tints** are theme-invariant and only ever colour a
  category bubble's icon, which is explicitly decorative (`contentDescription =
  null`, with the adjacent label carrying the meaning). They are still worth a look
  as a *design* matter in dark theme — they were picked for a light background — but
  that is not a WCAG failure. Tracked separately.
- **Seeded and dynamic schemes** (`material-kolor`, Material You) are derived at
  runtime from HCT tonal palettes and carry their own tone-distance guarantees. The
  test covers the two static schemes only.

## The gate

[`ColorContrastTest`](../../uikit/src/jvmTest/kotlin/com/georgeci/moneysurfer/uikit/theme/ColorContrastTest.kt)
runs in `:uikit:jvmTest`, so it is already part of `qaCommon` and every PR.

```bash
./gradlew :uikit:jvmTest
```

It asserts, for both schemes: body-text tokens and semantic accents at 4.5:1 over
all five surface levels, `outline` at 3:1, the eight `on*`/container pairs at 4.5:1,
and each accent over its own 14%/18% wash.

Changing a semantic accent also re-renders three Roborazzi galleries
(`surfer_account_stats`, `surfer_goal_parts`, `surfer_recent_transactions_widget`) —
re-record per [screenshot-tests.md](../testing/screenshot-tests.md).
<!-- AI:END -->
