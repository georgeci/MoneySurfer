package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetGauge
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetRing
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetStatus
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetStatusPill
import com.georgeci.moneysurfer.uikit.components.budget.color
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * How the spent-by-category card presents the same rows. The choice is a card style, not five
 * widgets: every variant draws the list below, and none of them adds or drops a category.
 *
 * They split into two families, which is what decides what a shape *measures*:
 *
 * - [Bar] and [Gauge] give each category its own meter, so a capped category's meter fills against
 *   its cap and an uncapped one against its share of the period's spend. Which of the two it is
 *   never has to be guessed — [SurferCategorySpendItem.caption] is drawn beside every such meter
 *   and says so in words.
 * - [Ring], [Multi] and [Chips] draw shapes that stand for the whole period, so their geometry is
 *   always the share: segments that measured different things would not add up to the card. A cap
 *   reaches those variants as the status colour, always alongside
 *   [SurferCategorySpendCap.statusLabel] — a colour on its own is not a state every reader can get
 *   at.
 *
 * The entry names double as the persisted keys of a dashboard card style, which is why [fromKey]
 * is lenient: a layout written by a newer build may name a treatment this one has never heard of,
 * and falling back to [Bar] beats refusing to draw the card.
 */
enum class SurferSpentByCategoryVariant {

    /** One row per category: name, amount, a meter, and the line that names what it measures. */
    Bar,

    /** The top category as a donut, the runners-up as a legend beside it. */
    Ring,

    /** The top category alone on a half-turn dial — the single-category reading of the card. */
    Gauge,

    /** Every category as a pill. The densest variant, and the only one that draws no meter. */
    Chips,

    /** One stacked bar carrying every category's share at once, with a legend under it. */
    Multi,

    ;

    companion object {
        fun fromKey(key: String?): SurferSpentByCategoryVariant = entries.firstOrNull { it.name == key } ?: Bar
    }
}

/**
 * The cap on one category, as the card draws it. Absent from a [SurferCategorySpendItem] whose
 * category nothing caps.
 *
 * [statusLabel] is null while the cap is comfortably clear: a pill on every row would make the two
 * that matter invisible. It exists at all because colour alone is not a state a screen reader or a
 * colour-blind reader can get at.
 */
data class SurferCategorySpendCap(
    /** Spend against the cap; can exceed 1, which every meter caps rather than overdrawing. */
    val progress: Float,
    val status: SurferBudgetStatus,
    val statusLabel: String? = null,
)

/**
 * One category on the card, already formatted and localized by the caller — uikit neither knows the
 * currency nor owns the copy.
 */
data class SurferCategorySpendItem(
    val id: String,
    val name: String,
    val amount: String,
    /** Share of the period's total spend, `0f..1f`. */
    val share: Float,
    /**
     * What the row's meter measures, in words — "€142 of €150", or "31% of spending". Drawn by
     * every variant whose meter switches on [cap], so the two readings are never told apart by the
     * shape alone.
     */
    val caption: String,
    /** The category's own colour, resolved from its stored hue by the host screen. */
    val tint: Color,
    val cap: SurferCategorySpendCap? = null,
) {

    /** What a per-category meter fills to: the cap when there is one, the share otherwise. */
    val meterFraction: Float get() = cap?.progress ?: share
}

/** What the card says when the period holds no spend to break down. */
data class SurferSpentByCategoryEmpty(
    val title: String? = null,
    val subtitle: String? = null,
)

/**
 * Where the period's money went, category by category.
 *
 * An empty [items] draws [empty] rather than an empty card: a widget the user switched on should
 * still say why it has nothing to show.
 */
@Composable
fun SurferSpentByCategoryWidget(
    title: String,
    items: List<SurferCategorySpendItem>,
    modifier: Modifier = Modifier,
    size: SurferWidgetSize = LocalSurferWidgetSize.current,
    variant: SurferSpentByCategoryVariant = SurferSpentByCategoryVariant.Bar,
    empty: SurferSpentByCategoryEmpty = SurferSpentByCategoryEmpty(),
) {
    SurferWidgetCard(title = title, modifier = modifier) {
        if (items.isEmpty()) {
            SurferWidgetEmptyState(
                icon = SurferIcons.Category,
                title = empty.title,
                subtitle = empty.subtitle,
            )
            return@SurferWidgetCard
        }
        val hero = size == SurferWidgetSize.Expanded
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = if (hero) 10.dp else 6.dp),
            verticalArrangement = Arrangement.spacedBy(if (hero) 12.dp else 8.dp),
        ) {
            when (variant) {
                SurferSpentByCategoryVariant.Bar -> BarBody(items.visible(hero), hero)
                SurferSpentByCategoryVariant.Ring -> RingBody(items, hero)
                SurferSpentByCategoryVariant.Gauge -> GaugeBody(items.first(), hero)
                SurferSpentByCategoryVariant.Chips -> ChipsBody(items.visible(hero))
                SurferSpentByCategoryVariant.Multi -> MultiBody(items, hero)
            }
        }
    }
}

