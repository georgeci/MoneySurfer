# MoneySurfer — Dashboard customization & final widgets

**Context.** Build on the existing widget vocabulary in `widgets.jsx` + `dashboard-widgets.jsx`, the assembled layouts in `Dashboard.html` (variants A–F), the M3 tokens in `tokens.jsx`, and icons in `icons.jsx`. Artboard width 392, seed `plum`, design both light and dark. Reuse the existing widget components — don't reinvent their internals.

## Part 1 — Customization UX (new, primary focus)

The dashboard stays a **single-column vertical stack** (no grid). Design:

1. **"Customize dashboard" screen** — a list of every available widget with an on/off toggle and drag-to-reorder (6-dot grip handle). Two sections: **On dashboard** (ordered, draggable) and **Available** (off, tap to add). Each row shows a small thumbnail preview of the widget.
2. **Per-widget card style** — for each enabled widget, let the user pick a **size (Hero / Compact)** and, where applicable, a **variant**:
   - Balance — A–F typographic treatments
   - SpentByCategory — bar / ring / gauge / chips / multi

   Use a bottom sheet or sub-screen showing the variant previews side by side.
3. **States** — **default dashboard** vs **customised**, a "Reset to default" action, and an empty / first-run state.
4. **Entry point** — how the user opens customization from the dashboard top bar (overflow icon or edit affordance).

## Part 2 — Finalize each widget as a dashboard card

For each of the following, deliver **Hero + Compact** sizes and an **empty state**:

- QuickActions
- SpentMonth
- CategoriesDonut
- Insights (list + carousel)
- BurnRate
- SafeToSpend
- Recurring
- SpentByCategory (all 5 variants: bar / ring / gauge / chips / multi)
- Balance (with sparkline + trend delta)
- Goals (with a forecast / ETA caption line)

For the Budgets widget, reuse the components already designed in `Budgets.html` rather than drawing new ones.

## Artboards to produce

- Customize screen (resting + mid-drag)
- The "available widgets" palette
- The card-style picker sheet
- Per-widget Hero / Compact / variant previews
- The dashboard in both default and customised states
- Empty / first-run
