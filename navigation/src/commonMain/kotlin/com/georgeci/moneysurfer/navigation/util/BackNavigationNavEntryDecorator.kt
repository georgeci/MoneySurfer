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
 * Takes the [backStack] rather than the keys [backNavigationContentKeys] derives from it, so the
 * navigation host is left with one call and nothing to hold: which entries can go back is this
 * file's business, and the host has no test harness to cover it in.
 *
 * The keys are derived on every composition rather than through a `derivedStateOf`. Memoizing them
 * would have to key on [entryProvider], which the host rebuilds each composition, so the cache
 * would miss every time anyway — and a back stack is a handful of entries. Only the *decorator* is
 * memoized, on the keys themselves, so an unchanged stack does not hand `NavDisplay` a new one.
 */
@Composable
fun <T : Any> rememberBackNavigationNavEntryDecorator(
    backStack: List<T>,
    entryProvider: (T) -> NavEntry<T>,
): NavEntryDecorator<T> {
    val poppableContentKeys = backNavigationContentKeys(backStack, entryProvider)
    return remember(poppableContentKeys) {
        NavEntryDecorator { entry ->
            CompositionLocalProvider(
                LocalSurferCanNavigateBack provides (entry.contentKey in poppableContentKeys),
            ) {
                entry.Content()
            }
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
