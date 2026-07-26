package com.georgeci.moneysurfer.feature.transaction.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.TransactionType
import com.georgeci.moneysurfer.uikit.components.SurferCategoryBubble
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.SurferCategoryVisual
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmount
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmountTier
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarAction
import com.georgeci.moneysurfer.uikit.components.transaction.SurferDeleteTransactionDialog
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_details_account_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_category_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_category_nested
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_content_description
import moneysurfer.feature.transaction.generated.resources.transaction_details_duplicate
import moneysurfer.feature.transaction.generated.resources.transaction_details_edit_content_description
import moneysurfer.feature.transaction.generated.resources.transaction_details_from_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_merchant_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_reference_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_status_planned
import moneysurfer.feature.transaction.generated.resources.transaction_details_status_posted
import moneysurfer.feature.transaction.generated.resources.transaction_details_tags_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_title
import moneysurfer.feature.transaction.generated.resources.transaction_details_to_account_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_to_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_type_expense
import moneysurfer.feature.transaction.generated.resources.transaction_details_type_income
import moneysurfer.feature.transaction.generated.resources.transaction_details_type_transfer
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Stable selectors for the transaction details screen — see docs/testing/testing-strategy.md.
 *
 * The dialog's confirm button is not here: the dialog moved to uikit when the transaction lists
 * gained swipe-to-delete, and its tag went with it as
 * [SurferDeleteTransactionDialogTestTags][com.georgeci.moneysurfer.uikit.components.transaction.SurferDeleteTransactionDialogTestTags].
 * The id the flows tap is unchanged.
 */
object TransactionDetailsTestTags {
    const val Root = "transactionDetails:root"
    const val Edit = "transactionDetails:edit"
    const val Delete = "transactionDetails:delete"
}

@Composable
fun TransactionDetailsScreen(
    transactionId: TransactionId,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (TransactionId) -> Unit = {},
    onNavigateToDuplicate: (TransactionId) -> Unit = {},
    viewModel: TransactionDetailsViewModel = koinViewModel(
        key = transactionId.value,
    ) { parametersOf(transactionId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            TransactionDetailsEffect.NavigateBack -> onNavigateBack()
            is TransactionDetailsEffect.NavigateToEdit -> onNavigateToEdit(effect.transactionId)
            is TransactionDetailsEffect.NavigateToDuplicate -> onNavigateToDuplicate(effect.transactionId)
        }
    }

    when (val current = state) {
        is TransactionDetailsState.Loading -> TransactionDetailsLoading(onEvent = viewModel::onEvent)
        is TransactionDetailsState.Content -> TransactionDetailsContent(
            state = current,
            onEvent = viewModel::onEvent,
        )
    }
}

@Composable
private fun TransactionDetailsLoading(onEvent: (TransactionDetailsEvent) -> Unit) {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.transaction_details_title),
                onBack = { onEvent(TransactionDetailsEvent.OnBackClick) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
private fun TransactionDetailsContent(
    state: TransactionDetailsState.Content,
    onEvent: (TransactionDetailsEvent) -> Unit,
) {
    if (state.showDeleteConfirmation) {
        SurferDeleteTransactionDialog(
            titleOrNull = state.note.ifBlank { null },
            onConfirm = { onEvent(TransactionDetailsEvent.OnDeleteConfirmed) },
            onDismiss = { onEvent(TransactionDetailsEvent.OnDeleteDismissed) },
            isTransfer = state.isTransfer,
        )
    }

    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .testTag(TransactionDetailsTestTags.Root)
            .surferTestTagAsId(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.transaction_details_title),
                onBack = { onEvent(TransactionDetailsEvent.OnBackClick) },
                actions = {
                    SurferToolbarAction(
                        icon = SurferIcons.Edit,
                        contentDescription = stringResource(Res.string.transaction_details_edit_content_description),
                        onClick = { onEvent(TransactionDetailsEvent.OnEditClick) },
                        modifier = Modifier.testTag(TransactionDetailsTestTags.Edit),
                    )
                    SurferToolbarAction(
                        icon = SurferIcons.Delete,
                        contentDescription = stringResource(Res.string.transaction_details_delete_content_description),
                        onClick = { onEvent(TransactionDetailsEvent.OnDeleteClick) },
                        modifier = Modifier.testTag(TransactionDetailsTestTags.Delete),
                        tint = AppTheme.materialColors.error,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .weight(1f),
            ) {
                val heroVisual = heroVisualFor(state)
                HeroCard(
                    header = heroHeaderFor(state),
                    headerColor = heroVisual.tint,
                    categoryTint = heroVisual.tint,
                    categoryIcon = heroVisual.icon,
                    formattedAmount = state.formattedAmount,
                    note = state.note,
                    formattedDate = state.formattedDate,
                    isPlanned = state.isPlanned,
                )

                DetailsCard(state = state)

                Spacer(Modifier.height(padding.calculateBottomPadding()))
            }

            if (state.canDuplicate) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = AppTheme.spacing.large),
                ) {
                    FilledTonalButton(
                        onClick = { onEvent(TransactionDetailsEvent.OnDuplicateClick) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Icon(
                            imageVector = SurferIcons.Copy,
                            contentDescription = SurferSemantics.Decorative,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(AppTheme.spacing.small))
                        Text(stringResource(Res.string.transaction_details_duplicate))
                    }
                }
            }
        }
    }
}

