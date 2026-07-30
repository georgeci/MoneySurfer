package com.georgeci.moneysurfer.uikit.window

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/** Which pane of the host layout a screen is currently composing into. */
enum class SurferPaneRole {
    /**
     * The screen is the only thing on screen — every compact-width window, and every route the
     * pane host does not lay out side by side. Chrome behaves exactly as it always has.
     */
    Single,

    /** The list pane of a two-pane layout. Owns the section's top app bar and its FAB. */
    List,

    /**
     * The detail pane of a two-pane layout, displayed *next to* its list pane. The list pane is
     * already drawing the section's top app bar and FAB, so this one must not draw its own.
     */
    Detail,

    /**
     * The extra pane of a three-pane layout — the design's inline "add" panel, displayed to the
     * right of the detail pane it was opened from (issue #391). Like the detail pane it draws no
     * section chrome; the list pane on the far side still owns the top app bar and the FAB.
     */
    Extra,
}

/**
 * How the pane host is displaying the screen currently composing, published through
 * [LocalSurferPane].
 *
 * A screen reads this instead of measuring the window itself: whether a sibling pane is on screen
 * is a fact about the *back stack* as much as about the width, and only the host knows both. See
 * `SurferPaneSceneStrategy` in `:navigation` for where the value comes from.
 *
 * @param role which pane the screen occupies.
 * @param hasPaneBackStack whether a back affordance on this screen still leads somewhere inside
 *   the two-pane layout rather than merely swapping the detail pane for its placeholder — either
 *   an earlier entry of the same pane to return to (a settings sub-screen that opened another,
 *   About → Licenses) or an entry stacked above this one (a list route pushed from a detail, which
 *   leaves the detail beside a list that is not its own). A detail opened straight from its list
 *   has neither, and is the one case that drops the affordance. An [SurferPaneRole.Extra] panel
 *   always has one: dismissing it leaves the list and the detail it was opened from in place.
 */
@Immutable
data class SurferPane(
    val role: SurferPaneRole = SurferPaneRole.Single,
    val hasPaneBackStack: Boolean = false,
) {
    /**
     * Whether this screen draws the section's top app bar and floating action button. Only the
     * panes displayed *beside* a list pane give them up — that list pane is already drawing both.
     */
    val ownsSectionChrome: Boolean
        get() = role == SurferPaneRole.Single || role == SurferPaneRole.List

    /** Whether a back affordance on this screen still has somewhere to go. */
    val showsBackNavigation: Boolean get() = ownsSectionChrome || hasPaneBackStack
}

/** The [SurferPane] the current composition sits in. Defaults to a plain full-window screen. */
val LocalSurferPane: ProvidableCompositionLocal<SurferPane> = compositionLocalOf { SurferPane() }
