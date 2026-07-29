# App fixes list

A running list of UI/UX fixes spotted while walking through the app. Items are recorded
here first; implementation happens later. Every item below is **done** except item 2,
which waits on a new background asset — each one names what changed.

## Welcome / onboarding screen

1. **Use the real app icon in the header.** The small logo next to the "MoneySurfer"
   wordmark at the top of the welcome screen is a placeholder glyph — replace it with the
   actual app launcher icon.

   *Done:* new `SurferAppIcon` in `:uikit` draws the launcher PNG the splash, drawer and
   About screen already used; the onboarding and sign-in brand rows call it.
2. **Bottom background artifact.** Where the green gradient meets the white rounded CTA
   container ("Get started" + sign-in hint), a leftover strip of the background shows
   through and looks broken. A new background asset will be provided later — hold until
   then.

   *Not done:* waiting on the asset.

## Sign-in screen

3. **Use the real app icon in the header.** Same placeholder glyph next to the
   "MoneySurfer" wordmark as on the welcome screen — see item 1.

   *Done:* with item 1.
4. **Colour seam under the system navigation bar.** The strip behind the gesture bar is a
   slightly lighter green than the background right above it, so the seam is visible.
   The navigation-bar area should draw the same colour as the screen background.

   *Done:* `enableEdgeToEdge()`'s default navigation-bar style paints a translucent white
   scrim in light mode. Both hosts' `MainActivity` now pass
   `SystemBarStyle.auto(TRANSPARENT, TRANSPARENT)`; icon tints stay per screen via
   `ConfigureSystemBars`.

## Sign-up / sign-in form

5. **Validation errors push the layout around.** When an error like "Enter your email"
   appears under a field, everything below it (the next field, the buttons) jumps down.
   Reserve the space for the error text under every field permanently, so the layout
   stays put whether or not an error is shown.

   *Done:* `SurferTextField` draws its own message line (`SurferFieldMessage`) instead of
   M3's `supportingText`, always laid out, error over helper text. Covered by
   `SurferFormFieldTest` and a sign-in layout-shift assertion.

## App-wide

6. **Input fields must use the same corner radius as `SurferCard`.** Applies everywhere,
   not just where it was spotted (the "Search currency" field in the currency picker —
   its corners are rounder than the currency rows below it). `SurferCard` draws with
   `AppTheme.shapes.large` (see [SurferContainer.kt](../../uikit/src/commonMain/kotlin/com/georgeci/moneysurfer/uikit/atom/SurferContainer.kt));
   every text field / search field should take the same shape token.

   *Done:* `AppShapes.extraSmall` — the slot `OutlinedTextField` defaults to — is now 16.dp,
   same as `large`, and `SurferTextField` / `SurferSearchField` / `SurferCurrencyPickerField`
   name the token explicitly.

## Currency picker

7. **The bottom sheet must not shrink in height.** Typing a search query filters the list
   down and the sheet collapses to fit the remaining rows (e.g. "eu" leaves a single Euro
   row and the sheet shrinks to it). The sheet should keep a stable height while the
   result list changes.

   *Done:* the list is sized from the unfiltered currency count, capped at 420.dp. The sheet
   body is now `SurferCurrencyBottomSheetContent`, so a UI test can mount and type into it.

## Create workspace

8. **Member count must not be shown in the create case.** The green preview card at the top
   of "New workspace" renders "Shared · 2 members · PLN" — the member count is meaningless
   for a workspace that doesn't exist yet. Drop the "2 members" part from the preview in
   the create flow (keep it when editing an existing workspace).

   *Done:* the preview takes a nullable member count — null while creating, which falls back
   to the currency-only subtitle.

## Customize dashboard

9. **Replace the toggles with a round +/− button.** Each widget row currently ends with a
   switch; use a circular icon button instead — "−" for widgets that are on the dashboard,
   "+" for the ones under "Available".

   *Done:* new `SurferRoundIconButton`; the two row composables collapsed into one `WidgetRow`
   that differs only in direction and in whether it offers the style pill.
10. **Drag & drop in the "Available" section too.** Rows under "Available" have no drag
    handle, so reordering only works inside "On dashboard". Make both sections draggable
    and allow dragging an item from one section to the other.

    *Done:* the reorder state spans both sections, and `DashboardLayoutConfig.withWidgetMoved`
    now adopts the target row's enabled flag — dropping across the boundary switches the
    widget on or off in that slot.
11. **Hide per-widget settings behind a Build-layer flag, off for now.** The per-widget
    configuration rows ("Full · Classic ›", "Full · List ›") ship disabled until they are
    ready. Use a local build key (not a user setting, not remote-overridable) and add the
    row to the "Feature flags shipped switched off" table in
    [AGENTS.md](../../AGENTS.md) in the same PR, as the rules there require.

    *Done:* `host.dashboard_widget_style`, `false` in both hosts, read through
    `HostCapabilities`; row added to the AGENTS.md table.

## Choose a workspace (Settings → change workspace)

12. **No back button in the toolbar.** Reached from Settings, the "Choose a workspace"
    screen has no navigation icon, so there is no way back other than the system gesture.
    Add a back arrow to the toolbar.

    *Done:* only when the screen was pushed (`showActions`) — as a start route it is the
    bottom of the stack and sign-out is the way out.

## Create flows — consistency

13. **Toolbar save action looks different per screen.** "New account" shows a filled pill
    button with a check icon ("✓ Save"), while "New workspace" shows a plain text action
    ("Create"). Pick one style and use it in every create/edit toolbar.

    *Done:* the pill (`SurferToolbarButtonAction`) wins — every other create/edit screen
    already used it; workspace creation was the only holdout.

## New account

14. **Gap between a section label and its field is too large and inconsistent.** "Type" →
    the segmented selector has a noticeably bigger gap than "Currency" → its dropdown.
    Use one spacing value for every label-to-control block on the screen.

    *Done:* the label and its control were siblings of the form's own `Column`, so its 24.dp
    `spacedBy` landed between them. New `SurferFormSection` keeps them one block, 8.dp apart;
    account and workspace creation use it.
15. **Helper text under a field is indented.** "You can adjust this later from the account
    menu." under "Opening balance" sits further right than the field's left edge — it
    should align with the field.

    *Done:* with item 5 — account creation's two raw `OutlinedTextField`s are now
    `SurferTextField`s, and the message line has no indent.

## Account detail

16. **The overflow menu is empty.** The "⋮" action in the toolbar opens a menu with no
    items in it — either fill it with the account actions or drop the button.

    *Done:* dropped — it was a stub with a `/* wires later */` click handler. Its string went
    with it.

## Transactions list

17. **Duplicate add buttons.** The empty state shows "+ Add transaction" while the FAB
    "+ New" sits right below it — two controls for the same action on one screen. Keep
    one.

    *Done:* the FAB hides while the empty state offers its own add CTA. Search and filter
    empties keep it — their CTA clears the filter rather than adding a row.
