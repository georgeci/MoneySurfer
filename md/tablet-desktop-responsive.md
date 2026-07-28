# Tablet / desktop responsive layout — research

Date: 2026-07-28 · Status: research only, no code changes

Sources:

- Claude Design project `019dd9e9-bcad-78f3-a4b9-49cde06a75ac`
  - `MoneySurfer Desktop - Wireframe.html` — layout skeleton with region labels and callouts
  - `MoneySurfer Desktop.html` — high-fidelity screens (Home, Accounts, Accounts/filled)
  - `Desktop - Research & Options.html` — the four-direction exploration that preceded them
- Codebase at `feature/tablet-desktop-responsive-layout-a645c6`

---

## 1. What the design specifies

### 1.1 Shell

| Region | Wireframe (1180×760) | Hi-fi (1360×880) |
| --- | --- | --- |
| Sidebar / nav rail | 200 dp fixed | 210 dp fixed, `surface-container-low`, right border |
| Top bar | 52 dp | 60 dp — title, workspace chip, spacer, search pill (max 300), bell icon |
| Content padding | 16 | 22 vertical / 26 horizontal, 14–16 gaps |

The sidebar is a **full drawer, not a rail**: brand block (34 dp logo + wordmark), primary items, an uppercase `Manage` section divider, spacer, then a user block (avatar + name + workspace) pinned to the bottom.

Design nav taxonomy: `Home · Accounts · Activity · Budgets · Goals` — then `Manage: Bills · Settings`.

### 1.2 Home — overview dashboard (direction A)

Three stacked bands inside a scrolling content column:

1. **KPI row** — `grid-template-columns: 1.5fr 1fr 1fr`. Hero card (total balance, big number, delta, sparkline background) + two stat cards with progress bars.
2. **Chart + rail** — `grid-template-columns: 1.6fr 1fr`, flex:1. Cash-flow area chart on the left, budget list on the right.
3. **Activity table** — full width, real table semantics: header row + `1.7fr 1fr 1fr .9fr` columns (Transaction / Category / Account / Amount, right-aligned).

### 1.3 Accounts — list–detail workspace (direction D)

Four columns: `200/210 nav · 270–320 list · fluid detail · 300–340 panel`.

- List pane: title + count/total subtitle, filter chips, selectable rows, dashed "Add account" affordance.
- Detail header doubles as a contextual toolbar (`Edit`, `Statements`) above a 40 px balance.
- Detail body is itself a two-column grid: transactions table + an **inline add-transaction panel**. The wireframe callout is explicit: *"inline add panel (no modal)"*.

### 1.4 The stated principle

From the research board: *"Web and mobile split roles. The web version is the informative dashboard for extended stats over a period; mobile stays focused on quickly adding data on the go. Design the desktop as the analysis surface, not a stretched phone."*

---

## 2. Where the codebase actually is

### 2.1 Already in place — the skeleton is real

The adaptive foundation is further along than the mocks imply.

- [AppNavGraph.kt:248](navigation/src/commonMain/kotlin/com/georgeci/moneysurfer/navigation/AppNavGraph.kt:248) — `NavigationSuiteScaffold` driven by `currentWindowAdaptiveInfo()`, so medium/expanded widths already get a rail or drawer for free.
- `ListDetailSceneStrategy` is wired into **every** feature nav graph — account, budget, category, goal, settings, transaction, workspace. List panes declare `detailPlaceholder = { NavDetailPlaceholder() }`; detail routes carry `detailPane()` metadata.
- [SurferDetailPlaceholder.kt](uikit/src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/components/SurferDetailPlaceholder.kt) — the empty-detail state exists and is screenshot-tested.
- JVM desktop target ships in `:composeApp` (`jvm()`, [main.kt](composeApp/src/jvmMain/kotlin/com/georgeci/moneysurfer/main.kt)).
- Dependencies are current: `material3 1.12.0-alpha03`, `compose-multiplatform-adaptive 1.3.0-beta02`, `adaptive-navigation3`, `material3-adaptive-navigation-suite`.

**So a tablet already renders two panes.** The gap is not "no adaptivity" — it's that nothing above the pane level knows about width.

### 2.2 Gaps, roughly in order of impact

**G1 · No content width cap anywhere.** `widthIn(max = …)` appears in exactly two places, both in the auth flow ([SignInScreen.kt:213](feature/login/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/login/SignInScreen.kt:213), [OnboardingScreen.kt:259](feature/login/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/login/onboarding/OnboardingScreen.kt:259)). Every other screen is `fillMaxWidth()` / `fillMaxSize()` (56 files across the 9 feature modules). On a 1360 dp window a settings row or a transaction line stretches to ~1150 dp of mostly empty space. This is the single most visible "stretched phone" symptom and the cheapest to fix.

**G2 · Every pane brings its own Scaffold, so two-pane mode doubles the chrome.** This is the most concrete defect on tablet today, and it has three faces.