/**
 * Hero bubble and hue per variant: a transfer is neutral with a swap glyph (money moved sideways,
 * its category is the seeded system one), income is green whatever the category says, and an
 * expense keeps its category's own colour.
 *
 * The category's stored appearance is read rather than its *name* hashed — two categories renamed
 * to the same word used to share a colour, and a rename silently repainted the screen.
 */
@Composable
private fun heroVisualFor(state: TransactionDetailsState.Content): SurferCategoryVisual {
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
private fun heroHeaderFor(state: TransactionDetailsState.Content): String {
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
private fun HeroCard(
    header: String,
    headerColor: Color,
    categoryTint: Color,
    categoryIcon: ImageVector,
    formattedAmount: String,
    note: String,
    formattedDate: String,
    isPlanned: Boolean,
) {
    val outlineVariant = AppTheme.materialColors.outlineVariant
    val surface = AppTheme.materialColors.surface
    val heroBrush = Brush.linearGradient(
        colors = listOf(
            categoryTint.copy(alpha = 0.22f),
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
        SurferCategoryBubble(icon = categoryIcon, tint = categoryTint, size = 64.dp)
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

/**
 * Account, Merchant, Category, Tags, Reference — the rows the design asks for. Date is not among
 * them: the hero already carries it, and the currency is legible in every formatted amount on the
 * screen, so neither earns a row of its own.
 *
 * A transfer swaps the account and category rows for `From` / `To`, since "which account" has two
 * answers and the category is always the seeded system one.
 */
@Composable
private fun DetailsCard(state: TransactionDetailsState.Content) {
    val rows = buildList {
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

private const val TAG_SEPARATOR = " · "

private data class DetailRowSpec(val icon: ImageVector, val label: String, val value: String)

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

private fun previewExpense(showDeleteConfirmation: Boolean = false, isPlanned: Boolean = false) =
    TransactionDetailsState.Content(
        transactionId = TransactionId("preview-tx-1a13"),
        formattedAmount = "−€48.20",
        type = TransactionType.EXPENSE,
        note = "Lidl — weekly shop",
        merchant = "Lidl",
        accountName = "Everyday",
        categoryName = "Groceries",
        parentCategoryName = "Home",
        tags = listOf("weekly", "food"),
        reference = "TX-1A13",
        formattedDate = "18 Mar 2025",
        isPlanned = isPlanned,
        showDeleteConfirmation = showDeleteConfirmation,
    )

@Preview
@Composable
private fun TransactionDetailsExpensePreview() {
    AppTheme {
        TransactionDetailsContent(state = previewExpense(), onEvent = {})
    }
}

@Preview
@Composable
private fun TransactionDetailsIncomePreview() {
    AppTheme {
        TransactionDetailsContent(
            state = TransactionDetailsState.Content(
                transactionId = TransactionId("preview-tx-2b24"),
                formattedAmount = "+€1,500.00",
                type = TransactionType.INCOME,
                note = "Monthly salary",
                merchant = "Acme Ltd",
                accountName = "Savings",
                categoryName = "Salary",
                reference = "TX-2B24",
                formattedDate = "18 Mar 2025",
                isPlanned = false,
                showDeleteConfirmation = false,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun TransactionDetailsTransferPreview() {
    AppTheme {
        TransactionDetailsContent(
            state = TransactionDetailsState.Content(
                transactionId = TransactionId("preview-tx-3c35"),
                formattedAmount = "€200.00",
                type = TransactionType.EXPENSE,
                transfer = TransferLeg(fromAccountName = "Everyday", toAccountName = "Savings"),
                note = "Rainy day top-up",
                accountName = "Everyday",
                categoryName = "Transfer",
                categorySystemKind = SurferCategoryPalette.SYSTEM_KIND_TRANSFER,
                categoryIconKey = SurferCategoryPalette.TRANSFER_ICON_KEY,
                reference = "TX-3C35",
                formattedDate = "18 Mar 2025",
                isPlanned = false,
                showDeleteConfirmation = false,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun TransactionDetailsDeletePreview() {
    AppTheme {
        TransactionDetailsContent(state = previewExpense(showDeleteConfirmation = true), onEvent = {})
    }
}

@Preview
@Composable
private fun TransactionDetailsPlannedPreview() {
    AppTheme {
        TransactionDetailsContent(state = previewExpense(isPlanned = true), onEvent = {})
    }
}
