package com.georgeci.moneysurfer.feature.transaction.details

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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.uikit.components.base.SurferPaneScaffold
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarAction
import com.georgeci.moneysurfer.uikit.components.transaction.SurferDeleteTransactionDialog
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferContentContainer
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_details_delete_content_description
import moneysurfer.feature.transaction.generated.resources.transaction_details_duplicate
import moneysurfer.feature.transaction.generated.resources.transaction_details_edit_content_description
import moneysurfer.feature.transaction.generated.resources.transaction_details_title
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
    SurferPaneScaffold(
        title = stringResource(Res.string.transaction_details_title),
        onBack = { onEvent(TransactionDetailsEvent.OnBackClick) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
internal fun TransactionDetailsContent(
    state: TransactionDetailsState.Content,
    onEvent: (TransactionDetailsEvent) -> Unit,
) {
    if (state.showDeleteConfirmation) {
        SurferDeleteTransactionDialog(
            titleOrNull = state.note.ifBlank { null },
            onConfirm = { onEvent(TransactionDetailsEvent.OnDeleteConfirmed) },
            onDismiss = { onEvent(TransactionDetailsEvent.OnDeleteDismissed) },
            isTransfer = state.isTransfer,
            isSplit = state.isSplit,
        )
    }

    SurferPaneScaffold(
        title = stringResource(Res.string.transaction_details_title),
        modifier = Modifier
            .testTag(TransactionDetailsTestTags.Root)
            .surferTestTagAsId(),
        onBack = { onEvent(TransactionDetailsEvent.OnBackClick) },
        actions = { DetailsActions(onEvent = onEvent) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .surferContentContainer()
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
                    visual = heroVisual,
                    formattedAmount = state.formattedAmount,
                    note = state.note,
                    formattedDate = state.formattedDate,
                    isPlanned = state.isPlanned,
                )

                state.split?.let { SplitCard(split = it) }

                DetailsCard(state = state)

                Spacer(Modifier.height(padding.calculateBottomPadding()))
            }

            if (state.canDuplicate) {
                DuplicateButton(onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun DetailsActions(onEvent: (TransactionDetailsEvent) -> Unit) {
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
}

@Composable
private fun DuplicateButton(onEvent: (TransactionDetailsEvent) -> Unit) {
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
