package com.georgeci.moneysurfer.feature.transaction.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.uikit.components.SurferCategoryBubble
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.SurferCategoryVisual
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmount
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmountTier
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_details_status_planned
import moneysurfer.feature.transaction.generated.resources.transaction_details_status_posted
import moneysurfer.feature.transaction.generated.resources.transaction_details_type_expense
import moneysurfer.feature.transaction.generated.resources.transaction_details_type_income
import moneysurfer.feature.transaction.generated.resources.transaction_details_type_transfer
import org.jetbrains.compose.resources.stringResource

/** The hero band's gradient wash, over the category tint. */
private const val HERO_GRADIENT_ALPHA = 0.22f

/**
 * Hero bubble and hue per variant: a transfer is neutral with a swap glyph (money moved sideways,
 * its category is the seeded system one), income is green whatever the category says, and an
 * expense keeps its category's own colour.
 *
 * The category's stored appearance is read rather than its *name* hashed — two categories renamed
 * to the same word used to share a colour, and a rename silently repainted the screen.
 */
@Composable
internal fun heroVisualFor(state: TransactionDetailsState.Content): SurferCategoryVisual {
    val categoryVisual = SurferCategoryPalette.visualFor(
        id = state.categoryId,
        iconKey = state.categoryIconKey,
        hue = state.categoryHue,
        systemKind = state.categorySystemKind,
    )
    return when {
        state.isTransfer -> SurferCategoryVisual(
            icon = SurferCategoryPalette.TransferIcon,
            tint = SurferCategoryPalette.TransferTint,
        )
        state.isIncome -> categoryVisual.copy(tint = AppTheme.semanticColors.income)
        else -> categoryVisual
    }
}

/** `TRANSFER · POSTED`, or `GROCERIES · EXPENSE · POSTED` once there is a category to name. */
@Composable
internal fun heroHeaderFor(state: TransactionDetailsState.Content): String {
    val typeLabel = stringResource(
        when {
            state.isTransfer -> Res.string.transaction_details_type_transfer
            state.type == TransactionType.INCOME -> Res.string.transaction_details_type_income
            else -> Res.string.transaction_details_type_expense
        },
    ).uppercase()
    val statusLabel = stringResource(
        if (state.isPlanned) {
            Res.string.transaction_details_status_planned
        } else {
            Res.string.transaction_details_status_posted
        },
    ).uppercase()
    // A transfer's category is always the seeded "Transfer" one — naming it would just repeat
    // the type label.
    return if (state.isTransfer || state.categoryName.isBlank()) {
        "$typeLabel · $statusLabel"
    } else {
        "${state.categoryName.uppercase()} · $typeLabel · $statusLabel"
    }
}

@Composable
internal fun HeroCard(
    header: String,
    headerColor: Color,
    /** Bubble glyph and hue in one, as [heroVisualFor] resolved them. */
    visual: SurferCategoryVisual,
    formattedAmount: String,
    note: String,
    formattedDate: String,
    isPlanned: Boolean,
) {
    val outlineVariant = AppTheme.materialColors.outlineVariant
    val surface = AppTheme.materialColors.surface
    val heroBrush = Brush.linearGradient(
        colors = listOf(
            visual.tint.copy(alpha = HERO_GRADIENT_ALPHA),
            surface,
        ),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(heroBrush)
            .border(1.dp, outlineVariant, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SurferCategoryBubble(icon = visual.icon, tint = visual.tint, size = 64.dp)
        Spacer(Modifier.height(12.dp))
        Text(
            text = header,
            style = AppTheme.typography.labelLarge,
            color = headerColor,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        SurferSplitAmount(
            formattedAmount = formattedAmount,
            tier = SurferSplitAmountTier.Hero,
            color = AppTheme.materialColors.onSurface,
            signAlpha = 0.7f,
            fractionAlpha = 0.55f,
        )
        if (note.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = note,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.materialColors.onSurface,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = formattedDate,
            style = AppTheme.typography.bodySmall,
            color = AppTheme.materialColors.onSurfaceVariant,
        )
        if (isPlanned) {
            Spacer(Modifier.height(10.dp))
            PlannedPill()
        }
    }
}

@Composable
private fun PlannedPill() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(AppTheme.materialColors.tertiaryContainer)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = SurferIcons.Clock,
            contentDescription = SurferSemantics.Decorative,
            tint = AppTheme.materialColors.onTertiaryContainer,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = stringResource(Res.string.transaction_details_status_planned),
            style = AppTheme.typography.labelSmall,
            color = AppTheme.materialColors.onTertiaryContainer,
        )
    }
}
