package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

private val PagerHeight = 40.dp
private val PagerShape = RoundedCornerShape(20.dp)
private val ArrowSize = 32.dp
private val ArrowIconSize = 18.dp

/** Alpha the arrows fade to when there is nothing to page to — from the design's `PeriodPager`. */
private const val DISABLED_ARROW_ALPHA = 0.4f

/**
 * One pager arrow's behaviour: what tapping it does, whether it is live, and its a11y label.
 * Bundling the three keeps [SurferPeriodPager]'s own parameter list readable rather than fanning
 * every arrow property out into a `previousX` / `nextX` pair.
 */
data class SurferPeriodArrow(
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val contentDescription: String? = null,
)

/**
 * Compact pager pill for stepping through a period: `‹ March 2025 ›`.
 *
 * Stateless — the caller advances via [previous] / [next]. [sublabel] is the smaller trailing
 * caption (the year, an ISO week, "No date filter"); [onLabelClick] makes the centre tappable for
 * callers that hang a period-mode menu off it.
 */
@Composable
fun SurferPeriodPager(
    label: String,
    previous: SurferPeriodArrow,
    next: SurferPeriodArrow,
    modifier: Modifier = Modifier,
    sublabel: String? = null,
    onLabelClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(PagerHeight)
            .clip(PagerShape)
            .background(AppTheme.materialColors.surfaceContainerLow)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PagerArrow(
            icon = SurferIcons.ChevronLeft,
            contentDescription = previous.contentDescription,
            enabled = previous.enabled,
            onClick = previous.onClick,
        )
        Row(
            modifier = Modifier
                .clip(PagerShape)
                .then(if (onLabelClick != null) Modifier.clickable(onClick = onLabelClick) else Modifier)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = AppTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = AppTheme.materialColors.onSurface,
                maxLines = 1,
            )
            if (sublabel != null) {
                Text(
                    text = sublabel,
                    style = AppTheme.typography.labelSmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        PagerArrow(
            icon = SurferIcons.ChevronRight,
            contentDescription = next.contentDescription,
            enabled = next.enabled,
            onClick = next.onClick,
        )
    }
}

@Composable
private fun PagerArrow(
    icon: ImageVector,
    contentDescription: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(ArrowSize),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = AppTheme.materialColors.onSurfaceVariant.copy(
                alpha = if (enabled) 1f else DISABLED_ARROW_ALPHA,
            ),
            modifier = Modifier.size(ArrowIconSize),
        )
    }
}

@Preview
@Composable
private fun SurferPeriodPagerPreview() {
    SurferComponentPreview {
        SurferPeriodPager(
            modifier = Modifier.padding(all = 16.dp),
            label = "March",
            sublabel = "2025",
            previous = SurferPeriodArrow(onClick = {}),
            next = SurferPeriodArrow(onClick = {}),
        )
    }
}

@Preview
@Composable
private fun SurferPeriodPagerAllTimePreview() {
    SurferComponentPreview {
        SurferPeriodPager(
            modifier = Modifier.padding(all = 16.dp),
            label = "All time",
            sublabel = "No date filter",
            previous = SurferPeriodArrow(onClick = {}, enabled = false),
            next = SurferPeriodArrow(onClick = {}, enabled = false),
        )
    }
}
