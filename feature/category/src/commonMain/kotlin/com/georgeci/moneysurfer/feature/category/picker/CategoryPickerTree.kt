package com.georgeci.moneysurfer.feature.category.picker

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.uikit.components.SurferCategoryBubble
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.category.generated.resources.Res
import moneysurfer.feature.category.generated.resources.category_picker_new_category
import moneysurfer.feature.category.generated.resources.category_picker_subcategories
import moneysurfer.feature.category.generated.resources.category_picker_toggle_children
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private val TreeMinHeight = 200.dp
private val TreeMaxHeight = 420.dp
private val CardRadius = 18.dp
private val CardSpacing = 8.dp
private val ParentBubbleSize = 40.dp
private val ChildBubbleSize = 28.dp
private val RowVerticalPadding = 12.dp
private val RowHorizontalPadding = 12.dp
private val BubbleSpacing = 12.dp

/** Where the L-connector's rules sit, measured from the card's leading edge. */
private val ConnectorX = 33.dp
private val ConnectorArm = 11.dp
private val ConnectorStroke = 1.5.dp
private val ChildContentStart = 48.dp

private val RadioSize = 22.dp
private val RadioStroke = 2.dp
private val RadioCheckSize = 14.dp
private val HairlineHeight = 1.dp
private val DashStroke = 1.5.dp
private val DashOn = 5.dp
private val DashOff = 4.dp
private const val ChevronExpandedDegrees = 180f
private const val ChevronRotationMillis = 140

/**
 * Variant B of the picker: one card per top-level category, with its children folded away behind
 * a chevron until asked for. Built for someone browsing rather than searching — the grid answers
 * "where is Groceries", this answers "what is under Food".
 *
 * A parent is selectable in its own right even when it has children: the row selects, the chevron
 * expands, and they are separate hit targets so neither steals the other's tap.
 */
@Composable
internal fun CategoryPickerTree(
    groups: List<CategoryTreeGroup>,
    onSelect: (CategoryId) -> Unit,
    onToggleExpand: (CategoryId) -> Unit,
    onCreateNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = TreeMinHeight, max = TreeMaxHeight),
        contentPadding = PaddingValues(horizontal = SheetBodyPadding),
        verticalArrangement = Arrangement.spacedBy(CardSpacing),
    ) {
        items(groups, key = { it.parent.id.value }) { group ->
            CategoryTreeCard(
                group = group,
                onSelect = onSelect,
                onToggleExpand = onToggleExpand,
            )
        }
        item(key = "create-new") {
            NewCategoryRow(onClick = onCreateNew)
        }
    }
}

