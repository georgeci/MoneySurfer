package com.georgeci.moneysurfer.uikit.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Whether a back affordance on the screen currently composing still leads somewhere.
 *
 * `false` on a destination sitting at the bottom of the back stack — every section the drawer and
 * the rail reset to. Their toolbars used to draw a back arrow regardless, which next to a permanent
 * drawer that already says where the user is reads as a second, redundant piece of navigation, and
 * which had nowhere to go: `AppNavigator.pop()` pops unconditionally, so the arrow's only effect on
 * a one-entry stack was to empty it.
 *
 * A screen reads this instead of reasoning about its own route, for the same reason it reads
 * [com.georgeci.moneysurfer.uikit.window.LocalSurferPane] instead of measuring the window: whether
 * back goes anywhere is a fact about the back stack, and only the navigation host knows it. See
 * `rememberBackNavigationNavEntryDecorator` in `:navigation` for where the value comes from.
 *
 * Defaults to `true`, so everything composed outside the navigation host — previews, component
 * tests, bottom sheets — keeps the affordance its caller asked for.
 */
val LocalSurferCanNavigateBack: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }
