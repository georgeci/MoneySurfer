package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Wraps a list-row style [content] with a horizontally-swipeable foreground that reveals an
 * [actions] layer pinned to the trailing edge. Used on the manage-accounts screen so each
 * account row can hide its destructive affordances behind a swipe.
 *
 * - [revealWidth] is the total horizontal travel — sized to fit the [actions] row's intrinsic
 *   width (e.g. two 72dp action buttons + 6dp gaps + 6dp padding ≈ 156dp).
 * - When [enabled] is false the wrapper short-circuits to plain [content] with no gesture and
 *   no action layer — useful while a list is in edit mode and swipe should be suppressed.
 */
@Composable
fun SurferSwipeRevealRow(
    revealWidth: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier) { content() }
        return
    }

    val density = LocalDensity.current
    val revealPx = with(density) { revealWidth.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier.draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                scope.launch {
                    offset.snapTo((offset.value + delta).coerceIn(-revealPx, 0f))
                }
            },
            onDragStopped = {
                val target = if (offset.value < -revealPx / 2f) -revealPx else 0f
                offset.animateTo(target)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
        Box(
            modifier = Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                layout(placeable.width, placeable.height) {
                    placeable.placeRelative(offset.value.roundToInt(), 0)
                }
            },
        ) {
            content()
        }
    }
}

/**
 * Standard trailing action used inside [SurferSwipeRevealRow.actions]. Stacks an icon over a
 * label, sized to the swipe-reveal track. Pass [destructive] = true for the delete-style action
 * (uses the error tonal pair).
 */
@Composable
fun SurferSwipeAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    width: Dp = 72.dp,
) {
    val bg = if (destructive) {
        AppTheme.materialColors.error
    } else {
        AppTheme.materialColors.surfaceContainerHighest
    }
    val fg = if (destructive) {
        AppTheme.materialColors.onError
    } else {
        AppTheme.materialColors.onSurface
    }
    Column(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = AppTheme.typography.labelMedium,
            color = fg,
        )
    }
}

@Preview
@Composable
private fun SurferSwipeRevealRowPreview() {
    SurferComponentPreview {
        SurferSwipeRevealRow(
            revealWidth = 156.dp,
            modifier = Modifier.padding(16.dp),
            actions = {
                SurferSwipeAction(
                    icon = SurferIcons.Archive,
                    label = "Archive",
                    onClick = {},
                )
                SurferSwipeAction(
                    icon = SurferIcons.Delete,
                    label = "Delete",
                    onClick = {},
                    destructive = true,
                )
            },
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppTheme.materialColors.surfaceContainerLow)
                    .padding(16.dp),
            ) {
                Text(
                    text = "Drag me left",
                    style = AppTheme.typography.bodyLarge,
                    color = Color.Unspecified,
                )
            }
        }
    }
}
