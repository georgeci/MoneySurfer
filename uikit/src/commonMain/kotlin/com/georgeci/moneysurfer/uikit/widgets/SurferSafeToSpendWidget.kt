package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetProgressBar
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetStatus
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * The numbers the safe-to-spend card draws, already formatted and localized by the caller — uikit
 * neither knows the currency nor owns the copy.
 *
 * [progress] is spend against the limit and [paceFraction] is how much of the period has gone, so
 * the bar reads as a pace: fill behind the tick means the money is outlasting the days.
 */
data class SurferSafeToSpendData(
    /** The headline — what is still safe to spend. Signed, so an overspent budget reads negative. */
    val amount: String,
    /** One line under the headline: the limit the headline is measured against, and whose it is. */
    val caption: String,
    val perDay: String,
    val daysLeft: String,
    val progress: Float,
    val paceFraction: Float,
    val status: SurferBudgetStatus,
    /** What a screen reader gets for the bar; the shape restates numbers already in the text. */
    val progressContentDescription: String? = null,
)

/**
 * The "no budget yet" half of the card: what it says, and the way out of it.
 *
 * Grouped rather than spread across the widget's parameter list, the way `SurferAddAccountCta`
 * carries the accounts widget's empty-state call to action. [actionTestTag] tags the link; the host
 * screen owns the value, since where it leads is a step in the host's flow.
 */
data class SurferSafeToSpendEmpty(
    val title: String? = null,
    val subtitle: String? = null,
    val actionLabel: String? = null,
    val onActionClick: (() -> Unit)? = null,
    val actionTestTag: String? = null,
)

/**
 * Safe-to-spend widget for the dashboard column: one number, the pace it has to hold, and how long
 * it has to hold it.
 *
 * A null [data] draws [empty] instead — the card keeps its heading and offers the way out rather
 * than disappearing, because a widget the user switched on should still say why it has nothing to
 * show.
 */
@Composable
fun SurferSafeToSpendWidget(
    title: String,
    data: SurferSafeToSpendData?,
    modifier: Modifier = Modifier,
    size: SurferWidgetSize = LocalSurferWidgetSize.current,
    empty: SurferSafeToSpendEmpty = SurferSafeToSpendEmpty(),
) {
    SurferWidgetCard(
        title = title,
        modifier = modifier,
        trailing = { if (data == null) EmptyStateAction(empty) },
    ) {
        if (data == null) {
            SurferWidgetEmptyState(
                icon = SurferIcons.Cash,
                title = empty.title,
                subtitle = empty.subtitle,
            )
            return@SurferWidgetCard
        }
        SafeToSpendBody(data = data, sizing = safeToSpendSizing(size == SurferWidgetSize.Expanded))
    }
}

@Composable
private fun EmptyStateAction(empty: SurferSafeToSpendEmpty) {
    val label = empty.actionLabel ?: return
    val onClick = empty.onActionClick ?: return
    Text(
        text = label,
        style = AppTheme.typography.labelMedium,
        color = AppTheme.materialColors.primary,
        modifier = Modifier
            .clickable(onClick = onClick)
            .then(empty.actionTestTag?.let { Modifier.testTag(it) } ?: Modifier),
    )
}

/**
 * Everything the Compact/Hero switch changes, resolved in one place so the body below reads as a
 * single layout instead of a column of size ternaries.
 */
private data class SafeToSpendSizing(
    val topPadding: Dp,
    val gap: Dp,
    val amountStyle: TextStyle,
    val figureStyle: TextStyle,
    val barHeight: Dp,
    /**
     * Compact drops the caption rather than shrinking every line: the headline, the pace bar and
     * the two figures under it are what the widget is for, and the limit is restated on the budget
     * screen.
     */
    val showsCaption: Boolean,
)

@Composable
private fun safeToSpendSizing(hero: Boolean): SafeToSpendSizing = if (hero) {
    SafeToSpendSizing(
        topPadding = 10.dp,
        gap = 10.dp,
        amountStyle = AppTheme.typography.displaySmall,
        figureStyle = AppTheme.typography.labelLarge,
        barHeight = 10.dp,
        showsCaption = true,
    )
} else {
    SafeToSpendSizing(
        topPadding = 6.dp,
        gap = 6.dp,
        amountStyle = AppTheme.typography.headlineSmall,
        figureStyle = AppTheme.typography.labelSmall,
        barHeight = 8.dp,
        showsCaption = false,
    )
}

@Composable
private fun SafeToSpendBody(data: SurferSafeToSpendData, sizing: SafeToSpendSizing) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = sizing.topPadding),
        verticalArrangement = Arrangement.spacedBy(sizing.gap),
    ) {
        Text(
            text = data.amount,
            style = sizing.amountStyle,
            color = if (data.status == SurferBudgetStatus.Over) {
                AppTheme.materialColors.error
            } else {
                AppTheme.materialColors.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (sizing.showsCaption) {
            Text(
                text = data.caption,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SurferBudgetProgressBar(
            progress = data.progress,
            status = data.status,
            tickFraction = data.paceFraction,
            height = sizing.barHeight,
            contentDescription = data.progressContentDescription,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = data.perDay,
                style = sizing.figureStyle,
                color = AppTheme.materialColors.onSurface,
            )
            Text(
                text = data.daysLeft,
                style = sizing.figureStyle,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

private const val PREVIEW_TITLE = "Safe to spend"

private val previewData = SurferSafeToSpendData(
    amount = "€642.30",
    caption = "of €1,800 · Everyday",
    perDay = "€53.52 a day",
    daysLeft = "12 days left",
    progress = 0.64f,
    paceFraction = 0.6f,
    status = SurferBudgetStatus.Ok,
)

@Preview
@Composable
private fun SurferSafeToSpendWidgetHeroPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferSafeToSpendWidget(
                title = PREVIEW_TITLE,
                data = previewData,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SurferSafeToSpendWidgetCompactPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferSafeToSpendWidget(
                title = PREVIEW_TITLE,
                data = previewData,
                size = SurferWidgetSize.Compact,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SurferSafeToSpendWidgetOverPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferSafeToSpendWidget(
                title = PREVIEW_TITLE,
                data = previewData.copy(
                    amount = "−€120.00",
                    perDay = "€0.00 a day",
                    daysLeft = "4 days left",
                    progress = 1.07f,
                    paceFraction = 0.87f,
                    status = SurferBudgetStatus.Over,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SurferSafeToSpendWidgetEmptyPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferSafeToSpendWidget(
                title = PREVIEW_TITLE,
                data = null,
                empty = SurferSafeToSpendEmpty(
                    title = "No budget yet",
                    subtitle = "Set a cap to see what is safe to spend.",
                    actionLabel = "Set a budget",
                    onActionClick = {},
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
        }
    }
}