@Composable
private fun CategoryTreeCard(
    group: CategoryTreeGroup,
    onSelect: (CategoryId) -> Unit,
    onToggleExpand: (CategoryId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(AppTheme.materialColors.surfaceContainerLowest)
            .border(
                width = HairlineHeight,
                color = AppTheme.materialColors.outlineVariant,
                shape = RoundedCornerShape(CardRadius),
            ),
    ) {
        ParentRow(
            group = group,
            onSelect = { onSelect(group.parent.id) },
            onToggleExpand = { onToggleExpand(group.parent.id) },
        )
        if (group.children.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HairlineHeight)
                    .background(AppTheme.materialColors.outlineVariant),
            )
            Column(modifier = Modifier.background(AppTheme.materialColors.surfaceContainerLow)) {
                group.children.forEachIndexed { index, child ->
                    ChildRow(
                        child = child,
                        isLast = index == group.children.lastIndex,
                        onSelect = { onSelect(child.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ParentRow(
    group: CategoryTreeGroup,
    onSelect: () -> Unit,
    onToggleExpand: () -> Unit,
) {
    val parent = group.parent
    val visual = SurferCategoryPalette.visualFor(
        id = parent.id.value,
        iconKey = parent.iconKey,
        hue = parent.hue,
        systemKind = parent.systemKind,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(selectionBackground(parent.selected))
            .clickable(onClick = onSelect)
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SurferCategoryBubble(
            icon = visual.icon,
            tint = visual.tint,
            size = ParentBubbleSize,
            modifier = Modifier.padding(end = BubbleSpacing),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = parent.name,
                style = AppTheme.typography.titleSmall,
                color = AppTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (group.childCount > 0) {
                Text(
                    text = pluralStringResource(
                        Res.plurals.category_picker_subcategories,
                        group.childCount,
                        group.childCount,
                    ),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
            }
        }
        SelectionRadio(selected = parent.selected)
        if (group.childCount > 0) {
            val rotation by animateFloatAsState(
                targetValue = if (group.expanded) ChevronExpandedDegrees else 0f,
                animationSpec = tween(ChevronRotationMillis),
                label = "chevron",
            )
            IconButton(onClick = onToggleExpand) {
                Icon(
                    imageVector = SurferIcons.DropDown,
                    contentDescription = stringResource(Res.string.category_picker_toggle_children),
                    tint = AppTheme.materialColors.onSurfaceVariant,
                    modifier = Modifier.rotate(rotation),
                )
            }
        }
    }
}

@Composable
private fun ChildRow(
    child: CategoryPickerItem,
    isLast: Boolean,
    onSelect: () -> Unit,
) {
    val visual = SurferCategoryPalette.visualFor(
        id = child.id.value,
        iconKey = child.iconKey,
        hue = child.hue,
        systemKind = child.systemKind,
    )
    val connector = AppTheme.materialColors.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(selectionBackground(child.selected))
            .clickable(onClick = onSelect)
            .drawBehind {
                val stroke = ConnectorStroke.toPx()
                val x = ConnectorX.toPx()
                val midY = size.height / 2f
                // The last child ends the run, so its vertical rule stops at the elbow instead
                // of carrying on into empty space.
                drawLine(
                    color = connector,
                    start = Offset(x, 0f),
                    end = Offset(x, if (isLast) midY else size.height),
                    strokeWidth = stroke,
                )
                drawLine(
                    color = connector,
                    start = Offset(x, midY),
                    end = Offset(x + ConnectorArm.toPx(), midY),
                    strokeWidth = stroke,
                )
            }
            .padding(
                start = ChildContentStart,
                end = RowHorizontalPadding,
                top = RowVerticalPadding,
                bottom = RowVerticalPadding,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SurferCategoryBubble(
            icon = visual.icon,
            tint = visual.tint,
            size = ChildBubbleSize,
            modifier = Modifier.padding(end = BubbleSpacing),
        )
        Text(
            text = child.name,
            style = AppTheme.typography.bodyLarge,
            color = AppTheme.materialColors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        SelectionRadio(selected = child.selected)
    }
}

@Composable
private fun SelectionRadio(selected: Boolean) {
    val primary = AppTheme.materialColors.primary
    val outline = AppTheme.materialColors.outline
    Box(
        modifier = Modifier
            .size(RadioSize)
            .clip(CircleShape)
            .background(if (selected) primary else Color.Transparent)
            .drawBehind {
                if (selected) return@drawBehind
                val stroke = RadioStroke.toPx()
                drawCircle(
                    color = outline,
                    radius = (size.minDimension - stroke) / 2f,
                    style = Stroke(width = stroke),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = SurferIcons.Check,
                // decorative — the row's own name is the accessible label
                contentDescription = null,
                tint = AppTheme.materialColors.onPrimary,
                modifier = Modifier.size(RadioCheckSize),
            )
        }
    }
}

@Composable
private fun NewCategoryRow(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outline = AppTheme.materialColors.outline
    val shape = RoundedCornerShape(CardRadius)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = CardSpacing)
            .clip(shape)
            .clickable(onClick = onClick)
            .drawBehind {
                val stroke = DashStroke.toPx()
                drawRoundRect(
                    color = outline,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(CardRadius.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(DashOn.toPx(), DashOff.toPx()),
                        ),
                    ),
                )
            }
            .padding(horizontal = RowHorizontalPadding, vertical = RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = SurferIcons.Add,
            // decorative — the label next to it names the action
            contentDescription = null,
            tint = AppTheme.materialColors.primary,
            modifier = Modifier
                .padding(end = BubbleSpacing)
                .size(ParentBubbleSize / 2),
        )
        Text(
            text = stringResource(Res.string.category_picker_new_category),
            style = AppTheme.typography.labelLarge,
            color = AppTheme.materialColors.primary,
        )
    }
}

@Composable
private fun selectionBackground(selected: Boolean): Color =
    if (selected) AppTheme.materialColors.secondaryContainer else Color.Transparent
