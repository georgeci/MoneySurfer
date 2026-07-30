package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.base.SurferSparkline
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmount
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmountTier
import com.georgeci.moneysurfer.uikit.components.base.sparklinePoints
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.theme.SurferContainerStyle

/**
 * Bottom line of [SurferBalanceWidget]. The two variants are mutually exclusive by construction —
 * passing them as independent nullable strings let callers ask for combinations the widget silently
 * dropped.
 *
 * The period delta is *not* one of them: it is [SurferBalanceTrend], because a card can honestly
 * carry both "up €412 this month" and "converted at rates from yesterday" at once, and forcing them
 * into one slot meant a workspace holding two currencies never saw its trend.
 */
sealed interface SurferBalanceFootnote {

    /**
     * Secondary line carrying what the single headline figure cannot say — e.g. the balances
     * held in other currencies, which no rate-free sum may fold into it.
     */
    data class Note(val text: String) : SurferBalanceFootnote

    /**
     * Nothing to total yet. Also switches the headline to plain text and drops the trend: there is
     * no amount to split and no curve to draw.
     */
    data class Empty(val text: String) : SurferBalanceFootnote
}

/**
 * Where the balance has been and what moved it over the newest period.
 *
 * Both halves are optional and independent: a workspace one month old has a delta and no curve
 * worth drawing, and a curve is still worth drawing over a month that netted out to nothing. With
 * neither, pass no trend at all.
 */
data class SurferBalanceTrend(
    /** The delta as a sentence, already formatted and signed by the caller. */
    val text: String? = null,
    /**
     * The balance at the close of each charted period, oldest first. Fewer than two points draws
     * nothing — see [SurferSparkline].
     */
    val series: List<Float> = emptyList(),
    /**
     * Whether the balance fell. Picks the arrow only: the card sits on `primaryContainer`, where an
     * error red would fail contrast, so the direction is carried by the icon and by the sign the
     * caller already put in [text].
     */
    val isNegative: Boolean = false,
)

/**
 * Presentation treatment of the balance card — six of them, the A–F set of the dashboard mockups.
 * The container, its colours and the footnote are the same in all six, so the choice reads as a
 * style rather than as six different widgets; what changes is the arrangement, the type scale of
 * the title and the amount, and how much room the trend curve gets.
 *
 * The entry names double as the persisted keys of a dashboard card style, which is why
 * [fromKey] is lenient: a layout written by a newer build may name a treatment this one has never
 * heard of, and falling back to [Classic] beats refusing to draw the balance.
 */
enum class SurferBalanceVariant {

    /** Label above the amount — the dashboard default. */
    Classic,

    /** Amount first, label demoted to a caption under it. */
    Stacked,

    /** Label and amount on one line, the amount a tier smaller so both fit. */
    Inline,

    /** Amount alone, and no trend: the card's position on the dashboard is the label. */
    Minimal,

    /** Label and delta on one line facing each other, the amount below them. */
    Split,

    /** The curve is the hero: caption label, amount a tier down, and a chart twice as tall. */
    Chart,

    ;

    companion object {
        fun fromKey(key: String?): SurferBalanceVariant = entries.firstOrNull { it.name == key } ?: Classic
    }
}

