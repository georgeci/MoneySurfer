package com.georgeci.moneysurfer.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.uikit.components.base.SurferDrawerItem
import com.georgeci.moneysurfer.uikit.components.base.SurferDrawerSection
import com.georgeci.moneysurfer.uikit.components.base.SurferNavigationDrawer
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.window.SurferWindowSize
import com.georgeci.moneysurfer.uikit.window.currentSurferWindowSize
import moneysurfer.navigation.generated.resources.Res
import moneysurfer.navigation.generated.resources.nav_user_guest
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * How the top-level destinations are presented at a given window width.
 *
 * Compact is [None] on purpose. §G6 of `md/tablet-desktop-responsive.md` asked whether the missing
 * bottom bar was a deliberate product decision; issue #389 settles it as one, so it is spelled out
 * here as a branch rather than left to fall out of a width comparison. Three things back it: the
 * product split the research board states (phone captures data, wide windows analyse it), the
 * seven-destination taxonomy the drawer needs — a `NavigationBar` holds at most five — and the
 * fact that every phone screen already ends in a FAB and its own bottom insets, which a bar would
 * have to be threaded through across nine feature modules.
 */
internal enum class NavigationPresentation {
    /** Compact — no navigation suite; the dashboard's own affordances carry phone navigation. */
    None,

    /** Medium — the `NavigationSuiteScaffold` rail, which needs no chrome the suite cannot model. */
    Rail,

    /** Expanded and Large — the custom permanent drawer (issue #389, gap G5). */
    Drawer,
}

internal fun SurferWindowSize.navigationPresentation(): NavigationPresentation = when (this) {
    SurferWindowSize.Compact -> NavigationPresentation.None
    SurferWindowSize.Medium -> NavigationPresentation.Rail
    SurferWindowSize.Expanded, SurferWindowSize.Large -> NavigationPresentation.Drawer
}

/**
 * Hosts [content] next to whichever presentation of [TopLevelDestination] the window width calls
 * for. One destination list, three presentations — see [NavigationPresentation].
 *
 * @param onOpenWorkspaceSelector invoked by the drawer's user footer; the rail has nowhere to put
 *   it, so at Medium the workspace switcher stays where it already lives, in Settings.
 */
@Composable
internal fun AppNavigationShell(
    currentTopLevel: Route.TopLevel,
    onSelect: (Route) -> Unit,
    onOpenWorkspaceSelector: () -> Unit,
    content: @Composable () -> Unit,
) {
    when (currentSurferWindowSize().navigationPresentation()) {
        NavigationPresentation.Drawer -> AppNavigationDrawer(
            currentTopLevel = currentTopLevel,
            onSelect = onSelect,
            onOpenWorkspaceSelector = onOpenWorkspaceSelector,
            content = content,
        )
        NavigationPresentation.Rail -> AppNavigationSuite(
            layoutType = NavigationSuiteType.NavigationRail,
            currentTopLevel = currentTopLevel,
            onSelect = onSelect,
            content = content,
        )
        NavigationPresentation.None -> AppNavigationSuite(
            layoutType = NavigationSuiteType.None,
            currentTopLevel = currentTopLevel,
            onSelect = onSelect,
            content = content,
        )
    }
}

/**
 * The drawer presentation: [SurferNavigationDrawer] beside the content, with the destinations
 * grouped by [NavigationSection].
 *
 * The horizontal `safeDrawing` insets are padded — and thereby consumed — once around both
 * children, exactly as [SurferPaneSceneStrategy] does for the two panes inside `content`. Applied
 * by each child instead, the drawer would pad the window edge and the content its own left edge,
 * which is an interior edge with no system inset behind it.
 */
@Composable
private fun AppNavigationDrawer(
    currentTopLevel: Route.TopLevel,
    onSelect: (Route) -> Unit,
    onOpenWorkspaceSelector: () -> Unit,
    content: @Composable () -> Unit,
) {
    val viewModel: AppShellViewModel = koinViewModel()
    val identity by viewModel.identity.collectAsStateWithLifecycle()
    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        SurferNavigationDrawer(
            sections = drawerSections(currentTopLevel = currentTopLevel, onSelect = onSelect),
            userName = identity.userName ?: stringResource(Res.string.nav_user_guest),
            workspaceName = identity.workspaceName,
            onUserClick = onOpenWorkspaceSelector,
        )
        // `weight` only sizes the horizontal axis; without `fillMaxHeight` the content column
        // would wrap its children's height instead of filling the window beside the drawer.
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
    }
}

/**
 * [TopLevelDestination.entries] folded into the drawer's groups, keeping declaration order inside
 * each one. `groupBy` preserves insertion order for both the keys and the values, so a section
 * that ever became non-contiguous in the enum would still render as one block — the enum's own
 * doc is what keeps the two orders honest.
 */
@Composable
private fun drawerSections(
    currentTopLevel: Route.TopLevel,
    onSelect: (Route) -> Unit,
): List<SurferDrawerSection> =
    TopLevelDestination.entries.groupBy { it.section }.map { (section, destinations) ->
        SurferDrawerSection(
            label = section.label?.let { stringResource(it) },
            items = destinations.map { destination ->
                SurferDrawerItem(
                    label = stringResource(destination.label),
                    icon = destination.icon,
                    selected = destination.matches(currentTopLevel),
                    onClick = { onSelect(destination.route) },
                )
            },
        )
    }

/** The rail (and the empty Compact) presentation — the flat list `NavigationSuiteScaffold` takes. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun AppNavigationSuite(
    layoutType: NavigationSuiteType,
    currentTopLevel: Route.TopLevel,
    onSelect: (Route) -> Unit,
    content: @Composable () -> Unit,
) {
    val labels = TopLevelDestination.entries.associateWith { stringResource(it.label) }
    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                val label = labels.getValue(destination)
                item(
                    selected = destination.matches(currentTopLevel),
                    onClick = { onSelect(destination.route) },
                    icon = { Icon(imageVector = destination.icon, contentDescription = label) },
                    label = { Text(label) },
                )
            }
        },
        containerColor = AppTheme.materialColors.background,
        content = content,
    )
}
