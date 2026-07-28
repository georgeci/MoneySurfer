package com.georgeci.moneysurfer.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import com.georgeci.moneysurfer.uikit.window.LocalSurferPane
import com.georgeci.moneysurfer.uikit.window.SurferPane
import com.georgeci.moneysurfer.uikit.window.SurferPaneRole

/**
 * Creates and remembers the app's [SurferPaneSceneStrategy].
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun <T : Any> rememberSurferPaneSceneStrategy(): SurferPaneSceneStrategy<T> {
    val delegate = rememberListDetailSceneStrategy<T>()
    return remember(delegate) { SurferPaneSceneStrategy(delegate) }
}

/**
 * The app's list-detail pane host: [ListDetailSceneStrategy] plus the two things the screens
 * inside it cannot work out for themselves (issue #388).
 *
 * 1. It tells every entry which pane it landed in, through [LocalSurferPane], so the detail pane
 *    stops drawing a second top app bar, a second back arrow and a second FAB next to the list
 *    pane that already draws all three.
 * 2. It pads the horizontal `safeDrawing` insets once, around the whole scaffold, and thereby
 *    consumes them for both panes. Applied per pane — which is what `surferSafeInsets()` used to
 *    do — the list pane padded its right edge and the detail pane its left, interior edges with
 *    no system inset behind them.
 *
 * Below the two-pane breakpoint the delegate declines the scene (it is constructed with
 * `shouldHandleSinglePaneLayout = false`), this strategy declines with it, and nothing above is
 * applied — compact-width behaviour is untouched, down to the composition local's default.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class SurferPaneSceneStrategy<T : Any>(
    private val delegate: ListDetailSceneStrategy<T>,
) : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val panes = entries.calculateSurferPanes()
        val decorated = entries.map { entry ->
            val pane = panes[entry.contentKey] ?: return@map entry
            NavEntry(navEntry = entry) {
                CompositionLocalProvider(LocalSurferPane provides pane) { entry.Content() }
            }
        }
        val scene = calculateScene(delegate, decorated) ?: return null
        return SurferPaneScene(delegate = scene, originals = entries.associateBy { it.contentKey })
    }

    companion object {
        /**
         * Marks a [NavEntry] as the list pane of a list-detail layout, showing [detailPlaceholder]
         * beside it while nothing is selected.
         */
        @OptIn(ExperimentalMaterial3AdaptiveApi::class)
        fun listPane(
            detailPlaceholder: @Composable ThreePaneScaffoldScope.() -> Unit = {},
        ): Map<String, Any> =
            ListDetailSceneStrategy.listPane(detailPlaceholder = detailPlaceholder) +
                (PaneRoleKey to SurferPaneRole.List)

        /** Marks a [NavEntry] as a detail pane of a list-detail layout. */
        @OptIn(ExperimentalMaterial3AdaptiveApi::class)
        fun detailPane(): Map<String, Any> =
            ListDetailSceneStrategy.detailPane() + (PaneRoleKey to SurferPaneRole.Detail)

        /**
         * Our own copy of the pane role. The delegate's metadata carries the same information, but
         * in `internal` classes this module cannot read.
         */
        internal const val PaneRoleKey = "com.georgeci.moneysurfer.navigation.SurferPaneRole"
    }
}

/**
 * The [SurferPane] each entry belongs to, keyed by `contentKey`, for the entries
 * [ListDetailSceneStrategy] will group into one scaffold — mirroring its own grouping: walk back
 * from the top while entries keep declaring a pane, and stop at the first one that does not.
 *
 * Entries outside that group, and every entry of a group with no list pane in it (a creation form
 * pushed straight from the dashboard, say), are left out of the map and keep the default
 * [SurferPane] — a detail pane with nothing beside it still needs its own chrome to be usable.
 */
internal fun <T : Any> List<NavEntry<T>>.calculateSurferPanes(): Map<Any, SurferPane> {
    val group = takeLastWhile { it.declaredPaneRole() != null }
    if (group.none { it.declaredPaneRole() == SurferPaneRole.List }) return emptyMap()
    var detailSeen = false
    return group.mapIndexed { index, entry ->
        val role = requireNotNull(entry.declaredPaneRole())
        // Only a detail pane ever gives up its back affordance, and only when popping would do
        // nothing but swap it for the placeholder. It keeps one if there is an earlier detail to
        // return to, or *any* entry stacked above it — a list route can be pushed from a detail
        // (Accounts' "See all" pushes the transactions list), which leaves the detail displayed
        // beside a list that is not its own, and that still needs a way out.
        val hasBackStack =
            role == SurferPaneRole.Detail && (detailSeen || index < group.lastIndex)
        if (role == SurferPaneRole.Detail) detailSeen = true
        entry.contentKey to SurferPane(role = role, hasPaneBackStack = hasBackStack)
    }.toMap()
}

private fun NavEntry<*>.declaredPaneRole(): SurferPaneRole? =
    metadata[SurferPaneSceneStrategy.PaneRoleKey] as? SurferPaneRole

/**
 * Calls [strategy]'s scene calculation from inside another strategy's, naming the delegate
 * explicitly — a plain `with(strategy) { calculateScene(entries) }` inside the caller would have
 * two implicit dispatch receivers carrying that member extension, and the wrong one recurses.
 */
private fun <T : Any> SceneStrategyScope<T>.calculateScene(
    strategy: SceneStrategy<T>,
    entries: List<NavEntry<T>>,
): Scene<T>? = with(strategy) { calculateScene(entries) }

/**
 * Wraps a list-detail [Scene] to pad the horizontal `safeDrawing` insets once, around both panes.
 *
 * [windowInsetsPadding] also *consumes* what it applies, so the per-screen `surferSafeInsets()`
 * inside each pane contributes nothing horizontally — no change needed in the screens, and no
 * padding on the edge where the two panes meet.
 *
 * @param originals the undecorated entries by `contentKey`. [previousEntries] is fed back through
 *   the strategy chain to build the predictive-back scene; handing back the decorated entries
 *   would wrap them a second time and the stale inner value would win.
 */
@Immutable
private class SurferPaneScene<T : Any>(
    private val delegate: Scene<T>,
    private val originals: Map<Any, NavEntry<T>>,
) : Scene<T> {

    override val key: Any get() = delegate.key

    override val entries: List<NavEntry<T>> get() = delegate.entries

    override val previousEntries: List<NavEntry<T>>
        get() = delegate.previousEntries.map { originals[it.contentKey] ?: it }

    override val metadata: Map<String, Any> get() = delegate.metadata

    override val content: @Composable () -> Unit = {
        Box(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            ),
        ) {
            delegate.content()
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is SurferPaneScene<*> && delegate == other.delegate)

    override fun hashCode(): Int = delegate.hashCode()
}