@Composable
fun SurferBalanceWidget(
    title: String,
    balance: String,
    modifier: Modifier = Modifier,
    size: SurferWidgetSize = LocalSurferWidgetSize.current,
    variant: SurferBalanceVariant = SurferBalanceVariant.Classic,
    footnote: SurferBalanceFootnote? = null,
    trend: SurferBalanceTrend? = null,
) {
    val hero = size == SurferWidgetSize.Expanded
    val isEmpty = footnote is SurferBalanceFootnote.Empty
    val elevated = AppTheme.containerStyle == SurferContainerStyle.Card
    // An empty balance has no trend by definition, and Minimal is the treatment that asked for the
    // figure and nothing else. Resolved once so the arrangement below and the curve agree.
    val shownTrend = trend?.takeUnless { isEmpty || variant == SurferBalanceVariant.Minimal }

    Card(
        modifier = modifier,
        shape = AppTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = AppTheme.materialColors.primaryContainer,
            contentColor = AppTheme.materialColors.onPrimaryContainer,
        ),
        elevation = if (elevated) {
            CardDefaults.elevatedCardElevation()
        } else {
            CardDefaults.cardElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp,
                draggedElevation = 0.dp,
                disabledElevation = 0.dp,
            )
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (hero) 20.dp else 16.dp, vertical = if (hero) 18.dp else 14.dp),
                verticalArrangement = Arrangement.spacedBy(if (hero) 8.dp else 4.dp),
            ) {
                val tier = variant.amountTier(hero)
                when (variant) {
                    SurferBalanceVariant.Classic -> {
                        Title(text = title, hero = hero)
                        Amount(balance = balance, tier = tier, isEmpty = isEmpty)
                    }
                    SurferBalanceVariant.Stacked -> {
                        Amount(balance = balance, tier = tier, isEmpty = isEmpty)
                        Title(text = title, hero = hero, caption = true)
                    }
                    SurferBalanceVariant.Inline -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Title(text = title, hero = hero, modifier = Modifier.weight(1f))
                        Amount(balance = balance, tier = tier, isEmpty = isEmpty)
                    }
                    SurferBalanceVariant.Minimal -> Amount(balance = balance, tier = tier, isEmpty = isEmpty)
                    SurferBalanceVariant.Split -> {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Title(text = title, hero = hero, modifier = Modifier.weight(1f))
                            TrendLabel(trend = shownTrend, hero = hero)
                        }
                        Amount(balance = balance, tier = tier, isEmpty = isEmpty)
                    }
                    SurferBalanceVariant.Chart -> {
                        Title(text = title, hero = hero, caption = true)
                        Amount(balance = balance, tier = tier, isEmpty = isEmpty)
                    }
                }
                // Split has already drawn the delta on the title's line; drawing it again below
                // would state the same figure twice.
                if (variant != SurferBalanceVariant.Split) {
                    TrendRow(trend = shownTrend, hero = hero)
                }
                Footnote(footnote = footnote)
            }
            // Outside the padded column so the curve bleeds to the card's edges, the way the
            // mockups draw it — a sparkline inset from the sides reads as a chart that was cropped.
            Sparkline(trend = shownTrend, variant = variant, hero = hero)
        }
    }
}

/**
 * How big the amount is set. [SurferBalanceVariant.Inline] shares its line with the title and
 * [SurferBalanceVariant.Chart] shares its card with a tall curve, so neither can keep the hero
 * tier; the rest have the full width of the card to themselves.
 */
private fun SurferBalanceVariant.amountTier(hero: Boolean): SurferSplitAmountTier = when (this) {
    SurferBalanceVariant.Inline,
    SurferBalanceVariant.Chart,
    -> if (hero) SurferSplitAmountTier.Stat else SurferSplitAmountTier.Body
    else -> if (hero) SurferSplitAmountTier.Hero else SurferSplitAmountTier.Stat
}

/**
 * Height the curve gets, or null for the treatments that draw none.
 * [SurferBalanceVariant.Chart] is the one that leads with it; elsewhere it is a band under the
 * figures.
 */
private fun SurferBalanceVariant.sparklineHeight(hero: Boolean): Dp? = when (this) {
    SurferBalanceVariant.Minimal -> null
    SurferBalanceVariant.Chart -> if (hero) 96.dp else 64.dp
    else -> if (hero) 56.dp else 40.dp
}

