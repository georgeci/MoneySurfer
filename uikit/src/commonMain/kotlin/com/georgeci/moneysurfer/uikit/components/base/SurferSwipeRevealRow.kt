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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * One revealed action, restated for assistive technology.
 *
 * A swipe is a gesture no screen reader user can perform, so anything hidden behind one is
 * unreachable unless it is also published as a semantic action. Pair each entry with the
 * [SurferSwipeAction] it mirrors.
 */
data class SurferSwipeAccessibilityAction(
    val label: String,
    val onAction: () -> Unit,
)

/**
 * Wraps a list-row style [content] with a horizontally-swipeable foreground that reveals an
 * [actions] layer pinned to the trailing edge. Used on the manage-accounts screen so each
 * account row can hide its destructive affordances behind a swipe.
 *
 * - [revealWidth] is the total horizontal travel — sized to fit the [actions] row's intrinsic
 *   width (e.g. two 72dp action buttons + 6dp gaps + 6dp padding ≈ 156dp).
 * - When [enabled] is false the wrapper short-circuits to plain [content] with no gesture and
 *   no action layer — useful while a list is in edit mode and swipe should be suppressed.
 * - [accessibilityActions] publishes the revealed actions as custom accessibility actions on the
 *   row. Supplying them also hides the action layer from the accessibility tree: it is composed
 *   whether or not the row is swiped open, so left alone a screen reader walks buttons the sighted
 *   user cannot see. Empty (the default) leaves existing callers exactly as they were.
 */
@Composable
fun SurferSwipeRevealRow(
    revealWidth: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accessibilityActions: List<SurferSwipeAccessibilityAction> = emptyList(),
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
        modifier = modifier
            .semanticActions(accessibilityActions)
            .draggable(
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
                .padding(6.dp)
                // Already offered as custom actions above; leaving the buttons in the tree as well
                // would make every row read twice.
                .then(if (accessibilityActions.isEmpty()) Modifier else Modifier.clearAndSetSemantics {}),
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
 * Merges the row into one accessibility node carrying [actions] as custom actions, so a screen
 * reader reaches them from the row itself. No-op when there are none, which keeps the semantics of
 * callers that have not opted in byte-for-byte unchanged.
 */
private fun Modifier.semanticActions(actions: List<SurferSwipeAccessibilityAction>): Modifier =
    if (actions.isEmpty()) {
        this
    } else {
        semantics(mergeDescendants = true) {
            customActions = actions.map { action ->
                // The `true` is the contract: it tells the accessibility framework the action was
                // handled here rather than falling through.
                CustomAccessibilityAction(action.label) {
                    action.onAction()
                    true
                }
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
            contentDescription = SurferSemantics.Decorative,
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
