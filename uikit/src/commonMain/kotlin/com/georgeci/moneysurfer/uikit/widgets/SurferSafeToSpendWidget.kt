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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
 * Safe-to-spend widget for the dashboard column: one number, the pace it has to hold, and how long
 * it has to hold it.
 *
 * A null [data] is the "no budget yet" state — the card keeps its heading and offers the way out of
 * it ([emptyActionLabel]) rather than disappearing, because a widget the user switched on should
 * still say why it has nothing to show. [emptyActionTestTag] tags that link; the host screen owns
 * the value, since where it leads is a step in the host's flow.
 */
@Composable
fun SurferSafeToSpendWidget(
    title: String,
    data: SurferSafeToSpendData?,
    modifier: Modifier = Modifier,
    size: SurferWidgetSize = LocalSurferWidgetSize.current,
    emptyTitle: String? = null,
    emptySubtitle: String? = null,
    emptyActionLabel: String? = null,
    onEmptyActionClick: (() -> Unit)? = null,
    emptyActionTestTag: String? = null,
) {
    SurferWidgetCard(
        title = title,
        modifier = modifier,
        trailing = {
            if (data == null && emptyActionLabel != null && onEmptyActionClick != null) {
                Text(
                    text = emptyActionLabel,
                    style = AppTheme.typography.labelMedium,
                    color = AppTheme.materialColors.primary,
                    modifier = Modifier
                        .clickable(onClick = onEmptyActionClick)
                        .then(emptyActionTestTag?.let { Modifier.testTag(it) } ?: Modifier),
                )
            }
        },
    ) {
        if (data == null) {
            SurferWidgetEmptyState(
                icon = SurferIcons.Cash,
                title = emptyTitle,
                subtitle = emptySubtitle,
            )
            return@SurferWidgetCard
        }
        SafeToSpendBody(data = data, hero = size == SurferWidgetSize.Expanded)
    }
}

/**
 * Compact drops the caption rather than shrinking every line: the headline, the pace bar and the
 * two figures under it are what the widget is for, and the limit is restated on the budget screen.
 */
@Composable
private fun SafeToSpendBody(data: SurferSafeToSpendData, hero: Boolean) {
    val amountColor = if (data.status == SurferBudgetStatus.Over) {
        AppTheme.materialColors.error
    } else {
        AppTheme.materialColors.onSurface
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = if (hero) 10.dp else 6.dp),
        verticalArrangement = Arrangement.spacedBy(if (hero) 10.dp else 6.dp),
    ) {
        Text(
            text = data.amount,
            style = if (hero) AppTheme.typography.displaySmall else AppTheme.typography.headlineSmall,
            color = amountColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (hero) {
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
            alertFraction = data.paceFraction,
            height = if (hero) 10.dp else 8.dp,
            contentDescription = data.progressContentDescription,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = data.perDay,
                style = if (hero) AppTheme.typography.labelLarge else AppTheme.typography.labelSmall,
                color = AppTheme.materialColors.onSurface,
            )
            Text(
                text = data.daysLeft,
                style = if (hero) AppTheme.typography.labelLarge else AppTheme.typography.labelSmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
        }
    }
}

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
                title = "Safe to spend",
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
                title = "Safe to spend",
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
                title = "Safe to spend",
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
                title = "Safe to spend",
                data = null,
                emptyTitle = "No budget yet",
                emptySubtitle = "Set a cap to see what is safe to spend.",
                emptyActionLabel = "Set a budget",
                onEmptyActionClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
        }
    }
}
