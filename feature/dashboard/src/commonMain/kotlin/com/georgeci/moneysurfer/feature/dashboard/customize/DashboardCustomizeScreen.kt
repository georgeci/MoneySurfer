package com.georgeci.moneysurfer.feature.dashboard.customize

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutItem
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.uikit.components.SurferSkeletonRow
import com.georgeci.moneysurfer.uikit.components.base.SurferDragHandle
import com.georgeci.moneysurfer.uikit.components.base.SurferSectionHeader
import com.georgeci.moneysurfer.uikit.components.base.SurferSectionHeaderHint
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.base.rememberSurferReorderState
import com.georgeci.moneysurfer.uikit.components.base.surferReorderHandle
import com.georgeci.moneysurfer.uikit.components.base.surferReorderableItem
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRow
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsSwitch
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsValuePill
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferContentContainer
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.AsyncState
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.dashboard.generated.resources.Res
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_available_empty
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_enabled_empty
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_footnote
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_reorder_hint
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_section_available
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_section_enabled
import moneysurfer.feature.dashboard.generated.resources.dashboard_customize_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** Stable selectors for the customize screen — see docs/testing/testing-strategy.md. */
object DashboardCustomizeTestTags {
    const val Root = "dashboardCustomize:root"
    const val EnabledHeader = "dashboardCustomize:enabledHeader"
    const val AvailableHeader = "dashboardCustomize:availableHeader"
    const val ReorderHint = "dashboardCustomize:reorderHint"

    /** Row in the "On dashboard" section — [widget] is the [DashboardWidgetType] name. */
    fun enabledRow(widget: String): String = "dashboardCustomize:on:$widget"

    /** Row in the "Available" section. */
    fun availableRow(widget: String): String = "dashboardCustomize:available:$widget"

    /** Visibility switch inside a row of either section; a widget appears in exactly one. */
    fun toggle(widget: String): String = "dashboardCustomize:toggle:$widget"

    /** Reorder grip, present only on rows in the "On dashboard" section. */
    fun dragHandle(widget: String): String = "dashboardCustomize:drag:$widget"

    /** The "Hero · Classic" pill on an on-dashboard row; opens the card-style sheet. */
    fun styleAction(widget: String): String = "dashboardCustomize:styleAction:$widget"

    /** The card-style sheet, once opened for a widget. */
    fun styleSheet(widget: String): String = "dashboardCustomize:style:$widget"

    /** A preview tile in that sheet — [option] is a size name or a variant key. */
    fun styleOption(widget: String, option: String): String = "dashboardCustomize:style:$widget:$option"
}

@Composable
fun DashboardCustomizeScreen(
    onNavigateBack: () -> Unit,
    viewModel: DashboardCustomizeViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            DashboardCustomizeEffect.NavigateBack -> onNavigateBack()
        }
    }

    DashboardCustomizeContent(state = state, onEvent = viewModel::onEvent)
}

private const val CUSTOMIZE_SKELETON_ROWS = 4

/**
 * Stateless half of the screen — public so `:composeApp` desktop UI tests can mount it with an
 * injected state, the way `SignInContent` is used.
 */
