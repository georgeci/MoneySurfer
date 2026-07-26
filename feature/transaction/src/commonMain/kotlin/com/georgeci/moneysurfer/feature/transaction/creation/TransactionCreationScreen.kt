package com.georgeci.moneysurfer.feature.transaction.creation

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.feature.transaction.delete.TransactionDeleteConfirmationDialog
import com.georgeci.moneysurfer.uikit.components.SurferButton
import com.georgeci.moneysurfer.uikit.components.SurferButtonSize
import com.georgeci.moneysurfer.uikit.components.SurferButtonStyle
import com.georgeci.moneysurfer.uikit.components.SurferPickerRow
import com.georgeci.moneysurfer.uikit.components.base.SurferSegmentedControl
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_creation_account_empty
import moneysurfer.feature.transaction.generated.resources.transaction_creation_account_label
import moneysurfer.feature.transaction.generated.resources.transaction_creation_close_content_description
import moneysurfer.feature.transaction.generated.resources.transaction_creation_expense
import moneysurfer.feature.transaction.generated.resources.transaction_creation_income
import moneysurfer.feature.transaction.generated.resources.transaction_creation_note_label
import moneysurfer.feature.transaction.generated.resources.transaction_creation_title_create
import moneysurfer.feature.transaction.generated.resources.transaction_creation_today
import moneysurfer.feature.transaction.generated.resources.transaction_creation_transfer
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Stable selectors for the transaction creation screen — see docs/testing/testing-strategy.md. */
object TransactionCreationTestTags {
    const val Root = "transactionCreation:root"
    const val Amount = "transactionCreation:amount"
    const val Note = "transactionCreation:note"
    const val Save = "transactionCreation:save"
    const val Delete = "transactionCreation:delete"
}

private fun transactionCreationSeed(
    transactionId: TransactionId?,
    duplicate: Boolean,
): TransactionCreationSeed? = transactionId?.let {
    TransactionCreationSeed(
        transactionId = it,
        mode = if (duplicate) {
            TransactionCreationSeed.Mode.Duplicate
        } else {
            TransactionCreationSeed.Mode.Edit
        },
    )
}

@Composable
fun TransactionCreationScreen(
    transactionId: TransactionId? = null,
    accountId: AccountId? = null,
    /** Treat [transactionId] as a template for a new transaction rather than the row to edit. */
    duplicate: Boolean = false,
    onNavigateBack: () -> Unit,
    /**
     * Leaving after the edited transaction was deleted. Defaults to [onNavigateBack]; the nav graph
     * overrides it to also drop the details screen of the row that no longer exists.
     */
    onNavigateBackAfterDelete: () -> Unit = onNavigateBack,
    onNavigateToCategoryChooser: (CategoryId?, CategoryType) -> Unit,
    onNavigateToCategoryCreation: () -> Unit,
    onNavigateToAccountChooser: (AccountId?, AccountId?, Boolean) -> Unit,
    pickedCategoryId: CategoryId? = null,
    pickedAccountId: AccountId? = null,
    transferRequested: Boolean? = null,
    viewModel: TransactionCreationViewModel = koinViewModel(
        key = "$transactionId:$accountId:$duplicate",
    ) { parametersOf(transactionCreationSeed(transactionId, duplicate), accountId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            TransactionCreationEffect.NavigateBack -> onNavigateBack()
            TransactionCreationEffect.NavigateBackAfterDelete -> onNavigateBackAfterDelete()
            is TransactionCreationEffect.NavigateToCategoryChooser -> onNavigateToCategoryChooser(
                effect.selectedCategoryId,
                effect.filterType,
            )
            TransactionCreationEffect.NavigateToCategoryCreation -> onNavigateToCategoryCreation()
            is TransactionCreationEffect.NavigateToAccountChooser -> onNavigateToAccountChooser(
                effect.selectedAccountId,
                effect.excludeAccountId,
                effect.showTransferShortcut,
            )
        }
    }

    LaunchedEffect(pickedCategoryId) {
        val id = pickedCategoryId ?: return@LaunchedEffect
        viewModel.onEvent(TransactionCreationEvent.OnCategoryPicked(id))
    }

    LaunchedEffect(pickedAccountId) {
        val id = pickedAccountId ?: return@LaunchedEffect
        viewModel.onEvent(TransactionCreationEvent.OnAccountPicked(id))
    }

    LaunchedEffect(transferRequested) {
        if (transferRequested != true) return@LaunchedEffect
        viewModel.onEvent(TransactionCreationEvent.OnTypeChanged(TransactionTypeUi.Transfer))
    }

    when (val current = state) {
        TransactionCreationState.Loading -> TransactionCreationLoading(onEvent = viewModel::onEvent)
        is TransactionCreationState.Content -> TransactionCreationContent(
            state = current,
            onEvent = viewModel::onEvent,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionCreationLoading(onEvent: (TransactionCreationEvent) -> Unit) {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.transaction_creation_title_create)) },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(48.dp)
                            .clickable { onEvent(TransactionCreationEvent.OnBackClick) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = SurferIcons.Close,
                            contentDescription = stringResource(
                                Res.string.transaction_creation_close_content_description,
                            ),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.materialColors.surface,
                ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionCreationContent(
    state: TransactionCreationState.Content,
    onEvent: (TransactionCreationEvent) -> Unit,
    amountState: TextFieldState = rememberTextFieldState(state.amount),
    toAmountState: TextFieldState = rememberTextFieldState(state.toAmount),
) {
    SyncAmountFields(state = state, amountState = amountState, toAmountState = toAmountState, onEvent = onEvent)

    if (state.showDeleteConfirmation) {
        TransactionDeleteConfirmationDialog(
            noteOrNull = state.note.ifBlank { null },
            onConfirm = { onEvent(TransactionCreationEvent.OnDeleteConfirmed) },
            onDismiss = { onEvent(TransactionCreationEvent.OnDeleteDismissed) },
        )
    }

    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .surferTestTagAsId()
            .testTag(TransactionCreationTestTags.Root),
        containerColor = AppTheme.materialColors.surface,
        topBar = { CreationTopBar(state = state, onEvent = onEvent) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            state.editIdentity?.let { identity ->
                EditIdentityBand(
                    identity = identity,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = AppTheme.spacing.default),
                )
            }

            TypeSegments(state = state, onEvent = onEvent)

            Spacer(Modifier.height(AppTheme.spacing.large))

            if (state.isTransfer) {
                TransferAccountsBlock(
                    state = state,
                    fromAmountState = amountState,
                    toAmountState = toAmountState,
                    onEvent = onEvent,
                )
            } else {
                AmountHero(
                    type = state.type,
                    currencySymbol = currencySymbol(state.selectedAccount?.currencyCode),
                    amountState = amountState,
                    error = state.amountError,
                )
            }

            Spacer(Modifier.height(AppTheme.spacing.default))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = padding.calculateBottomPadding() + AppTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
            ) {
                if (!state.isTransfer) {
                    SurferPickerRow(
                        label = stringResource(Res.string.transaction_creation_account_label),
                        value = state.selectedAccount?.name
                            ?: stringResource(Res.string.transaction_creation_account_empty),
                        icon = SurferIcons.CreditCard,
                        onClick = { onEvent(TransactionCreationEvent.OnOpenAccountChooser) },
                    )

                    CategoryGridSection(
                        categories = state.displayCategories,
                        selected = state.selectedCategory,
                        onSelect = { onEvent(TransactionCreationEvent.OnCategorySelected(it)) },
                        onAllClick = { onEvent(TransactionCreationEvent.OnOpenCategoryChooser) },
                        onMoreClick = { onEvent(TransactionCreationEvent.OnOpenCategoryCreation) },
                    )
                }

                OutlinedTextField(
                    value = state.note,
                    onValueChange = { onEvent(TransactionCreationEvent.OnNoteChanged(it)) },
                    label = { Text(stringResource(Res.string.transaction_creation_note_label)) },
                    shape = AppTheme.shapes.extraSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TransactionCreationTestTags.Note),
                    minLines = 2,
                    maxLines = 4,
                )

                DateRow(state = state, onEvent = onEvent)
            }
        }
    }
}