@Composable
private fun Title(
    text: String,
    hero: Boolean,
    modifier: Modifier = Modifier,
    caption: Boolean = false,
) {
    Text(
        text = text,
        style = when {
            caption -> if (hero) AppTheme.typography.labelMedium else AppTheme.typography.labelSmall
            hero -> AppTheme.typography.labelLarge
            else -> AppTheme.typography.labelMedium
        },
        color = AppTheme.materialColors.onPrimaryContainer.copy(alpha = 0.8f),
        // Inline puts the title on the amount's line, where the amount is measured first: a long
        // balance leaves the title a narrow column, and it must shorten rather than stack up.
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The headline figure. An empty balance is a dash, not an amount, so it is plain text. */
@Composable
private fun Amount(
    balance: String,
    tier: SurferSplitAmountTier,
    isEmpty: Boolean,
) {
    if (isEmpty) {
        Text(
            text = balance,
            style = when (tier) {
                SurferSplitAmountTier.Hero -> AppTheme.typography.displaySmall
                SurferSplitAmountTier.Stat -> AppTheme.typography.headlineSmall
                SurferSplitAmountTier.Body -> AppTheme.typography.titleLarge
            },
            color = AppTheme.materialColors.onPrimaryContainer,
        )
    } else {
        SurferSplitAmount(
            formattedAmount = balance,
            tier = tier,
            color = AppTheme.materialColors.onPrimaryContainer,
            signAlpha = 0.7f,
            fractionAlpha = 0.55f,
        )
    }
}

/** The delta with its direction arrow, on a line of its own. */
@Composable
private fun TrendRow(trend: SurferBalanceTrend?, hero: Boolean) {
    val text = trend?.text ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = trend.icon(),
            contentDescription = SurferSemantics.Decorative,
            tint = AppTheme.materialColors.onPrimaryContainer,
            modifier = Modifier.size(if (hero) 16.dp else 14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = if (hero) AppTheme.typography.bodyMedium else AppTheme.typography.bodySmall,
            color = AppTheme.materialColors.onPrimaryContainer,
        )
    }
}

/**
 * The delta alone, for [SurferBalanceVariant.Split], where it faces the title across the card and
 * the arrow would crowd the gap between them.
 *
 * The unweighted child of that row, so it is measured first against the card's whole width — a long
 * enough delta squeezes the title rather than running off the edge, and shortens rather than clips
 * once even that is not enough.
 */
@Composable
private fun TrendLabel(trend: SurferBalanceTrend?, hero: Boolean) {
    val text = trend?.text ?: return
    Text(
        text = text,
        style = if (hero) AppTheme.typography.labelLarge else AppTheme.typography.labelMedium,
        color = AppTheme.materialColors.onPrimaryContainer,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun SurferBalanceTrend.icon() =
    if (isNegative) Icons.AutoMirrored.Filled.TrendingDown else Icons.AutoMirrored.Filled.TrendingUp

@Composable
private fun Sparkline(trend: SurferBalanceTrend?, variant: SurferBalanceVariant, hero: Boolean) {
    val series = trend?.series ?: return
    if (series.size < 2) return
    val height = variant.sparklineHeight(hero) ?: return
    SurferSparkline(
        points = sparklinePoints(series),
        color = AppTheme.materialColors.onPrimaryContainer,
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
    )
}

/**
 * Takes no size: the two remaining footnotes are qualifying prose either way, and only the delta —
 * now [TrendRow]'s job — ever scaled with the card.
 */
@Composable
private fun Footnote(footnote: SurferBalanceFootnote?) {
    when (footnote) {
        null -> Unit
        is SurferBalanceFootnote.Note -> Text(
            text = footnote.text,
            style = AppTheme.typography.bodySmall,
            color = AppTheme.materialColors.onPrimaryContainer.copy(alpha = 0.85f),
        )
        is SurferBalanceFootnote.Empty -> Text(
            text = footnote.text,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.materialColors.onPrimaryContainer.copy(alpha = 0.85f),
        )
    }
}

/** Shared preview copy, so the galleries below differ only in the treatment they show. */
private const val PREVIEW_TITLE = "Total balance"

private const val PREVIEW_BALANCE = "€11,575.32"

private val PREVIEW_TREND = SurferBalanceTrend(
    text = "+€412 this month",
    series = listOf(9_800f, 10_240f, 9_950f, 10_610f, 11_160f, 11_575f),
)

@Preview
@Composable
private fun SurferBalanceWidgetHeroPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferBalanceWidget(
                title = PREVIEW_TITLE,
                balance = PREVIEW_BALANCE,
                trend = PREVIEW_TREND,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SurferBalanceWidgetCompactPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferBalanceWidget(
                title = PREVIEW_TITLE,
                balance = PREVIEW_BALANCE,
                size = SurferWidgetSize.Compact,
                trend = PREVIEW_TREND.copy(text = "+€412"),
                modifier = Modifier.width(220.dp),
            )
        }
    }
}

@Preview
@Composable
private fun SurferBalanceWidgetVariantsPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferBalanceVariant.entries.forEach { variant ->
                SurferBalanceWidget(
                    title = PREVIEW_TITLE,
                    balance = PREVIEW_BALANCE,
                    variant = variant,
                    trend = PREVIEW_TREND,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Preview
@Composable
private fun SurferBalanceWidgetFallingPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferBalanceWidget(
                title = PREVIEW_TITLE,
                balance = "€9,140.08",
                trend = SurferBalanceTrend(
                    text = "−€612 this month",
                    series = listOf(11_575f, 11_160f, 10_610f, 9_950f, 10_240f, 9_140f),
                    isNegative = true,
                ),
                footnote = SurferBalanceFootnote.Note("Plus £820.00 no rate could convert"),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview
@Composable
private fun SurferBalanceWidgetEmptyPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferBalanceWidget(
                title = PREVIEW_TITLE,
                balance = "—",
                footnote = SurferBalanceFootnote.Empty("Add your first account to see balance."),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