*Two toolbars.* List-pane and detail-pane screens are each a full `Scaffold` with its own `SurferToolbar`. Side by side at expanded width that renders **two top app bars at the same y**, each with its own title:

| Section | List pane | Detail pane |
| --- | --- | --- |
| Accounts | [AccountsManageScreen.kt:152](feature/account/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/account/manage/AccountsManageScreen.kt:152) | [AccountDetailsScreen.kt:147](feature/account/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/account/details/AccountDetailsScreen.kt:147) |
| Categories | [CategoriesManageScreen.kt:127](feature/category/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/category/manage/CategoriesManageScreen.kt:127) | [CategoryDetailsScreen.kt:171](feature/category/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/category/details/CategoryDetailsScreen.kt:171) |
| Transactions | [TransactionsByAccountScreen.kt:123](feature/transaction/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/transaction/list/TransactionsByAccountScreen.kt:123) | [TransactionDetailsScreen.kt:162](feature/transaction/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/transaction/details/TransactionDetailsScreen.kt:162) |
| Goals | [GoalsScreen.kt:93](feature/goal/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/goal/list/GoalsScreen.kt:93) | [GoalDetailsScreen.kt:122](feature/goal/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/goal/details/GoalDetailsScreen.kt:122) |
| Budgets | [BudgetsScreen.kt:106](feature/budget/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/budget/list/BudgetsScreen.kt:106) | [BudgetDetailsScreen.kt:109](feature/budget/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/budget/details/BudgetDetailsScreen.kt:109) |
| Settings | [SettingsScreen.kt:183](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/SettingsScreen.kt:183) | 10 detail routes — 6 via [SettingsSubScreenScaffold.kt:45](feature/settings/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/settings/components/SettingsSubScreenScaffold.kt:45), the rest with their own toolbars (Preferences, Appearance, About, Sync) |

Both sides also pass `onBack`, so there are **two back arrows**. The detail one pops back to the placeholder — visible but pointless when the list is already on screen. The list one navigates out of the whole section, which is a strange neighbour for a back arrow that means "close the detail".

The design has neither: one top bar spans the whole main area, and the detail pane opens with a *contextual header* — subtitle, `Edit` / `Statements` buttons, then a 40 px balance — with no navigation icon at all.

*Two FABs.* The same pairs both declare `floatingActionButton`, so Accounts, Categories, Transactions, Goals and Budgets each show two FABs at expanded width, bottom-right of each pane, roughly 300 dp apart.

*Insets applied to interior edges.* [`surferSafeInsets()`](uikit/src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/modifier/SurferSafeInsets.kt) pads `Top + Horizontal` from `safeDrawing`. Every pane applies it independently, so the list pane pads its **right** edge and the detail pane its **left** — interior edges that have no system inset. Harmless on phone (one pane, both edges are real), wrong the moment two panes coexist, and wrong again next to a navigation rail whose inset the suite has already consumed.

The root cause is one architectural choice: `Scaffold` is owned by the screen rather than by the pane host. Fixing this is the prerequisite for most of Phase 1 — worth resolving before the drawer work, not after.

**G3 · Dashboard is a one-column list.** [DashboardScreen.kt:227](feature/dashboard/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/dashboard/DashboardScreen.kt:227) is a `LazyColumn` — one widget per row at any width. The design wants `1.5/1/1` then `1.6/1`. The config-driven widget model already carries a per-widget size (`DashboardWidgetSize.Expanded/Compact` → `LocalSurferWidgetSize`, provided at [DashboardScreen.kt:240](feature/dashboard/src/commonMain/kotlin/com/georgeci/moneysurfer/feature/dashboard/DashboardScreen.kt:240)), but there is no column-count or span concept to build a grid from.

**G4 · No third pane / inline panel.** The design's add-transaction panel is a persistent 340 dp column, explicitly *not* a modal. Today creation is a separate route or a bottom sheet (`BottomSheetSceneStrategy`). `ListDetailSceneStrategy` supports an extra pane; nothing uses it.

**G5 · The nav suite has no brand, sections, or user block.** `NavigationSuiteScaffold` gets a flat `TopLevelDestination.entries` list. The design's drawer has a logo header, a `Manage` section divider, and a bottom user/workspace block. `NavigationSuiteScaffold` does not model any of those — an expanded-width drawer would need a custom composable.

**G6 · No navigation suite at compact width.** [AppNavGraph.kt:255-261](navigation/src/commonMain/kotlin/com/georgeci/moneysurfer/navigation/AppNavGraph.kt:255) returns `NavigationSuiteType.None` below `WIDTH_DP_MEDIUM_LOWER_BOUND`, and no `bottomBar` exists anywhere (only two local footers, in transaction filters and workspace selector). Phone top-level navigation therefore runs entirely through dashboard affordances. Worth confirming this is a deliberate product decision before building on top of it — the desktop work will want the same `TopLevelDestination` list to be authoritative.