/** Keeps the two text-field buffers and the ViewModel's amounts in step, in both directions. */
@Composable
private fun SyncAmountFields(
    state: TransactionCreationState.Content,
    amountState: TextFieldState,
    toAmountState: TextFieldState,
    onEvent: (TransactionCreationEvent) -> Unit,
) {
    LaunchedEffect(amountState) {
        snapshotFlow { amountState.text.toString() }
            .collect { onEvent(TransactionCreationEvent.OnAmountChanged(it)) }
    }
    LaunchedEffect(state.amount) {
        if (amountState.text.toString() != state.amount) {
            amountState.edit { replace(0, length, state.amount) }
        }
    }
    LaunchedEffect(toAmountState) {
        snapshotFlow { toAmountState.text.toString() }
            .collect { onEvent(TransactionCreationEvent.OnToAmountChanged(it)) }
    }
    LaunchedEffect(state.toAmount) {
        if (toAmountState.text.toString() != state.toAmount) {
            toAmountState.edit { replace(0, length, state.toAmount) }
        }
    }
}

@Composable
private fun TypeSegments(
    state: TransactionCreationState.Content,
    onEvent: (TransactionCreationEvent) -> Unit,
) {
    // Editing an existing single-leg transaction can't morph into a paired transfer in place —
    // hide the Transfer segment to avoid silently creating a new transfer pair alongside the
    // original row. The offline build also hides Transfer entirely (multi-account transfers are
    // out of MVP scope) via `transferEnabled`.
    val transferVisible = !state.isEditMode && state.transferEnabled
    val typeOptions = remember(transferVisible) {
        if (transferVisible) {
            listOf(TransactionTypeUi.Expense, TransactionTypeUi.Income, TransactionTypeUi.Transfer)
        } else {
            listOf(TransactionTypeUi.Expense, TransactionTypeUi.Income)
        }
    }
    val expenseLabel = stringResource(Res.string.transaction_creation_expense)
    val incomeLabel = stringResource(Res.string.transaction_creation_income)
    val transferLabel = stringResource(Res.string.transaction_creation_transfer)
    SurferSegmentedControl(
        options = typeOptions,
        selected = state.type,
        label = {
            when (it) {
                TransactionTypeUi.Expense -> expenseLabel
                TransactionTypeUi.Income -> incomeLabel
                TransactionTypeUi.Transfer -> transferLabel
            }
        },
        onSelect = { onEvent(TransactionCreationEvent.OnTypeChanged(it)) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    )
}

@Composable
private fun DateRow(
    state: TransactionCreationState.Content,
    onEvent: (TransactionCreationEvent) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateTimeField(
            timestamp = state.timestamp,
            onClick = { showDatePicker = true },
            modifier = Modifier.weight(1f),
        )
        SurferButton(
            text = stringResource(Res.string.transaction_creation_today),
            onClick = { onEvent(TransactionCreationEvent.OnTodayClick) },
            style = SurferButtonStyle.Tonal,
            size = SurferButtonSize.Biggest,
            startIcon = SurferIcons.Event,
        )
    }

    if (showDatePicker) {
        TransactionDatePickerDialog(
            initialTimestamp = state.timestamp,
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                onEvent(TransactionCreationEvent.OnDateChanged(picked))
                showDatePicker = false
            },
        )
    }
}