/**
 * The rows a size actually shows. Everything past them still counts towards the shares and the
 * stacked bar — the card trims what it lists, never what it measures.
 */
private fun List<SurferCategorySpendItem>.visible(hero: Boolean): List<SurferCategorySpendItem> =
    take(if (hero) HERO_ROWS else COMPACT_ROWS)

@Composable
private fun BarBody(items: List<SurferCategorySpendItem>, hero: Boolean) {
    items.forEach { item ->
        Column(verticalArrangement = Arrangement.spacedBy(if (hero) 6.dp else 4.dp)) {
            HeadlineRow(item = item, hero = hero)
            CategoryMeter(
                fraction = item.meterFraction,
                color = item.meterColor(),
                height = if (hero) 8.dp else 6.dp,
            )
            // Kept at both sizes, unlike the safe-to-spend card's caption: this one is not a
            // restatement of the meter but the only thing that says what the meter measured, and a
            // Compact card is already the shorter card through its type scale and its row count.
            CaptionRow(item)
        }
    }
}

@Composable
private fun RingBody(items: List<SurferCategorySpendItem>, hero: Boolean) {
    val top = items.first()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The ring stands for the whole period, so it fills to the top category's share of it.
        SurferBudgetRing(
            progress = top.share,
            status = top.cap?.status ?: SurferBudgetStatus.Ok,
            size = if (hero) 116.dp else 84.dp,
            strokeWidth = if (hero) 12.dp else 9.dp,
        ) {
            // The amount alone: the legend beside it names this category on its first row, and the
            // hole is too narrow to repeat a name without truncating it.
            Text(
                text = top.amount,
                style = if (hero) AppTheme.typography.titleMedium else AppTheme.typography.titleSmall,
                color = AppTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(if (hero) 8.dp else 6.dp),
        ) {
            // The top category leads the legend as well as filling the ring: the ring's hole has
            // no room for its status word, and the ring is the one shape here that carries a
            // status colour with nothing to read it by.
            items.take(if (hero) RING_LEGEND_ROWS else RING_LEGEND_ROWS_COMPACT)
                .forEach { LegendRow(item = it, hero = hero) }
        }
    }
}

