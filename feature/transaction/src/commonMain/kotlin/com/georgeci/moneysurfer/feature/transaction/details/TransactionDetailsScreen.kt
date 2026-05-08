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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.uikit.components.SurferCategoryBubble
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmount
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmountTier
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarAction
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_details_account_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_category_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_currency_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_date_label
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_cancel
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_confirm
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_content_description
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_message
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_message_generic
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_title
import moneysurfer.feature.transaction.generated.resources.transaction_details_duplicate
import moneysurfer.feature.transaction.generated.resources.transaction_details_edit_content_description
import moneysurfer.feature.transaction.generated.resources.transaction_details_status_planned
import moneysurfer.feature.transaction.generated.resources.transaction_details_status_posted
import moneysurfer.feature.transaction.generated.resources.transaction_details_title
import moneysurfer.feature.transaction.generated.resources.transaction_details_type_expense
import moneysurfer.feature.transaction.generated.resources.transaction_details_type_income
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun TransactionDetailsScreen(
    transactionId: TransactionId,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (TransactionId) -> Unit = {},
    viewModel: TransactionDetailsViewModel = koinViewModel(
        key = transactionId.value,
    ) { parametersOf(transactionId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            TransactionDetailsEffect.NavigateBack -> onNavigateBack()
            is TransactionDetailsEffect.NavigateToEdit -> onNavigateToEdit(effect.transactionId)
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
        DeleteConfirmationDialog(
            noteOrNull = state.note.ifBlank { null },
            onConfirm = { onEvent(TransactionDetailsEvent.OnDeleteConfirmed) },
            onDismiss = { onEvent(TransactionDetailsEvent.OnDeleteDismissed) },
        )
    }

    val typeLabel = stringResource(
        if (state.isExpense) {
            Res.string.transaction_details_type_expense
        } else {
            Res.string.transaction_details_type_income
        },
    )
    val categoryTint = if (state.categorySystemKind == SurferCategoryPalette.SYSTEM_KIND_TRANSFER) {
        SurferCategoryPalette.TransferTint
    } else {
        SurferCategoryPalette.tintForName(state.categoryName)
    }
    val categoryIcon = SurferCategoryPalette.iconFor(state.categoryName, state.categorySystemKind)

    Scaffold(
        modifier = Modifier.surferSafeInsets(),
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
                    )
                    SurferToolbarAction(
                        icon = SurferIcons.Delete,
                        contentDescription = stringResource(Res.string.transaction_details_delete_content_description),
                        tint = AppTheme.materialColors.error,
                        onClick = { onEvent(TransactionDetailsEvent.OnDeleteClick) },
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
                HeroCard(
                    categoryName = state.categoryName,
                    categoryTint = categoryTint,
                    categoryIcon = categoryIcon,
                    typeLabel = typeLabel.uppercase(),
                    formattedAmount = state.formattedAmount,
                    note = state.note,
                    formattedDate = state.formattedDate,
                    isPlanned = state.isPlanned,
                )

                DetailsCard(state = state)

                Spacer(Modifier.height(padding.calculateBottomPadding()))
            }

            if (ShowDuplicateAction) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = AppTheme.spacing.large),
                ) {
                    FilledTonalButton(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Icon(
                            imageVector = SurferIcons.Copy,
                            contentDescription = null,
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

@Composable
private fun HeroCard(
    categoryName: String,
    categoryTint: Color,
    categoryIcon: ImageVector,
    typeLabel: String,
    formattedAmount: String,
    note: String,
    formattedDate: String,
    isPlanned: Boolean,
) {
    val outlineVariant = AppTheme.materialColors.outlineVariant
    val surface = AppTheme.materialColors.surface
    val headerColor = categoryTint
    val heroBrush = Brush.linearGradient(
        colors = listOf(
            categoryTint.copy(alpha = 0.22f),
            surface,
        ),
    )
    val statusLabel = if (isPlanned) {
        stringResource(Res.string.transaction_details_status_planned)
    } else {
        stringResource(Res.string.transaction_details_status_posted)
    }
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
        val header = if (categoryName.isNotBlank()) {
            "${categoryName.uppercase()} · $typeLabel · ${statusLabel.uppercase()}"
        } else {
            "$typeLabel · ${statusLabel.uppercase()}"
        }
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
            contentDescription = null,
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

@Composable
private fun DetailsCard(state: TransactionDetailsState.Content) {
    val rows = buildList {
        if (state.accountName.isNotBlank()) {
            add(
                DetailRowSpec(
                    SurferIcons.CreditCard,
                    stringResource(Res.string.transaction_details_account_label),
                    state.accountName,
                ),
            )
        }
        add(
            DetailRowSpec(
                SurferIcons.Event,
                stringResource(Res.string.transaction_details_date_label),
                state.formattedDate,
            ),
        )
        if (state.categoryName.isNotBlank()) {
            add(
                DetailRowSpec(
                    SurferIcons.Category,
                    stringResource(Res.string.transaction_details_category_label),
                    state.categoryName,
                ),
            )
        }
        if (state.currency.isNotBlank()) {
            add(
                DetailRowSpec(
                    SurferIcons.Tag,
                    stringResource(Res.string.transaction_details_currency_label),
                    state.currency,
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
                contentDescription = null,
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

@Composable
private fun DeleteConfirmationDialog(
    noteOrNull: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = SurferIcons.Delete,
                contentDescription = null,
                tint = AppTheme.materialColors.error,
            )
        },
        title = {
            Text(
                text = stringResource(Res.string.transaction_details_delete_title),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            val msg = if (noteOrNull != null) {
                stringResource(Res.string.transaction_details_delete_message, noteOrNull)
            } else {
                stringResource(Res.string.transaction_details_delete_message_generic)
            }
            Text(text = msg, textAlign = TextAlign.Center)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.materialColors.error,
                    contentColor = AppTheme.materialColors.onError,
                ),
            ) {
                Text(stringResource(Res.string.transaction_details_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.transaction_details_delete_cancel))
            }
        },
    )
}

@Preview
@Composable
private fun TransactionDetailsExpensePreview() {
    AppTheme {
        TransactionDetailsContent(
            state = TransactionDetailsState.Content(
                transactionId = TransactionId("preview-tx-1"),
                formattedAmount = "−€48.20",
                isExpense = true,
                note = "Lidl — weekly shop",
                accountName = "Everyday",
                categoryName = "Groceries",
                currency = "EUR",
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
private fun TransactionDetailsIncomePreview() {
    AppTheme {
        TransactionDetailsContent(
            state = TransactionDetailsState.Content(
                transactionId = TransactionId("preview-tx-2"),
                formattedAmount = "€1,500.00",
                isExpense = false,
                note = "Monthly salary",
                accountName = "Savings",
                categoryName = "Income",
                currency = "EUR",
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
        TransactionDetailsContent(
            state = TransactionDetailsState.Content(
                transactionId = TransactionId("preview-tx-1"),
                formattedAmount = "−€48.20",
                isExpense = true,
                note = "Lidl — weekly shop",
                accountName = "Everyday",
                categoryName = "Groceries",
                currency = "EUR",
                formattedDate = "18 Mar 2025",
                isPlanned = false,
                showDeleteConfirmation = true,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun TransactionDetailsPlannedPreview() {
    AppTheme {
        TransactionDetailsContent(
            state = TransactionDetailsState.Content(
                transactionId = TransactionId("preview-tx-1"),
                formattedAmount = "−€48.20",
                isExpense = true,
                note = "Lidl — weekly shop",
                accountName = "Everyday",
                categoryName = "Groceries",
                currency = "EUR",
                formattedDate = "20 Mar 2025",
                isPlanned = true,
                showDeleteConfirmation = false,
            ),
            onEvent = {},
        )
    }
}

private const val ShowDuplicateAction = false
