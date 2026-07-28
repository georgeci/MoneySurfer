package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

enum class SurferInsightTone { Good, Warn, Neutral }

data class SurferInsightItem(
    val id: String,
    val tone: SurferInsightTone,
    val icon: ImageVector,
    val title: String,
    val body: String,
)

/**
 * How the cards are arranged inside the widget.
 *
 * The entry names double as the persisted keys of a dashboard card style, which is why [fromKey]
 * is lenient: a layout written by a newer build may name a layout this one has never heard of, and
 * falling back to [List] beats refusing to draw the insights.
 */
enum class SurferInsightsVariant {

    /** Stacked cards, as many as the card size has room for — the dashboard default. */
    List,

    /** One card per page, swiped sideways, with a dot per insight underneath. */
    Carousel,

    ;

    companion object {
        fun fromKey(key: String?): SurferInsightsVariant = entries.firstOrNull { it.name == key } ?: List
    }
}

/**
 * The generated-insight card.
 *
 * [SurferInsightsVariant.List] keeps as many cards as the size allows — three expanded, one
 * compact — because a column of insights is read at a glance. [SurferInsightsVariant.Carousel]
 * keeps all of them instead and trades height for swipes, which is what makes it worth offering:
 * a compact carousel still reaches every insight.
 */
@Composable
fun SurferInsightsWidget(
    items: List<SurferInsightItem>,
    title: String,
    modifier: Modifier = Modifier,
    size: SurferWidgetSize = LocalSurferWidgetSize.current,
    variant: SurferInsightsVariant = SurferInsightsVariant.List,
    onItemClick: ((SurferInsightItem) -> Unit)? = null,
    badgeFormat: ((Int) -> String)? = null,
    emptyText: String? = null,
) {
    SurferWidgetCard(
        title = title,
        modifier = modifier,
        trailing = {
            if (items.isNotEmpty() && badgeFormat != null) {
                Text(
                    text = badgeFormat(items.size),
                    style = AppTheme.typography.labelMedium,
                    color = AppTheme.materialColors.primary,
                )
            }
        },
    ) {
        if (items.isEmpty()) {
            SurferWidgetEmptyState(
                icon = SurferIcons.Sparkle,
                title = null,
                subtitle = emptyText,
            )
            return@SurferWidgetCard
        }

        Spacer(Modifier.height(10.dp))
        when (variant) {
            SurferInsightsVariant.List -> InsightList(
                items = items.take(if (size == SurferWidgetSize.Expanded) EXPANDED_ROWS else COMPACT_ROWS),
                onItemClick = onItemClick,
            )
            SurferInsightsVariant.Carousel -> InsightCarousel(items = items, onItemClick = onItemClick)
        }
    }
}

@Composable
private fun InsightList(
    items: List<SurferInsightItem>,
    onItemClick: ((SurferInsightItem) -> Unit)?,
) {
    Column(
        modifier = Modifier.padding(horizontal = CARD_INSET),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            InsightCard(
                item = item,
                onClick = onItemClick?.let { handler -> { handler(item) } },
            )
        }
    }
}

/**
 * One card per page plus a dot row.
 *
 * Every page pins its body to [CAROUSEL_BODY_LINES] lines so the pager keeps one height across the
 * whole set: a lazy row of pages is measured from what is composed, so pages of different heights
 * make the card grow and shrink under the reader's thumb mid-swipe.
 */
@Composable
private fun InsightCarousel(
    items: List<SurferInsightItem>,
    onItemClick: ((SurferInsightItem) -> Unit)?,
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(horizontal = CARD_INSET),
        pageSpacing = 8.dp,
    ) { page ->
        val item = items[page]
        InsightCard(
            item = item,
            onClick = onItemClick?.let { handler -> { handler(item) } },
            bodyLines = CAROUSEL_BODY_LINES,
        )
    }
    if (items.size > 1) {
        Spacer(Modifier.height(10.dp))
        PageDots(count = items.size, selected = pagerState.currentPage)
    }
}

@Composable
private fun PageDots(count: Int, selected: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .size(if (active) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (active) {
                            AppTheme.materialColors.primary
                        } else {
                            AppTheme.materialColors.surfaceContainerHighest
                        },
                    ),
            )
        }
    }
}

@Composable
private fun InsightCard(
    item: SurferInsightItem,
    onClick: (() -> Unit)?,
    bodyLines: Int? = null,
) {
    val (bg, fg) = when (item.tone) {
        SurferInsightTone.Good ->
            AppTheme.materialColors.tertiaryContainer to
                AppTheme.materialColors.onTertiaryContainer
        SurferInsightTone.Warn ->
            AppTheme.materialColors.errorContainer to
                AppTheme.materialColors.onErrorContainer
        SurferInsightTone.Neutral ->
            AppTheme.materialColors.surfaceContainerHigh to
                AppTheme.materialColors.onSurface
    }
    val rowModifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = SurferSemantics.Decorative,
                tint = fg,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = AppTheme.typography.titleSmall,
                color = fg,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.body,
                style = AppTheme.typography.bodySmall,
                color = fg.copy(alpha = 0.85f),
                minLines = bodyLines ?: 1,
                maxLines = bodyLines ?: Int.MAX_VALUE,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Side inset of the cards inside the widget — a touch tighter than the header's 16dp. */
private val CARD_INSET = 14.dp

private const val EXPANDED_ROWS = 3
private const val COMPACT_ROWS = 1

/** Two lines fit the generated copy; see [InsightCarousel] for why it is pinned rather than capped. */
private const val CAROUSEL_BODY_LINES = 2

private val PREVIEW_ITEMS = listOf(
    SurferInsightItem(
        id = "1",
        tone = SurferInsightTone.Warn,
        icon = SurferIcons.ArrowUp,
        title = "Dining is up 28%",
        body = "€162 spent — €35 above your usual €127 / month.",
    ),
    SurferInsightItem(
        id = "2",
        tone = SurferInsightTone.Good,
        icon = SurferIcons.Sparkle,
        title = "On track to save €420",
        body = "You spent 21% less on Leisure than last month.",
    ),
    SurferInsightItem(
        id = "3",
        tone = SurferInsightTone.Neutral,
        icon = SurferIcons.Sync,
        title = "4 active subscriptions",
        body = "About €62 a month.",
    ),
)

@Preview
@Composable
private fun SurferInsightsWidgetHeroPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferInsightsWidget(
                items = PREVIEW_ITEMS,
                title = "Insights",
                badgeFormat = { "$it new" },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SurferInsightsWidgetCarouselPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferInsightsWidget(
                items = PREVIEW_ITEMS,
                title = "Insights",
                variant = SurferInsightsVariant.Carousel,
                size = SurferWidgetSize.Compact,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SurferInsightsWidgetEmptyPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferInsightsWidget(
                items = emptyList(),
                title = "Insights",
                emptyText = "Nothing notable this period.",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
            )
        }
    }
}