@Composable
fun DashboardCustomizeContent(
    state: AsyncState<DashboardLayoutConfig>,
    onEvent: (DashboardCustomizeEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .surferTestTagAsId()
            .testTag(DashboardCustomizeTestTags.Root),
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.dashboard_customize_title),
                onBack = { onEvent(DashboardCustomizeEvent.OnBackClick) },
            )
        },
    ) { padding ->
        when (state) {
            AsyncState.Loading -> CustomizeSkeleton(padding)
            is AsyncState.Content -> CustomizeList(
                layout = state.value,
                padding = padding,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun CustomizeSkeleton(padding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .surferContentContainer()
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(CUSTOMIZE_SKELETON_ROWS) { SurferSkeletonRow() }
    }
}

@Composable
private fun CustomizeList(
    layout: DashboardLayoutConfig,
    padding: PaddingValues,
    onEvent: (DashboardCustomizeEvent) -> Unit,
) {
    val enabled = layout.enabledItems
    val available = layout.disabledItems
    // Which widget's style sheet is open, by type name — the type itself is not saveable. Held
    // here rather than in the ViewModel because it is sheet chrome, not part of the layout.
    var styleTarget by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val reorderState = rememberSurferReorderState(
        listState = listState,
        keys = enabled.map { it.type.name },
        onMove = { from, to ->
            val fromType = enabled.firstOrNull { it.type.name == from }?.type
            val toType = enabled.firstOrNull { it.type.name == to }?.type
            if (fromType != null && toType != null) {
                onEvent(DashboardCustomizeEvent.OnWidgetMove(from = fromType, to = toType))
            }
        },
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .surferContentContainer()
            .padding(top = padding.calculateTopPadding()),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header-enabled") {
            SectionHeader(
                title = stringResource(Res.string.dashboard_customize_section_enabled),
                modifier = Modifier.testTag(DashboardCustomizeTestTags.EnabledHeader),
                // Nothing to reorder with a single widget on the dashboard.
                hint = stringResource(Res.string.dashboard_customize_reorder_hint)
                    .takeIf { enabled.size > 1 },
            )
        }
        if (enabled.isEmpty()) {
            item(key = "empty-enabled") {
                SectionNote(text = stringResource(Res.string.dashboard_customize_enabled_empty))
            }
        }
        items(items = enabled, key = { it.type.name }) { item ->
            EnabledWidgetRow(
                item = item,
                modifier = surferReorderableItem(reorderState, item.type.name),
                handleModifier = Modifier.surferReorderHandle(reorderState, item.type.name),
                onStyleClick = { styleTarget = item.type.name },
                onEvent = onEvent,
            )
        }

        item(key = "header-available") {
            SectionHeader(
                title = stringResource(Res.string.dashboard_customize_section_available),
                modifier = Modifier
                    .padding(top = 16.dp)
                    .testTag(DashboardCustomizeTestTags.AvailableHeader),
            )
        }
        if (available.isEmpty()) {
            item(key = "empty-available") {
                SectionNote(text = stringResource(Res.string.dashboard_customize_available_empty))
            }
        }
        items(items = available, key = { "available-${it.type.name}" }) { item ->
            AvailableWidgetRow(item = item, onEvent = onEvent)
        }

        item(key = "footnote") {
            SectionNote(
                text = stringResource(Res.string.dashboard_customize_footnote),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }

    // Resolved against the live layout, not captured when the sheet opened, so the tiles show the
    // selection the last tap produced. A widget switched off while its sheet is open closes it.
    val styled = styleTarget?.let { name -> enabled.firstOrNull { it.type.name == name } }
    if (styled != null) {
        DashboardCardStyleSheet(
            item = styled,
            onSelect = { onEvent(DashboardCustomizeEvent.OnCardStyleChange(styled.type, it)) },
            onDismiss = { styleTarget = null },
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    SurferSectionHeader(
        title = title,
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        trailing = hint?.let {
            {
                SurferSectionHeaderHint(
                    text = it,
                    modifier = Modifier.testTag(DashboardCustomizeTestTags.ReorderHint),
                )
            }
        },
    )
}

@Composable
private fun SectionNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = AppTheme.typography.bodySmall,
        color = AppTheme.materialColors.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun EnabledWidgetRow(
    item: DashboardLayoutItem,
    modifier: Modifier,
    handleModifier: Modifier,
    onStyleClick: () -> Unit,
    onEvent: (DashboardCustomizeEvent) -> Unit,
) {
    val widget = item.type.name
    SurferSettingsRow(
        title = stringResource(item.type.titleResource()),
        modifier = modifier.testTag(DashboardCustomizeTestTags.enabledRow(widget)),
        icon = item.type.icon(),
        // The pill carries the click, not the whole row: a clickable row merges its subtree
        // semantics, which would swallow the drag grip and the switch as addressable nodes.
        supporting = {
            SurferSettingsValuePill(
                text = cardStyleSummary(item.type, item.cardStyle),
                modifier = Modifier
                    .clip(AppTheme.shapes.small)
                    .clickable(onClick = onStyleClick)
                    .padding(vertical = 4.dp, horizontal = 2.dp)
                    .testTag(DashboardCustomizeTestTags.styleAction(widget)),
            )
        },
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SurferSettingsSwitch(
                    checked = true,
                    onCheckedChange = {
                        onEvent(DashboardCustomizeEvent.OnWidgetEnabledChange(item.type, enabled = false))
                    },
                    modifier = Modifier.testTag(DashboardCustomizeTestTags.toggle(widget)),
                )
                SurferDragHandle(
                    modifier = handleModifier.testTag(DashboardCustomizeTestTags.dragHandle(widget)),
                )
            }
        },
    )
}

@Composable
private fun AvailableWidgetRow(
    item: DashboardLayoutItem,
    onEvent: (DashboardCustomizeEvent) -> Unit,
) {
    val widget = item.type.name
    val turnOn = { onEvent(DashboardCustomizeEvent.OnWidgetEnabledChange(item.type, enabled = true)) }
    SurferSettingsRow(
        title = stringResource(item.type.titleResource()),
        modifier = Modifier.testTag(DashboardCustomizeTestTags.availableRow(widget)),
        icon = item.type.icon(),
        trailing = {
            SurferSettingsSwitch(
                checked = false,
                onCheckedChange = { turnOn() },
                modifier = Modifier.testTag(DashboardCustomizeTestTags.toggle(widget)),
            )
        },
        onClick = turnOn,
    )
}

private fun DashboardWidgetType.icon(): ImageVector = when (this) {
    DashboardWidgetType.Balance -> SurferIcons.Wallet
    DashboardWidgetType.QuickActions -> SurferIcons.Bolt
    DashboardWidgetType.SafeToSpend -> SurferIcons.Cash
    DashboardWidgetType.Budgets -> SurferIcons.Category
    DashboardWidgetType.Accounts -> SurferIcons.Bank
    DashboardWidgetType.Goals -> SurferIcons.Savings
    DashboardWidgetType.RecentTransactions -> SurferIcons.Receipt
}

@Preview
@Composable
private fun DashboardCustomizePreview() {
    SurferComponentPreview {
        DashboardCustomizeContent(
            state = AsyncState.Content(
                DashboardLayoutConfig.DEFAULT.withWidgetEnabled(DashboardWidgetType.Goals, enabled = false),
            ),
            onEvent = {},
        )
    }
}
