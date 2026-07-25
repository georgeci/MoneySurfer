package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmount
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmountTier
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.theme.SurferContainerStyle

/**
 * Bottom line of [SurferBalanceWidget]. The three variants are mutually exclusive by
 * construction — passing them as three independent nullable strings let callers ask for
 * combinations the widget silently dropped.
 */
sealed interface SurferBalanceFootnote {

    /** Period delta, rendered with a trending-up icon. */
    data class Trend(val text: String) : SurferBalanceFootnote

    /**
     * Secondary line carrying what the single headline figure cannot say — e.g. the balances
     * held in other currencies, which no rate-free sum may fold into it.
     */
    data class Note(val text: String) : SurferBalanceFootnote

    /**
     * Nothing to total yet. Also switches the headline to plain text and drops the sparkline:
     * there is no amount to split or trend to draw.
     */
    data class Empty(val text: String) : SurferBalanceFootnote
}

/**
 * Typographic treatment of the balance card. Only the arrangement and the type scale of the title
 * and the amount change — the container, its colours and the footnote are the same in all four, so
 * the choice reads as a style rather than as four different widgets.
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

    /** Amount alone; the card's position on the dashboard is the label. */
    Minimal,

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
) {
    val hero = size == SurferWidgetSize.Hero
    val isEmpty = footnote is SurferBalanceFootnote.Empty
    val elevated = AppTheme.containerStyle == SurferContainerStyle.Card

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
            }
            Footnote(hero = hero, footnote = footnote)
        }
    }
}

/**
 * How big the amount is set. Every treatment but [SurferBalanceVariant.Inline] gives the amount
 * the full width of the card, so it keeps the hero tier; a line it shares with the title cannot.
 */
private fun SurferBalanceVariant.amountTier(hero: Boolean): SurferSplitAmountTier = when (this) {
    SurferBalanceVariant.Inline -> if (hero) SurferSplitAmountTier.Stat else SurferSplitAmountTier.Body
    else -> if (hero) SurferSplitAmountTier.Hero else SurferSplitAmountTier.Stat
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

@Composable
private fun Footnote(hero: Boolean, footnote: SurferBalanceFootnote?) {
    when (footnote) {
        null -> Unit
        is SurferBalanceFootnote.Trend -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                contentDescription = SurferSemantics.Decorative,
                tint = AppTheme.materialColors.onPrimaryContainer,
                modifier = Modifier.size(if (hero) 16.dp else 14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = footnote.text,
                style = if (hero) AppTheme.typography.bodyMedium else AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onPrimaryContainer,
            )
        }
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

/** Shared preview copy, so the four galleries below differ only in the treatment they show. */
private const val PREVIEW_TITLE = "Total balance"

private const val PREVIEW_BALANCE = "€11,575.32"

private const val PREVIEW_TREND = "+€412 this month"

@Preview
@Composable
private fun SurferBalanceWidgetHeroPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferBalanceWidget(
                title = PREVIEW_TITLE,
                balance = PREVIEW_BALANCE,
                footnote = SurferBalanceFootnote.Trend(PREVIEW_TREND),
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
                footnote = SurferBalanceFootnote.Trend("+€412"),
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
                    footnote = SurferBalanceFootnote.Trend(PREVIEW_TREND),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
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
