package com.georgeci.moneysurfer.feature.transaction.list

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.SurferEmptyState
import com.georgeci.moneysurfer.uikit.components.transaction.SurferSwipeToDeleteTransaction
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
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Alpha of the transfer tile's tint wash — the same weight the category bubbles use. */
private const val TRANSFER_TILE_ALPHA = 0.18f

/** Alpha of the income amount pill behind the row's amount. */
private const val INCOME_PILL_ALPHA = 0.14f

private const val META_SEPARATOR = " · "

/**
 * One transaction line, with the three variants the list can render, swipeable to delete.
 *
 * A transfer leg is deliberately neither of the other two: it carries the swap glyph on a neutral
 * transfer tint and no amount pill, because the money did not leave or arrive — it moved sideways,
 * and drawing it as a plain expense is exactly what made the two legs unreadable.
 *
 * The row's gap to the next one is on the swipe wrapper while its inset stays on the line: the
 * Delete button is measured against the wrapper, so a bottom padding inside it would push the
 * button off the line's centre, and a horizontal one would leave a dead 16dp strip at the edge
 * where the swipe starts.
 */
@Composable
internal fun TransactionRow(
    row: TransactionRowUi,
    showAccount: Boolean,
    untitled: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val title = row.title.ifBlank { untitled }
    SurferSwipeToDeleteTransaction(
        transactionTitle = title,
        onDelete = onDelete,
        modifier = Modifier.padding(bottom = 12.dp),
        isTransfer = row.isTransfer,
    ) {
        TransactionLine(row = row, showAccount = showAccount, title = title, onClick = onClick)
    }
}

@Composable
private fun TransactionLine(
    row: TransactionRowUi,
    showAccount: Boolean,
    title: String,
    onClick: () -> Unit,
) {
    val transferTint = AppTheme.semanticColors.transfer
    SurferTransactionLine(
        modifier = Modifier.padding(horizontal = 16.dp),
        icon = when {
            row.isTransfer -> SurferCategoryPalette.TransferIcon
            row.isExpense -> SurferIcons.Receipt
            else -> SurferIcons.Wallet
        },
        title = title,
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
 * Why the list is empty, which is what every word and the CTA on the empty state follow from.
 *
 * Resolved from [TransactionsByAccountState.Content.activeFilterCount] rather than from
 * `isFiltered`: the latter already counts a non-blank query, so it cannot tell [Search] from
 * [Filtered] — and only a real filter may be offered for clearing.
 */
private enum class EmptyReason { FirstRun, Search, Filtered }

private fun TransactionsByAccountState.Content.emptyReason(): EmptyReason = when {
    activeFilterCount > 0 -> EmptyReason.Filtered
    query.isNotBlank() -> EmptyReason.Search
    else -> EmptyReason.FirstRun
}

/** Everything the empty state renders, so each reason names its copy in one place. */
private data class EmptyStateSpec(
    val title: StringResource,
    val guidance: StringResource,
    val actionLabel: StringResource,
    val icon: ImageVector,
    val actionIcon: ImageVector,
    val action: TransactionsByAccountEvent,
)

private fun EmptyReason.spec(): EmptyStateSpec = when (this) {
    // Nothing to show *yet*: the next step is the first transaction.
    EmptyReason.FirstRun -> EmptyStateSpec(
        title = Res.string.transactions_list_empty_title,
        guidance = Res.string.transactions_list_empty,
        actionLabel = Res.string.transactions_list_empty_cta,
        icon = SurferIcons.Receipt,
        actionIcon = SurferIcons.Add,
        action = TransactionsByAccountEvent.OnAddTransactionClick,
    )
    // Rows exist and are hidden, so the CTA is the way back rather than a way forward. "Clear
    // filters" would name a control the search-only user never touched, hence the two variants.
    EmptyReason.Search -> EmptyStateSpec(
        title = Res.string.transactions_list_empty_filtered_title,
        guidance = Res.string.transactions_list_empty_search,
        actionLabel = Res.string.transactions_list_empty_search_cta,
        icon = SurferIcons.Search,
        actionIcon = SurferIcons.Close,
        action = TransactionsByAccountEvent.OnClearFiltersClick,
    )
    EmptyReason.Filtered -> EmptyStateSpec(
        title = Res.string.transactions_list_empty_filtered_title,
        guidance = Res.string.transactions_list_empty_filtered,
        actionLabel = Res.string.transactions_list_empty_filtered_cta,
        icon = SurferIcons.Search,
        actionIcon = SurferIcons.Close,
        action = TransactionsByAccountEvent.OnClearFiltersClick,
    )
}

/** Title, one line of guidance and a single CTA, per the design system's empty-state rule. */
@Composable
internal fun EmptyState(
    state: TransactionsByAccountState.Content,
    onEvent: (TransactionsByAccountEvent) -> Unit,
) {
    val spec = state.emptyReason().spec()
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        SurferEmptyState(
            title = stringResource(spec.title),
            subtitle = stringResource(spec.guidance),
            icon = spec.icon,
            actionLabel = stringResource(spec.actionLabel),
            actionIcon = spec.actionIcon,
            onActionClick = { onEvent(spec.action) },
        )
    }
}
