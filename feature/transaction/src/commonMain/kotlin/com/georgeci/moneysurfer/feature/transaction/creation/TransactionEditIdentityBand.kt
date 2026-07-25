package com.georgeci.moneysurfer.feature.transaction.creation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.SurferCategoryBubble
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_creation_expense
import moneysurfer.feature.transaction.generated.resources.transaction_creation_income
import moneysurfer.feature.transaction.generated.resources.transaction_creation_transfer
import org.jetbrains.compose.resources.stringResource

/** Opacity of the identity band's tint wash — lighter than a pill, it backs a whole block. */
private const val IDENTITY_BAND_WASH_ALPHA = 0.12f

/** Opacity of the band's hairline border, strong enough to read against the wash behind it. */
private const val IDENTITY_BAND_BORDER_ALPHA = 0.18f

private const val IDENTITY_HEADER_SEPARATOR = " · "

/**
 * The design's `04b · Edit transaction` band: the category bubble, `EXPENSE · TX-8213`, the note
 * and the amount, tinted with the transaction's own colour.
 *
 * It is what distinguishes editing from creating beyond a word in the title, and it renders the
 * *stored* row (see [TransactionEditIdentity]) — so it keeps naming the transaction under edit
 * while the fields below it change.
 */
@Composable
internal fun EditIdentityBand(
    identity: TransactionEditIdentity,
    modifier: Modifier = Modifier,
) {
    val visual = SurferCategoryPalette.visualFor(
        id = identity.categoryId,
        iconKey = identity.categoryIconKey,
        hue = identity.categoryHue,
        systemKind = identity.categorySystemKind,
    )
    // A transfer's category is the seeded system one, so its own tint says nothing the type
    // colour does not already say better.
    val tint = if (identity.type == TransactionTypeUi.Transfer) typeTint(identity.type) else visual.tint
    val wash = tint.copy(alpha = IDENTITY_BAND_WASH_ALPHA)
        .compositeOver(AppTheme.materialColors.surface)
    val typeLabel = stringResource(
        when (identity.type) {
            TransactionTypeUi.Expense -> Res.string.transaction_creation_expense
            TransactionTypeUi.Income -> Res.string.transaction_creation_income
            TransactionTypeUi.Transfer -> Res.string.transaction_creation_transfer
        },
    ).uppercase()
    Row(
        modifier = modifier
            .clip(AppTheme.shapes.large)
            .background(wash)
            .border(1.dp, tint.copy(alpha = IDENTITY_BAND_BORDER_ALPHA), AppTheme.shapes.large)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SurferCategoryBubble(icon = visual.icon, tint = tint, size = 44.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = typeLabel + IDENTITY_HEADER_SEPARATOR + identity.reference,
                style = AppTheme.typography.labelMedium,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (identity.note.isNotBlank()) {
                Text(
                    text = identity.note,
                    style = AppTheme.typography.bodyLarge,
                    color = AppTheme.materialColors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = identity.formattedAmount,
            style = AppTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = AppTheme.materialColors.onSurface,
            maxLines = 1,
        )
    }
}

/** Semantic colour of a transaction type, shared by the band and the amount hero's type pill. */
@Composable
internal fun typeTint(type: TransactionTypeUi): Color = when (type) {
    TransactionTypeUi.Expense -> AppTheme.semanticColors.expense
    TransactionTypeUi.Income -> AppTheme.semanticColors.income
    TransactionTypeUi.Transfer -> AppTheme.semanticColors.transfer
}

@Preview
@Composable
private fun EditIdentityBandPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EditIdentityBand(
                identity = TransactionEditIdentity(
                    reference = "TX-8213",
                    type = TransactionTypeUi.Expense,
                    note = PreviewNote,
                    formattedAmount = "−€48.20",
                    categoryId = "preview-cat-1",
                    categoryIconKey = "",
                    categoryHue = -1,
                    categorySystemKind = null,
                ),
            )
            EditIdentityBand(
                identity = TransactionEditIdentity(
                    reference = "TX-3C35",
                    type = TransactionTypeUi.Transfer,
                    note = "Rainy day top-up",
                    formattedAmount = "€200.00",
                    categoryId = "preview-cat-transfer",
                    categoryIconKey = SurferCategoryPalette.TRANSFER_ICON_KEY,
                    categoryHue = -1,
                    categorySystemKind = SurferCategoryPalette.SYSTEM_KIND_TRANSFER,
                ),
            )
        }
    }
}