**G7 · Nav taxonomy diverges from the design.** App: `Dashboard · Transactions · Accounts · Categories · Settings`. Design: `Home · Accounts · Activity · Budgets · Goals` + `Manage: Bills · Settings`. Budgets and Goals exist as features with routes but are not top-level; Categories is top-level but absent from the design; "Bills" has a `SurferRecurringWidget` but no route at all.

**G8 · Desktop window is unconfigured.** [main.kt](composeApp/src/jvmMain/kotlin/com/georgeci/moneysurfer/main.kt) opens a bare `Window` with no `rememberWindowState`, no default size, no minimum size — so the desktop host can be dragged to phone width and back with nothing tested at either end. The file's own comment says desktop is "a developer-only host (no shipped release)".

**G9 · Zero wide-width test coverage.** The uikit screenshot tests do not parametrise device width; Maestro flows are phone-only. Any responsive work lands unverified unless this is addressed alongside.

---

## 3. The unresolved question: what is the target?

The mocks are drawn as a **browser window** — traffic-light chrome, URL `app.moneysurfer.com/home`. The repo has no web target: `:composeApp` declares `android`, `iosArm64`, `iosSimulatorArm64`, `jvm` and nothing else. No `wasmJs`, no `js`.

So "desktop" currently resolves to one of three quite different projects:

1. **Tablet-first** — Android tablets / iPad, using the shell that already exists. Cheapest; the two-pane layer is already built.
2. **JVM desktop as a real product** — promoting the existing developer host to a shipped app (packaging, window state, menus, keyboard).
3. **Compose for Web (wasmJs)** — what the mocks literally depict. A new target, new DI wiring for storage and Firebase, new build and deploy pipeline. Much the largest.

Directions 1 and 2 share almost all of the layout work below; 3 adds a platform on top of it. Worth settling before Phase 2.

---

## 4. Suggested roadmap

**Phase 0 · Foundation in `:uikit`** — no visual change on phone.
- A `SurferWindowSize` helper over `currentWindowAdaptiveInfo()` with the Material breakpoints (Compact <600, Medium 600–839, Expanded 840–1199, Large ≥1200), so features stop reaching for `androidx.window` directly.
- A `Modifier.surferContentContainer()` that caps content width (~840–1120 dp) and centres it. Applying it at each feature's Scaffold content root fixes **G1** everywhere at once, with essentially no risk on phone.

**Phase 1 · Pane chrome** (G2) — the structural one; do it before the drawer.
- Decide who owns `Scaffold`. Two workable shapes: (a) the pane host owns one `Scaffold`, and screens contribute title/actions/FAB through a small `SurferPaneChrome` slot API; or (b) screens keep their `Scaffold` but read a `LocalPaneRole` (`Single` / `List` / `Detail`) to drop the back arrow, demote the toolbar to a contextual header, and hide the FAB when a sibling pane already shows one. (b) is far less invasive and can land section by section; (a) is closer to the design and to where Material's `ThreePaneScaffold` is heading.
- Either way, move horizontal inset handling out of `surferSafeInsets()` at the screen level and up to the host, so interior pane edges stop being padded.
- Good pilot: Accounts — one list, one detail, both with a FAB and an overflow, and it is the section the hi-fi mock actually draws.

**Phase 2 · Navigation shell** (G5, G6, G7)
- Settle the compact-width decision and the taxonomy.
- Medium → rail (already free). Expanded/Large → a custom permanent drawer matching the 210 dp design: brand header, section grouping, user footer.

**Phase 3 · Dashboard grid** (G3)
- Add a span/column notion to the dashboard layout config, then render a staggered grid at ≥840 dp while keeping the `LazyColumn` below. The existing `Expanded`/`Compact` per-widget switch already gives each widget two densities to place into it.

**Phase 4 · Pane polish** (G4)
- Set preferred list-pane widths (300–320 dp) to match the design.
- Evaluate the extra pane for the inline add-transaction panel at Large width, keeping sheet/route behaviour on Compact.

**Phase 5 · Verification** (G8, G9)
- Screenshot tests at three widths for the shell and the dashboard.
- `rememberWindowState(1360×880)` plus a minimum size on the desktop window; a desktop `runComposeUiTest` smoke.

---

## 5. Open questions for the owner

1. Which target — tablet, shipped JVM desktop, or web? (§3)
2. Pane chrome: host-owned `Scaffold` with a slot API, or screen-owned `Scaffold` reading a pane role? (G2, Phase 1)
3. Is "no bottom bar on phone" intentional? (G6)
4. Do Budgets / Goals / Bills become top-level nav, and does Categories move out? (G7)
5. Should the desktop add-transaction panel be persistent (design's stance) or stay a sheet, given the existing `BottomSheetSceneStrategy` investment? (G4)
