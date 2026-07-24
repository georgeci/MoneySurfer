package com.georgeci.moneysurfer.feature.transaction.creation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.formatter.MoneyFormatter
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.uikit.components.SurferButton
import com.georgeci.moneysurfer.uikit.components.SurferButtonSize
import com.georgeci.moneysurfer.uikit.components.SurferButtonStyle
import com.georgeci.moneysurfer.uikit.components.SurferCategoryBubble
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.SurferPickerRow
import com.georgeci.moneysurfer.uikit.components.base.SurferSegmentedControl
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarButtonAction
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.AmountInputTransformation
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_creation_account_empty
import moneysurfer.feature.transaction.generated.resources.transaction_creation_account_label
import moneysurfer.feature.transaction.generated.resources.transaction_creation_amount_error_format
import moneysurfer.feature.transaction.generated.resources.transaction_creation_amount_error_zero
import moneysurfer.feature.transaction.generated.resources.transaction_creation_amount_placeholder
import moneysurfer.feature.transaction.generated.resources.transaction_creation_category_all
import moneysurfer.feature.transaction.generated.resources.transaction_creation_category_label
import moneysurfer.feature.transaction.generated.resources.transaction_creation_category_more
import moneysurfer.feature.transaction.generated.resources.transaction_creation_close_content_description
import moneysurfer.feature.transaction.generated.resources.transaction_creation_expense
import moneysurfer.feature.transaction.generated.resources.transaction_creation_from_account
import moneysurfer.feature.transaction.generated.resources.transaction_creation_from_label
import moneysurfer.feature.transaction.generated.resources.transaction_creation_income
import moneysurfer.feature.transaction.generated.resources.transaction_creation_note_label
import moneysurfer.feature.transaction.generated.resources.transaction_creation_rate_hint
import moneysurfer.feature.transaction.generated.resources.transaction_creation_save
import moneysurfer.feature.transaction.generated.resources.transaction_creation_swap_content_description
import moneysurfer.feature.transaction.generated.resources.transaction_creation_title_create
import moneysurfer.feature.transaction.generated.resources.transaction_creation_title_edit
import moneysurfer.feature.transaction.generated.resources.transaction_creation_to_account
import moneysurfer.feature.transaction.generated.resources.transaction_creation_to_label
import moneysurfer.feature.transaction.generated.resources.transaction_creation_today
import moneysurfer.feature.transaction.generated.resources.transaction_creation_transfer
import moneysurfer.feature.transaction.generated.resources.transaction_creation_update
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** Stable selectors for the transaction creation screen — see docs/testing/testing-strategy.md. */
object TransactionCreationTestTags {
    const val Root = "transactionCreation:root"
    const val Amount = "transactionCreation:amount"
    const val Note = "transactionCreation:note"
    const val Save = "transactionCreation:save"
}

