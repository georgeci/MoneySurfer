package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.uikit.generated.resources.Res
import moneysurfer.uikit.generated.resources.uikit_app_icon
import moneysurfer.uikit.generated.resources.uikit_brand
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** One destination row of [SurferNavigationDrawer]. */
@Immutable
data class SurferDrawerItem(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * A group of [items] under an optional caption.
 *
 * [label] is rendered uppercase by the drawer, so pass it in its natural case ("Manage") — the
 * same string is what a screen reader announces and what a rail would show if it ever grew
 * grouping.
 */
@Immutable
data class SurferDrawerSection(
    val items: List<SurferDrawerItem>,
    val label: String? = null,
)

/**
 * The permanent navigation drawer for wide windows: brand header, grouped destinations, and a
 * user/workspace block pinned to the bottom.
 *
 * `NavigationSuiteScaffold`'s own drawer models none of those three (issue #389, gap G5) — it
 * takes a flat item list and nothing else — which is why this is a component rather than a
 * configuration of it. It is deliberately presentational: it knows nothing about routes, so the
 * caller maps its destinations onto [sections] and keeps the selection rule.
 *
 * Sized to the design's 210 dp sidebar. The destination list scrolls if the window is too short
 * to hold every group, so the footer stays reachable rather than being pushed off the bottom.
 *
 * @param userName primary line of the footer — a display name, an email, or a placeholder.
 * @param workspaceName secondary line; omitted when no workspace is selected yet.
 * @param onUserClick makes the footer a button (the workspace switcher). Null leaves it inert.
 * @param windowInsets the insets the drawer keeps its content clear of. Owned here rather than by
 *   the host for the same reason `NavigationRail` owns its own: the surface has to paint edge to
 *   edge — under the status bar and any start-edge cutout — while the brand header and the user
 *   footer stay out from under them. The default matches `NavigationRailDefaults.windowInsets`.
 */
@Composable
fun SurferNavigationDrawer(
    sections: List<SurferDrawerSection>,
    userName: String,
    modifier: Modifier = Modifier,
    brand: String = stringResource(Res.string.uikit_brand),
    workspaceName: String? = null,
    onUserClick: (() -> Unit)? = null,
    windowInsets: WindowInsets =
        WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
) {
    Row(modifier = modifier.fillMaxHeight().width(DrawerWidth)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(AppTheme.materialColors.surfaceContainerLow)
                // Inside the background, so the surface still reaches the window edge.
                .windowInsetsPadding(windowInsets)
                .testTag(SurferNavigationDrawerTestTags.Root),
        ) {
            BrandHeader(brand = brand)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .selectableGroup(),
            ) {
                sections.forEach { section -> DrawerSection(section = section) }
            }
            UserFooter(
                userName = userName,
                workspaceName = workspaceName,
                onClick = onUserClick,
            )
        }
        VerticalDivider(color = AppTheme.materialColors.outlineVariant)
    }
}

/** Stable selectors for [SurferNavigationDrawer] — see docs/testing/testing-strategy.md. */
object SurferNavigationDrawerTestTags {
    const val Root = "drawer:root"
    const val User = "drawer:user"
}

@Composable
private fun BrandHeader(brand: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HorizontalInset, vertical = AppTheme.spacing.default),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        Image(
            painter = painterResource(Res.drawable.uikit_app_icon),
            contentDescription = SurferSemantics.Decorative,
            modifier = Modifier
                .size(BrandLogoSize)
                .clip(RoundedCornerShape(BrandLogoCorner)),
        )
        Text(
            text = brand,
            style = AppTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = AppTheme.materialColors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DrawerSection(section: SurferDrawerSection) {
    if (section.label != null) {
        Text(
            // Uppercased here rather than by the caller so the label stays readable at its
            // source and in the accessibility tree.
            text = section.label.uppercase(),
            style = AppTheme.typography.labelSmall,
            color = AppTheme.materialColors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(
                start = HorizontalInset + ItemInset,
                end = HorizontalInset,
                top = AppTheme.spacing.default,
                bottom = AppTheme.spacing.xSmall,
            ),
        )
    }
    section.items.forEach { item -> DrawerItemRow(item = item) }
}

@Composable
private fun DrawerItemRow(item: SurferDrawerItem) {
    val contentColor = if (item.selected) {
        AppTheme.materialColors.onSecondaryContainer
    } else {
        AppTheme.materialColors.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = HorizontalInset, vertical = ItemGap)
            .clip(RoundedCornerShape(ItemCorner))
            .background(
                if (item.selected) AppTheme.materialColors.secondaryContainer else Color.Transparent,
            )
            .selectable(
                selected = item.selected,
                role = Role.Tab,
                onClick = item.onClick,
            )
            .height(ItemHeight)
            .padding(horizontal = ItemInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = SurferSemantics.Decorative,
            tint = contentColor,
            modifier = Modifier.size(ItemIconSize),
        )
        Text(
            text = item.label,
            style = AppTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UserFooter(
    userName: String,
    workspaceName: String?,
    onClick: (() -> Unit)?,
) {
    HorizontalDivider(color = AppTheme.materialColors.outlineVariant)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = HorizontalInset, vertical = AppTheme.spacing.medium)
            .testTag(SurferNavigationDrawerTestTags.User),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        Box(
            modifier = Modifier
                .size(AvatarSize)
                .clip(CircleShape)
                .background(AppTheme.materialColors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = userName.firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                style = AppTheme.typography.labelLarge,
                color = AppTheme.materialColors.onPrimaryContainer,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = userName,
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (workspaceName != null) {
                Text(
                    text = workspaceName,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The design's 210 dp sidebar, divider included. */
private val DrawerWidth: Dp = 210.dp
private val BrandLogoSize: Dp = 34.dp
private val BrandLogoCorner: Dp = 10.dp
private val HorizontalInset: Dp = 10.dp
private val ItemInset: Dp = 10.dp
private val ItemGap: Dp = 2.dp

/** The 48 dp minimum touch target — `Modifier.selectable` does not expand one of its own. */
private val ItemHeight: Dp = 48.dp
private val ItemCorner: Dp = 10.dp
private val ItemIconSize: Dp = 20.dp
private val AvatarSize: Dp = 32.dp

private fun previewItem(
    label: String,
    icon: ImageVector,
    selected: Boolean = false,
) = SurferDrawerItem(label = label, icon = icon, selected = selected, onClick = {})

@Preview
@Composable
private fun SurferNavigationDrawerPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.height(520.dp)) {
            SurferNavigationDrawer(
                sections = listOf(
                    SurferDrawerSection(
                        items = listOf(
                            previewItem("Dashboard", SurferIcons.Dashboard, selected = true),
                            previewItem("Accounts", SurferIcons.Wallet),
                            previewItem("Transactions", SurferIcons.SwapHoriz),
                            previewItem("Budgets", SurferIcons.Savings),
                            previewItem("Goals", SurferIcons.Flag),
                        ),
                    ),
                    SurferDrawerSection(
                        label = "Manage",
                        items = listOf(
                            previewItem("Categories", SurferIcons.Category),
                            previewItem("Settings", SurferIcons.Settings),
                        ),
                    ),
                ),
                userName = "Georgy",
                workspaceName = "Household budget",
                onUserClick = {},
            )
        }
    }
}