@Composable
private fun GaugeBody(item: SurferCategorySpendItem, hero: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (hero) 6.dp else 4.dp),
    ) {
        SurferBudgetGauge(
            progress = item.meterFraction,
            status = item.cap?.status ?: SurferBudgetStatus.Ok,
            width = if (hero) 180.dp else 128.dp,
            strokeWidth = if (hero) 12.dp else 9.dp,
        ) {
            Text(
                text = item.amount,
                style = if (hero) AppTheme.typography.headlineSmall else AppTheme.typography.titleMedium,
                color = AppTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = item.name,
            style = AppTheme.typography.titleSmall,
            color = AppTheme.materialColors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.caption,
            style = AppTheme.typography.bodySmall,
            color = AppTheme.materialColors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipsBody(items: List<SurferCategorySpendItem>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            val tint = item.meterColor()
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.name,
                    style = AppTheme.typography.labelMedium,
                    color = AppTheme.materialColors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.amount,
                    style = AppTheme.typography.labelMedium,
                    color = tint,
                    maxLines = 1,
                )
                // The chip has no meter and no caption, so a cap would otherwise reach it as a
                // colour and nothing else.
                val statusLabel = item.cap?.statusLabel
                if (statusLabel != null) {
                    Text(
                        text = statusLabel,
                        style = AppTheme.typography.labelSmall,
                        color = tint,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiBody(items: List<SurferCategorySpendItem>, hero: Boolean) {
    // Every category is in the bar, including the ones the legend below has no room to name.
    StackedShareBar(items = items, height = if (hero) 12.dp else 9.dp)
    items.visible(hero).forEach { LegendRow(item = it, hero = hero) }
}

@Composable
private fun HeadlineRow(item: SurferCategorySpendItem, hero: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = item.name,
            style = if (hero) AppTheme.typography.bodyMedium else AppTheme.typography.bodySmall,
            color = AppTheme.materialColors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = item.amount,
            style = if (hero) AppTheme.typography.labelLarge else AppTheme.typography.labelMedium,
            color = AppTheme.materialColors.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun CaptionRow(item: SurferCategorySpendItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = item.caption,
            style = AppTheme.typography.bodySmall,
            color = AppTheme.materialColors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        StatusPill(item.cap)
    }
}

@Composable
private fun LegendRow(item: SurferCategorySpendItem, hero: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(item.tint),
        )
        Text(
            text = item.name,
            style = if (hero) AppTheme.typography.bodyMedium else AppTheme.typography.bodySmall,
            color = AppTheme.materialColors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        StatusPill(item.cap)
        Text(
            text = item.amount,
            style = if (hero) AppTheme.typography.labelMedium else AppTheme.typography.labelSmall,
            color = AppTheme.materialColors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/** The status word, drawn only for the states worth interrupting the row for. */
@Composable
private fun StatusPill(cap: SurferCategorySpendCap?) {
    val label = cap?.statusLabel ?: return
    SurferBudgetStatusPill(label = label, status = cap.status)
}

/**
 * A capped category is coloured by how it is doing against its cap; an uncapped one keeps its own
 * category colour, because there is no state to report and a uniform tint would say there is.
 */
@Composable
private fun SurferCategorySpendItem.meterColor(): Color = cap?.status?.color ?: tint

@Composable
private fun CategoryMeter(fraction: Float, color: Color, height: Dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(AppTheme.materialColors.surfaceContainerHighest),
    ) {
        // Capped at the full width — past 100 % the colour carries the overspend, not the geometry.
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(color),
        )
    }
}

@Composable
private fun StackedShareBar(items: List<SurferCategorySpendItem>, height: Dp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(AppTheme.materialColors.surfaceContainerHighest),
    ) {
        items.forEach { item ->
            Box(
                modifier = Modifier
                    // A weight has to be positive, and a category that rounds to nothing still
                    // deserves a hairline rather than vanishing out of the total it is part of.
                    .weight(item.share.coerceAtLeast(MIN_SEGMENT_SHARE))
                    .fillMaxHeight()
                    .background(item.tint),
            )
        }
    }
}

/** Rows a Hero card lists. Deeper than Compact because the extra height is what Hero is for. */
private const val HERO_ROWS = 5

private const val COMPACT_ROWS = 3

/** Fewer than the other variants list: this legend shares its row with the ring. */
private const val RING_LEGEND_ROWS = 4

private const val RING_LEGEND_ROWS_COMPACT = 3

private const val MIN_SEGMENT_SHARE = 0.004f

private const val PREVIEW_TITLE = "Spent by category"

private val previewItems: List<SurferCategorySpendItem>
    @Composable get() = listOf(
        SurferCategorySpendItem(
            id = "rent",
            name = "Rent",
            amount = "€760.00",
            share = 0.45f,
            caption = "45% of spending",
            tint = SurferCategoryPalette.tints[0],
        ),
        SurferCategorySpendItem(
            id = "groceries",
            name = "Groceries",
            amount = "€142.10",
            share = 0.22f,
            caption = "€142.10 of €150",
            tint = SurferCategoryPalette.tints[1],
            cap = SurferCategorySpendCap(
                progress = 0.95f,
                status = SurferBudgetStatus.Warn,
                statusLabel = "Near limit",
            ),
        ),
        SurferCategorySpendItem(
            id = "dining",
            name = "Eating out",
            amount = "€96.40",
            share = 0.12f,
            caption = "over €80",
            tint = SurferCategoryPalette.tints[2],
            cap = SurferCategorySpendCap(
                progress = 1.2f,
                status = SurferBudgetStatus.Over,
                statusLabel = "Over",
            ),
        ),
        SurferCategorySpendItem(
            id = "transport",
            name = "Transport",
            amount = "€54.00",
            share = 0.08f,
            caption = "8% of spending",
            tint = SurferCategoryPalette.tints[3],
        ),
        SurferCategorySpendItem(
            id = "leisure",
            name = "Leisure",
            amount = "€38.20",
            share = 0.06f,
            caption = "6% of spending",
            tint = SurferCategoryPalette.tints[4],
        ),
    )

@Preview
@Composable
private fun SurferSpentByCategoryVariantsPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferSpentByCategoryVariant.entries.forEach { variant ->
                SurferSpentByCategoryWidget(
                    title = PREVIEW_TITLE,
                    items = previewItems,
                    variant = variant,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Preview
@Composable
private fun SurferSpentByCategoryCompactPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferSpentByCategoryVariant.entries.forEach { variant ->
                SurferSpentByCategoryWidget(
                    title = PREVIEW_TITLE,
                    items = previewItems,
                    variant = variant,
                    size = SurferWidgetSize.Compact,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Preview
@Composable
private fun SurferSpentByCategoryEmptyPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferSpentByCategoryWidget(
                title = PREVIEW_TITLE,
                items = emptyList(),
                empty = SurferSpentByCategoryEmpty(
                    title = "Nothing spent yet",
                    subtitle = "Expenses you log this month will break down here.",
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
        }
    }
}
