package com.georgeci.moneysurfer.feature.transaction.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_details_account_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_category_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_category_nested
import moneysurfer.feature.transaction.generated.resources.transaction_details_from_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_merchant_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_reference_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_tags_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_to_account_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_to_label
import org.jetbrains.compose.resources.stringResource

private const val TAG_SEPARATOR = " · "

private data class DetailRowSpec(val icon: ImageVector, val label: String, val value: String)

/**
 * Account, Merchant, Category, Tags, Reference — the rows the design asks for. Date is not among
 * them: the hero already carries it, and the currency is legible in every formatted amount on the
 * screen, so neither earns a row of its own.
 *
 * A transfer swaps the account and category rows for `From` / `To`, since "which account" has two
 * answers and the category is always the seeded system one.
 */
@Composable
internal fun DetailsCard(state: TransactionDetailsState.Content) {
    val rows = detailRows(state)
    if (rows.isEmpty()) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = AppTheme.materialColors.surfaceContainerHigh,
            contentColor = AppTheme.materialColors.onSurface,
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            rows.forEachIndexed { index, spec ->
                DetailRow(spec)
                if (index < rows.lastIndex) {
                    HorizontalDivider(
                        color = AppTheme.materialColors.outlineVariant,
                        modifier = Modifier.padding(start = 62.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun detailRows(state: TransactionDetailsState.Content): List<DetailRowSpec> = buildList {
    val transfer = state.transfer
    if (transfer != null) {
        if (transfer.fromAccountName.isNotBlank()) {
            add(
                DetailRowSpec(
                    SurferIcons.ArrowUp,
                    stringResource(Res.string.transaction_details_from_label),
                    transfer.fromAccountName,
                ),
            )
        }
        if (transfer.toAccountName.isNotBlank()) {
            add(
                DetailRowSpec(
                    SurferIcons.ArrowDown,
                    stringResource(Res.string.transaction_details_to_label),
                    transfer.toAccountName,
                ),
            )
        }
    } else {
        addSingleLegRows(state)
    }
    if (state.tags.isNotEmpty()) {
        add(
            DetailRowSpec(
                SurferIcons.Tag,
                stringResource(Res.string.transaction_details_tags_label),
                state.tags.joinToString(TAG_SEPARATOR),
            ),
        )
    }
    if (state.reference.isNotBlank()) {
        add(
            DetailRowSpec(
                SurferIcons.Code,
                stringResource(Res.string.transaction_details_reference_label),
                state.reference,
            ),
        )
    }
}

/** Account, merchant and category — the rows an ordinary income or expense answers. */
@Composable
private fun MutableList<DetailRowSpec>.addSingleLegRows(state: TransactionDetailsState.Content) {
    if (state.accountName.isNotBlank()) {
        add(
            DetailRowSpec(
                SurferIcons.CreditCard,
                // Income lands *in* the account; naming it "To account" keeps the row
                // readable next to the "From" sender below it.
                if (state.isIncome) {
                    stringResource(Res.string.transaction_details_to_account_label)
                } else {
                    stringResource(Res.string.transaction_details_account_label)
                },
                state.accountName,
            ),
        )
    }
    if (state.merchant.isNotBlank()) {
        add(
            DetailRowSpec(
                SurferIcons.Receipt,
                if (state.isIncome) {
                    stringResource(Res.string.transaction_details_from_label)
                } else {
                    stringResource(Res.string.transaction_details_merchant_label)
                },
                state.merchant,
            ),
        )
    }
    if (state.categoryName.isNotBlank()) {
        add(
            DetailRowSpec(
                SurferIcons.Category,
                stringResource(Res.string.transaction_details_category_label),
                categoryValue(state),
            ),
        )
    }
}

/** `Coffee · in Dining` for a nested category, plain `Dining` for a top-level one. */
@Composable
private fun categoryValue(state: TransactionDetailsState.Content): String {
    val parent = state.parentCategoryName
    return if (parent.isNullOrBlank()) {
        state.categoryName
    } else {
        stringResource(Res.string.transaction_details_category_nested, state.categoryName, parent)
    }
}

@Composable
private fun DetailRow(spec: DetailRowSpec) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppTheme.materialColors.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = SurferSemantics.Decorative,
                tint = AppTheme.materialColors.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = spec.label,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
            Text(
                text = spec.value,
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.materialColors.onSurface,
            )
        }
    }
}
