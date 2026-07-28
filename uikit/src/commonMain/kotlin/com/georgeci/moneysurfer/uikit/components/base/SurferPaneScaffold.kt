package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.window.LocalSurferPane
import com.georgeci.moneysurfer.uikit.window.SurferPane
import com.georgeci.moneysurfer.uikit.window.SurferPaneRole
import moneysurfer.uikit.generated.resources.Res
import moneysurfer.uikit.generated.resources.uikit_back
import org.jetbrains.compose.resources.stringResource

/**
 * The screen's primary "add a thing" action.
 *
 * It is declared rather than passed as a composable because where it renders depends on the pane:
 * an extended FAB when the screen owns the section chrome, a header button when it is a detail
 * pane sitting next to its list and the list already owns the FAB. Dropping it outright in that
 * case would lose the affordance, which is why [SurferPaneScaffold] takes the action instead of a
 * `floatingActionButton` slot.
 */
@Immutable
data class SurferPaneAction(
    val label: String,
    val onClick: () -> Unit,
    val icon: ImageVector = SurferIcons.Add,
    val testTag: String? = null,
)

/**
 * A [Scaffold] whose chrome adapts to the pane the host put the screen in — see [SurferPane].
 *
 * As a whole screen or as a list pane it is exactly the `Scaffold` + [SurferToolbar] + FAB every
 * screen used to build by hand. As the detail pane of a two-pane layout it drops the top app bar
 * and the FAB (its list pane is already drawing both, so drawing them again is what issue #388
 * showed twice at Expanded width) in favour of a [SurferPaneHeader]: a contextual header with no
 * navigation icon, carrying the same actions plus [primaryAction].
 *
 * @param onBack invoked by the back affordance. Kept in the signature even for a detail pane,
 *   which shows it only while [SurferPane.hasPaneBackStack] says popping still lands inside the
 *   pane.
 */
@Composable
fun SurferPaneScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    containerColor: Color = AppTheme.materialColors.surface,
    actions: @Composable RowScope.() -> Unit = {},
    primaryAction: SurferPaneAction? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val pane = LocalSurferPane.current
    Scaffold(
        modifier = Modifier.surferSafeInsets().then(modifier),
        containerColor = containerColor,
        topBar = {
            SurferPaneTopBar(
                title = title,
                onBack = onBack,
                actions = {
                    actions()
                    // The FAB slot is the list pane's; a detail pane keeps the action as a button.
                    if (!pane.ownsSectionChrome) primaryAction?.let { SurferPaneHeaderAction(it) }
                },
            )
        },
        floatingActionButton = {
            if (pane.ownsSectionChrome && primaryAction != null) {
                SurferAddFab(
                    label = primaryAction.label,
                    onClick = primaryAction.onClick,
                    icon = primaryAction.icon,
                    modifier = primaryAction.testTag?.let { Modifier.testTag(it) } ?: Modifier,
                )
            }
        },
        content = content,
    )
}

/**
 * The top bar of a pane-aware screen: a [SurferToolbar] while the screen owns the section chrome,
 * a [SurferPaneHeader] once it is a detail pane displayed beside its list.
 *
 * Exposed on its own for screens that build their own [Scaffold] because they need slots
 * [SurferPaneScaffold] does not carry (a snackbar host, an overlay) but want the same chrome rule.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurferPaneTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    actions: @Composable RowScope.() -> Unit = {},
) {
    val pane = LocalSurferPane.current
    if (pane.ownsSectionChrome) {
        SurferToolbar(
            title = title,
            modifier = modifier,
            onBack = onBack,
            colors = colors,
            actions = actions,
        )
    } else {
        SurferPaneHeader(
            title = title,
            modifier = modifier,
            onBack = onBack.takeIf { pane.showsBackNavigation },
            actions = actions,
        )
    }
}

@Composable
private fun SurferPaneHeaderAction(action: SurferPaneAction) {
    SurferToolbarButtonAction(
        icon = action.icon,
        text = action.label,
        onClick = action.onClick,
        modifier = action.testTag?.let { Modifier.testTag(it) } ?: Modifier,
    )
}

/**
 * The detail pane's contextual header: title and actions on one line, no navigation icon unless
 * [onBack] leads somewhere inside the pane.
 *
 * Deliberately not a `TopAppBar` — the whole point at Expanded width is that the section has one
 * top app bar, drawn by the list pane. This is part of the detail pane's content, so it also skips
 * the sync badge [SurferToolbar] adds.
 */
@Composable
fun SurferPaneHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = PaneHeaderMinHeight)
            .padding(start = if (onBack == null) TitleInset else IconInset, end = IconInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.testTag(SurferToolbarTestTags.Back)) {
                Icon(
                    imageVector = SurferIcons.Back,
                    contentDescription = stringResource(Res.string.uikit_back),
                )
            }
        }
        Text(
            text = title,
            style = AppTheme.typography.titleLarge,
            color = AppTheme.materialColors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        actions()
    }
}

/** Matches the `TopAppBar` height the list pane draws, so the two panes line up. */
private val PaneHeaderMinHeight = 64.dp
private val TitleInset = 16.dp
private val IconInset = 4.dp

@Preview
@Composable
private fun SurferPaneHeaderPreview() {
    SurferComponentPreview {
        SurferPaneHeader(
            title = "Account",
            actions = {
                SurferToolbarAction(
                    icon = SurferIcons.Edit,
                    contentDescription = "Edit",
                    onClick = {},
                )
                SurferToolbarButtonAction(icon = SurferIcons.Add, text = "Add", onClick = {})
            },
        )
    }
}

@Preview
@Composable
private fun SurferPaneHeaderWithBackPreview() {
    SurferComponentPreview {
        SurferPaneHeader(title = "Licenses", onBack = {})
    }
}

@Preview
@Composable
private fun SurferPaneScaffoldDetailPreview() {
    SurferComponentPreview {
        CompositionLocalProvider(
            LocalSurferPane provides SurferPane(role = SurferPaneRole.Detail),
        ) {
            SurferPaneScaffold(
                title = "Budget",
                onBack = {},
                primaryAction = SurferPaneAction(label = "Add", onClick = {}),
            ) { padding ->
                Text(text = "Detail pane content", modifier = Modifier.padding(padding))
            }
        }
    }
}