@Composable
fun TransactionCreationScreen(
    transactionId: TransactionId? = null,
    accountId: AccountId? = null,
    onNavigateBack: () -> Unit,
    onNavigateToCategoryChooser: (CategoryId?, CategoryType) -> Unit,
    onNavigateToCategoryCreation: () -> Unit,
    onNavigateToAccountChooser: (AccountId?, AccountId?) -> Unit,
    pickedCategoryId: CategoryId? = null,
    pickedAccountId: AccountId? = null,
    viewModel: TransactionCreationViewModel = koinViewModel(
        key = "$transactionId:$accountId",
    ) { parametersOf(transactionId, accountId) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            TransactionCreationEffect.NavigateBack -> onNavigateBack()
            is TransactionCreationEffect.NavigateToCategoryChooser -> onNavigateToCategoryChooser(
                effect.selectedCategoryId,
                effect.filterType,
            )
            TransactionCreationEffect.NavigateToCategoryCreation -> onNavigateToCategoryCreation()
            is TransactionCreationEffect.NavigateToAccountChooser -> onNavigateToAccountChooser(
                effect.selectedAccountId,
                effect.excludeAccountId,
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
private fun TransactionCreationContent(
    state: TransactionCreationState.Content,
    onEvent: (TransactionCreationEvent) -> Unit,
    amountState: TextFieldState = rememberTextFieldState(state.amount),
    toAmountState: TextFieldState = rememberTextFieldState(state.toAmount),
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

    val title = if (state.isEditMode) {
        stringResource(Res.string.transaction_creation_title_edit)
    } else {
        stringResource(Res.string.transaction_creation_title_create)
    }
    val saveLabel = if (state.isEditMode) {
        stringResource(Res.string.transaction_creation_update)
    } else {
        stringResource(Res.string.transaction_creation_save)
    }

    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .surferTestTagAsId()
            .testTag(TransactionCreationTestTags.Root),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            TopAppBar(
                title = { Text(title, style = AppTheme.typography.titleLarge) },
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
                actions = {
                    SurferToolbarButtonAction(
                        icon = SurferIcons.Check,
                        text = saveLabel,
                        onClick = { onEvent(TransactionCreationEvent.OnSaveClick) },
                        enabled = state.isSaveEnabled,
                        modifier = Modifier.testTag(TransactionCreationTestTags.Save),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.materialColors.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            // Editing an existing single-leg transaction can't morph into a paired transfer in
            // place — hide the Transfer segment to avoid silently creating a new transfer pair
            // alongside the original row. The offline build also hides Transfer entirely
            // (multi-account transfers are out of MVP scope) via `transferEnabled`.
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
        }
    }
}

private const val CATEGORY_PREVIEW_SIZE = 7

/** Opacity of the type pill's tint wash. See [TypePill] for why it is composited, not blended. */
private const val TYPE_PILL_WASH_ALPHA = 0.18f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountHero(
    type: TransactionTypeUi,
    currencySymbol: String,
    amountState: TextFieldState,
    error: TransactionAmountError?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TypePill(type = type)
        Spacer(Modifier.height(AppTheme.spacing.xSmall))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = currencySymbol,
                style = AppTheme.typography.displayMedium,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            val text = amountState.text.toString()
            val placeholderText = stringResource(Res.string.transaction_creation_amount_placeholder)
            BasicTextField(
                state = amountState,
                modifier = Modifier.testTag(TransactionCreationTestTags.Amount),
                inputTransformation = AmountInputTransformation,
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = LocalTextStyle.current.merge(AppTheme.typography.displayLarge).copy(
                    color = AppTheme.materialColors.onSurface,
                    textAlign = TextAlign.Start,
                    fontWeight = FontWeight.Medium,
                ),
                cursorBrush = SolidColor(AppTheme.materialColors.primary),
                decorator = { innerTextField ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                text = placeholderText,
                                style = AppTheme.typography.displayLarge,
                                color = AppTheme.materialColors.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        if (error != null) {
            Spacer(Modifier.height(AppTheme.spacing.xSmall))
            AmountErrorText(error)
        }
    }
}

@Composable
private fun AmountErrorText(
    error: TransactionAmountError,
    modifier: Modifier = Modifier,
) {
    val messageRes: StringResource = when (error) {
        TransactionAmountError.INVALID_FORMAT -> Res.string.transaction_creation_amount_error_format
        TransactionAmountError.NOT_POSITIVE -> Res.string.transaction_creation_amount_error_zero
    }
    Text(
        text = stringResource(messageRes),
        style = AppTheme.typography.bodySmall,
        color = AppTheme.materialColors.error,
        modifier = modifier,
    )
}

@Composable
private fun TypePill(type: TransactionTypeUi) {
    val tint = typeTint(type)
    val labelRes = when (type) {
        TransactionTypeUi.Expense -> Res.string.transaction_creation_expense
        TransactionTypeUi.Income -> Res.string.transaction_creation_income
        TransactionTypeUi.Transfer -> Res.string.transaction_creation_transfer
    }
    // Composited over `surface` rather than left translucent: the label is `tint` on a wash
    // of the same tint, so the contrast ratio depends entirely on what shows through. Pinning
    // the wash to the scaffold's own colour keeps the pair at a known ≥4.5:1 even if the pill
    // is later moved onto a card.
    val wash = tint.copy(alpha = TYPE_PILL_WASH_ALPHA).compositeOver(AppTheme.materialColors.surface)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(wash)
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(labelRes).uppercase(),
            style = AppTheme.typography.labelMedium,
            color = tint,
        )
    }
}

@Composable
private fun typeTint(type: TransactionTypeUi): Color = when (type) {
    TransactionTypeUi.Expense -> AppTheme.semanticColors.expense
    TransactionTypeUi.Income -> AppTheme.semanticColors.income
    TransactionTypeUi.Transfer -> AppTheme.semanticColors.transfer
}

@Composable
private fun CategoryGridSection(
    categories: List<Category>,
    selected: Category?,
    onSelect: (Category) -> Unit,
    onAllClick: () -> Unit,
    onMoreClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.transaction_creation_category_label),
                style = AppTheme.typography.labelLarge,
                color = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.transaction_creation_category_all),
                style = AppTheme.typography.labelMedium,
                color = AppTheme.materialColors.primary,
                modifier = Modifier.clickable(onClick = onAllClick),
            )
        }
        Spacer(Modifier.height(8.dp))
        val preview = categories.take(CATEGORY_PREVIEW_SIZE)
        // 4 columns, rows of 4; last cell is "More".
        val rows = (preview + listOf<Category?>(null)).chunked(4)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { cat ->
                        Box(modifier = Modifier.weight(1f)) {
                            if (cat != null) {
                                CategoryTile(
                                    category = cat,
                                    selected = cat.id == selected?.id,
                                    onClick = { onSelect(cat) },
                                )
                            } else {
                                MoreTile(onClick = onMoreClick)
                            }
                        }
                    }
                    // Pad row if last row is shorter than 4 columns.
                    if (row.size < 4) {
                        repeat(4 - row.size) {
                            Box(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) AppTheme.materialColors.secondaryContainer else Color.Transparent
    val labelColor =
        if (selected) AppTheme.materialColors.onSecondaryContainer else AppTheme.materialColors.onSurface
    val visual = SurferCategoryPalette.visualFor(
        id = category.id.value,
        iconKey = category.iconKey,
        hue = category.hue,
        systemKind = category.systemKind?.name,
    )
    val tint = visual.tint
    val icon: ImageVector = visual.icon
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            SurferCategoryBubble(icon = icon, tint = tint, size = 44.dp)
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(AppTheme.materialColors.primary)
                        .border(2.dp, AppTheme.materialColors.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = SurferIcons.Check,
                        contentDescription = null,
                        tint = AppTheme.materialColors.onPrimary,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
        Text(
            text = category.name,
            style = AppTheme.typography.labelMedium,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun MoreTile(onClick: () -> Unit) {
    val outline = AppTheme.materialColors.outline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(1.5.dp, outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = SurferIcons.Add,
                contentDescription = null,
                tint = AppTheme.materialColors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = stringResource(Res.string.transaction_creation_category_more),
            style = AppTheme.typography.labelMedium,
            color = AppTheme.materialColors.primary,
            textAlign = TextAlign.Center,
        )
    }
}

private fun currencySymbol(code: CurrencyCode?): String {
    val value = code?.value ?: return "$"
    return when (value) {
        "EUR" -> "€"
        "GBP" -> "£"
        "PLN" -> "zł"
        "JPY" -> "¥"
        "RUB" -> "₽"
        else -> "$"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferAccountsBlock(
    state: TransactionCreationState.Content,
    fromAmountState: TextFieldState,
    toAmountState: TextFieldState,
    onEvent: (TransactionCreationEvent) -> Unit,
) {
    val crossCurrency = state.crossCurrency
    val fromSymbol = currencySymbol(state.fromAccount?.currencyCode)
    val toSymbol = currencySymbol(state.toAccount?.currencyCode)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TransferLegCard(
            label = stringResource(Res.string.transaction_creation_from_label),
            currencySymbol = fromSymbol,
            amountText = fromAmountState.text.toString(),
            amountEditable = true,
            amountState = fromAmountState,
            account = state.fromAccount,
            accountPlaceholder = stringResource(Res.string.transaction_creation_from_account),
            onAccountClick = { onEvent(TransactionCreationEvent.OnOpenFromAccountChooser) },
        )

        state.amountError?.let { error ->
            AmountErrorText(error, modifier = Modifier.padding(start = 4.dp))
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AppTheme.materialColors.primary)
                    .clickable { onEvent(TransactionCreationEvent.OnSwapAccountsClick) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = SurferIcons.SwapHoriz,
                    contentDescription = stringResource(Res.string.transaction_creation_swap_content_description),
                    tint = AppTheme.materialColors.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        TransferLegCard(
            label = stringResource(Res.string.transaction_creation_to_label),
            currencySymbol = toSymbol,
            amountText = if (crossCurrency) toAmountState.text.toString() else fromAmountState.text.toString(),
            amountEditable = crossCurrency,
            amountState = if (crossCurrency) toAmountState else null,
            account = state.toAccount,
            accountPlaceholder = stringResource(Res.string.transaction_creation_to_account),
            onAccountClick = { onEvent(TransactionCreationEvent.OnOpenToAccountChooser) },
        )

        state.toAmountError?.let { error ->
            AmountErrorText(error, modifier = Modifier.padding(start = 4.dp))
        }

        if (crossCurrency) {
            val from = state.amount.toDoubleOrNull()?.takeIf { it > 0 }
            val to = state.toAmount.toDoubleOrNull()?.takeIf { it > 0 }
            if (from != null && to != null) {
                val rate = to / from
                Text(
                    text = stringResource(
                        Res.string.transaction_creation_rate_hint,
                        fromSymbol,
                        formatRate(rate),
                        toSymbol,
                    ),
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferLegCard(
    label: String,
    currencySymbol: String,
    amountText: String,
    amountEditable: Boolean,
    amountState: TextFieldState?,
    account: Account?,
    accountPlaceholder: String,
    onAccountClick: () -> Unit,
) {
    val shape = AppTheme.shapes.small
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppTheme.materialColors.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = AppTheme.typography.labelMedium,
                color = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = currencySymbol,
                style = AppTheme.typography.headlineMedium,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            if (amountEditable && amountState != null) {
                BasicTextField(
                    state = amountState,
                    inputTransformation = AmountInputTransformation,
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    textStyle = LocalTextStyle.current.merge(AppTheme.typography.headlineLarge).copy(
                        color = AppTheme.materialColors.onSurface,
                        textAlign = TextAlign.End,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = SolidColor(AppTheme.materialColors.primary),
                )
            } else {
                Text(
                    text = amountText.ifEmpty { stringResource(Res.string.transaction_creation_amount_placeholder) },
                    style = AppTheme.typography.headlineLarge,
                    color = AppTheme.materialColors.onSurface,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        SurferPickerRow(
            label = accountPlaceholder,
            value = account?.let { acc ->
                val balance = MoneyFormatter.format(acc.balance, acc.currencyCode)
                "${acc.name} · $balance"
            } ?: stringResource(Res.string.transaction_creation_account_empty),
            icon = SurferIcons.CreditCard,
            onClick = onAccountClick,
        )
    }
}

private const val RATE_DECIMAL_SCALE = 10_000.0

private fun formatRate(rate: Double): String {
    val rounded = (rate * RATE_DECIMAL_SCALE).toLong() / RATE_DECIMAL_SCALE
    val asLong = rounded.toLong()
    return if (rounded == asLong.toDouble()) {
        asLong.toString()
    } else {
        rounded.toString()
    }
}

@Preview
@Composable
private fun TransactionCreationFilledPreview() {
    AppTheme {
        TransactionCreationContent(
            state = TransactionCreationState.Content(
                amount = "48.20",
                note = "Lidl — weekly shop",
                type = TransactionTypeUi.Expense,
                accounts = PreviewAccounts,
                categories = PreviewCategories,
                selectedAccount = PreviewAccounts.first(),
                selectedCategory = PreviewCategories.first(),
                isEditMode = false,
                editingTransactionId = null,
                timestamp = 0L,
                categoryUsageCounts = emptyMap(),
                displayCategories = PreviewCategories,
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun TransactionCreationTransferPreview() {
    AppTheme {
        TransactionCreationContent(
            state = TransactionCreationState.Content(
                amount = "48.20",
                note = "Lidl — weekly shop",
                type = TransactionTypeUi.Transfer,
                accounts = PreviewAccounts,
                categories = PreviewCategories,
                selectedAccount = PreviewAccounts.first(),
                selectedCategory = PreviewCategories.first(),
                isEditMode = false,
                editingTransactionId = null,
                timestamp = 0L,
                categoryUsageCounts = emptyMap(),
                displayCategories = PreviewCategories,
                fromAccount = PreviewAccounts[0],
                toAccount = PreviewAccounts[1],
            ),
            onEvent = {},
        )
    }
}

@Preview
@Composable
private fun TransactionCreationTransferCrossCurrencyPreview() {
    AppTheme {
        val from = PreviewAccounts[0]
        val to = PreviewAccounts[1].copy(
            name = "Travel USD",
            currencyCode = com.georgeci.moneysurfer.domain.primitives.CurrencyCode("USD"),
        )
        TransactionCreationContent(
            state = TransactionCreationState.Content(
                amount = "250",
                toAmount = "271.83",
                note = "Lidl — weekly shop",
                type = TransactionTypeUi.Transfer,
                accounts = listOf(from, to),
                categories = PreviewCategories,
                selectedAccount = from,
                selectedCategory = PreviewCategories.first(),
                isEditMode = false,
                editingTransactionId = null,
                timestamp = 0L,
                categoryUsageCounts = emptyMap(),
                displayCategories = PreviewCategories,
                fromAccount = from,
                toAccount = to,
            ),
            onEvent = {},
        )
    }
}
