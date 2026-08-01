package com.georgeci.moneysurfer.navigation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import com.georgeci.moneysurfer.uikit.navigation.LocalSurferCanNavigateBack

/**
 * Publishes [LocalSurferCanNavigateBack] to every rendered entry, so a screen's toolbar can drop a
 * back arrow that leads nowhere.
 *
 * A decorator rather than a wrapper around the entry provider: the flag has to follow the back
 * stack, and only the decoration runs again when the stack changes underneath an entry that stays
 * composed — selecting the section a screen is already in resets the stack to just that screen, and
 * its arrow has to go with the entries that were below it.
 *
 * @param poppableContentKeys from [backNavigationContentKeys].
 */
@Composable
fun <T : Any> rememberBackNavigationNavEntryDecorator(
    poppableContentKeys: Set<Any>,
): NavEntryDecorator<T> = remember(poppableContentKeys) {
    NavEntryDecorator { entry ->
        CompositionLocalProvider(
            LocalSurferCanNavigateBack provides (entry.contentKey in poppableContentKeys),
        ) {
            entry.Content()
        }
    }
}

/**
 * The `contentKey`s of the [backStack] entries that have another entry below them — the ones a back
 * affordance can actually pop.
 *
 * Keys are resolved through the same [entryProvider] the host renders with, rather than derived
 * from the route: what a route's content key is, is that provider's decision.
 *
 * A route that appears on the stack twice, once at the bottom, contributes one key for both
 * occurrences and so keeps its affordance at the bottom too. Nothing in the app pushes a route on
 * top of itself — the sections are reached with `AppNavigator.resetTo` — and the alternative,
 * dropping the affordance on the *upper* copy, strands the user on it.
 */
internal fun <T : Any> backNavigationContentKeys(
    backStack: List<T>,
    entryProvider: (T) -> NavEntry<T>,
): Set<Any> = backStack.drop(1).mapTo(mutableSetOf()) { entryProvider(it).contentKey }
