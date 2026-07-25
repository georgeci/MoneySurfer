package com.georgeci.moneysurfer.feature.transaction.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.SurferEmptyState
import com.georgeci.moneysurfer.uikit.components.transaction.SurferTransactionLine
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transactions_list_empty
import moneysurfer.feature.transaction.generated.resources.transactions_list_empty_cta
import moneysurfer.feature.transaction.generated.resources.transactions_list_empty_filtered
import moneysurfer.feature.transaction.generated.resources.transactions_list_empty_filtered_cta
import moneysurfer.feature.transaction.generated.resources.transactions_list_empty_filtered_title
import moneysurfer.feature.transaction.generated.resources.transactions_list_empty_search
import moneysurfer.feature.transaction.generated.resources.transactions_list_empty_search_cta
import moneysurfer.feature.transaction.generated.resources.transactions_list_empty_title
import org.jetbrains.compose.resources.stringResource

/** Alpha of the transfer tile's tint wash — the same weight the category bubbles use. */
private const val TRANSFER_TILE_ALPHA = 0.18f

/** Alpha of the income amount pill behind the row's amount. */
private const val INCOME_PILL_ALPHA = 0.14f

private const val META_SEPARATOR = " · "

/**
 * One transaction line, with the three variants the list can render.
 *
 * A transfer leg is deliberately neither of the other two: it carries the swap glyph on a neutral
 * transfer tint and no amount pill, because the money did not leave or arrive — it moved sideways,
 * and drawing it as a plain expense is exactly what made the two legs unreadable.
 */
@Composable
internal fun TransactionRow(
    row: TransactionRowUi,
    showAccount: Boolean,
    untitled: String,
    onClick: () -> Unit,
) {
    val transferTint = AppTheme.semanticColors.transfer
    SurferTransactionLine(
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
        icon = when {
            row.isTransfer -> SurferCategoryPalette.TransferIcon
            row.isExpense -> SurferIcons.Receipt
            else -> SurferIcons.Wallet
        },
        title = row.title.ifBlank { untitled },
        formattedAmount = row.formattedAmount,
        categoryHueSeed = row.categoryHueSeed,
        meta = row.metaLine(showAccount),
        iconTint = if (row.isTransfer) transferTint else AppTheme.materialColors.onSurfaceVariant,
        iconBackground = if (row.isTransfer) {
            transferTint.copy(alpha = TRANSFER_TILE_ALPHA)
        } else {
            AppTheme.materialColors.surfaceContainerHighest
        },
        amountColor = when {
            row.isTransfer -> AppTheme.materialColors.onSurfaceVariant
            row.isExpense -> AppTheme.materialColors.onSurface
            else -> AppTheme.semanticColors.income
        },
        amountPillBackground = if (row.isExpense || row.isTransfer) {
            null
        } else {
            AppTheme.semanticColors.income.copy(alpha = INCOME_PILL_ALPHA)
        },
        onClick = onClick,
    )
}

/**
 * `Groceries · Everyday` — the row's category, then the account that owns it once the list is not
 * scoped to one. Either half may be missing (an uncategorised row, a single-account list), so the
 * separator is joined in rather than baked into a string.
 */
private fun TransactionRowUi.metaLine(showAccount: Boolean): String? = listOf(
    subtitle,
    accountName.takeIf { showAccount }.orEmpty(),
).filter { it.isNotBlank() }
    .joinToString(META_SEPARATOR)
    .takeIf { it.isNotBlank() }

/**
 * Title, one line of guidance and a single CTA, per the design system's empty-state rule.
 *
 * The CTA is what tells the two cases apart: an untouched list has nothing to show *yet*, so it
 * offers the first transaction; a narrowed one has hidden what exists, so it offers the way back.
 * The search-only wording is separate from the filtered one because "clear filters" would name a
 * control the user never touched.
 */
@Composable
internal fun EmptyState(
    state: TransactionsByAccountState.Content,
    onEvent: (TransactionsByAccountEvent) -> Unit,
) {
    // `activeFilterCount`, not `isFiltered`: the latter already counts a non-blank query, so it
    // cannot tell "only searched" from "searched and filtered" — and only the former may promise
    // to clear a filter the user actually set.
    val hasFilters = state.activeFilterCount > 0
    val searchOnly = state.query.isNotBlank() && !hasFilters
    val narrowed = state.query.isNotBlank() || hasFilters
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        SurferEmptyState(
            title = stringResource(
                if (narrowed) {
                    Res.string.transactions_list_empty_filtered_title
                } else {
                    Res.string.transactions_list_empty_title
                },
            ),
            subtitle = stringResource(
                when {
                    searchOnly -> Res.string.transactions_list_empty_search
                    narrowed -> Res.string.transactions_list_empty_filtered
                    else -> Res.string.transactions_list_empty
                },
            ),
            icon = if (narrowed) SurferIcons.Search else SurferIcons.Receipt,
            actionLabel = stringResource(
                when {
                    searchOnly -> Res.string.transactions_list_empty_search_cta
                    narrowed -> Res.string.transactions_list_empty_filtered_cta
                    else -> Res.string.transactions_list_empty_cta
                },
            ),
            actionIcon = if (narrowed) SurferIcons.Close else SurferIcons.Add,
            onActionClick = {
                onEvent(
                    if (narrowed) {
                        TransactionsByAccountEvent.OnClearFiltersClick
                    } else {
                        TransactionsByAccountEvent.OnAddTransactionClick
                    },
                )
            },
        )
    }
}
