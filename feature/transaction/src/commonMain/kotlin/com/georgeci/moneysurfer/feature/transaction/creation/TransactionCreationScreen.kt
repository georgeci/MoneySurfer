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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.model.Account
import com.georgeci.moneysurfer.domain.model.Category
import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CategoryId
import com.georgeci.moneysurfer.domain.primitives.CategoryType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.primitives.Money
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.domain.primitives.WorkspaceId
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
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.AmountInputTransformation
import com.georgeci.moneysurfer.utils.HandleSideEffect
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import moneysurfer.feature.transaction.generated.resources.Res
import moneysurfer.feature.transaction.generated.resources.transaction_creation_account_empty
import moneysurfer.feature.transaction.generated.resources.transaction_creation_account_label
import moneysurfer.feature.transaction.generated.resources.transaction_creation_amount_placeholder
import moneysurfer.feature.transaction.generated.resources.transaction_creation_category_all
import moneysurfer.feature.transaction.generated.resources.transaction_creation_category_label
import moneysurfer.feature.transaction.generated.resources.transaction_creation_category_more
import moneysurfer.feature.transaction.generated.resources.transaction_creation_close_content_description
import moneysurfer.feature.transaction.generated.resources.transaction_creation_datetime_label
import moneysurfer.feature.transaction.generated.resources.transaction_creation_datetime_placeholder
import moneysurfer.feature.transaction.generated.resources.transaction_creation_expense
import moneysurfer.feature.transaction.generated.resources.transaction_creation_income
import moneysurfer.feature.transaction.generated.resources.transaction_creation_note_label
import moneysurfer.feature.transaction.generated.resources.transaction_creation_save
import moneysurfer.feature.transaction.generated.resources.transaction_creation_title_create
import moneysurfer.feature.transaction.generated.resources.transaction_creation_title_edit
import moneysurfer.feature.transaction.generated.resources.transaction_creation_today
import moneysurfer.feature.transaction.generated.resources.transaction_creation_transfer
import moneysurfer.feature.transaction.generated.resources.transaction_creation_update
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
fun TransactionCreationScreen(
    transactionId: TransactionId? = null,
    accountId: AccountId? = null,
    onNavigateBack: () -> Unit,
    onNavigateToCategoryChooser: (CategoryId?, CategoryType) -> Unit,
    onNavigateToCategoryCreation: () -> Unit,
    onNavigateToAccountChooser: (AccountId?) -> Unit,
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
        modifier = Modifier.surferSafeInsets(),
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
            val typeOptions = remember {
                if (ShowTransferType) {
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

            AmountHero(
                type = state.type,
                currencySymbol = currencySymbol(state.selectedAccount?.currencyCode),
                amountState = amountState,
            )

            Spacer(Modifier.height(AppTheme.spacing.default))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = padding.calculateBottomPadding() + AppTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium),
            ) {
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

                OutlinedTextField(
                    value = state.note,
                    onValueChange = { onEvent(TransactionCreationEvent.OnNoteChanged(it)) },
                    label = { Text(stringResource(Res.string.transaction_creation_note_label)) },
                    shape = AppTheme.shapes.extraSmall,
                    modifier = Modifier.fillMaxWidth(),
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

private const val ShowTransferType = false
private const val CATEGORY_PREVIEW_SIZE = 7

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AmountHero(
    type: TransactionTypeUi,
    currencySymbol: String,
    amountState: TextFieldState,
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
    }
}

@Composable
private fun TypePill(type: TransactionTypeUi) {
    val tint = typeTint(type)
    val labelRes = when (type) {
        TransactionTypeUi.Expense -> Res.string.transaction_creation_expense
        TransactionTypeUi.Income -> Res.string.transaction_creation_income
        TransactionTypeUi.Transfer -> Res.string.transaction_creation_transfer
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(tint.copy(alpha = 0.18f))
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

private fun typeTint(type: TransactionTypeUi): Color = when (type) {
    TransactionTypeUi.Expense -> Color(0xFFB54744)
    TransactionTypeUi.Income -> Color(0xFF2F8E6E)
    TransactionTypeUi.Transfer -> Color(0xFF2E5AA8)
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
    val tint = SurferCategoryPalette.tintFor(category.id.value)
    val icon: ImageVector = SurferCategoryPalette.iconFor(category.id.value)
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

@Composable
private fun DateTimeField(
    timestamp: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldShape = AppTheme.shapes.extraSmall
    Row(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(fieldShape)
            .border(1.dp, AppTheme.materialColors.outline, fieldShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = SurferIcons.Event,
            contentDescription = null,
            tint = AppTheme.materialColors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.transaction_creation_datetime_label),
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
            Text(
                text = timestamp?.let(::formatShortDate)
                    ?: stringResource(Res.string.transaction_creation_datetime_placeholder),
                style = AppTheme.typography.bodyLarge,
                color = AppTheme.materialColors.onSurface,
            )
        }
        Icon(
            imageVector = SurferIcons.DropDown,
            contentDescription = null,
            tint = AppTheme.materialColors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionDatePickerDialog(
    initialTimestamp: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val now = remember { Clock.System.now().toEpochMilliseconds() }
    val nowYear = remember(now) {
        Instant.fromEpochMilliseconds(now)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .year
    }
    val notFuture = remember(now, nowYear) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= now
            override fun isSelectableYear(year: Int): Boolean = year <= nowYear
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initialTimestamp,
        selectableDates = notFuture,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { state.selectedDateMillis?.let(onConfirm) },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(Res.string.transaction_creation_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.transaction_creation_close_content_description))
            }
        },
    ) {
        DatePicker(state = state)
    }
}

private fun formatShortDate(timestamp: Long): String {
    val instant = Instant.fromEpochMilliseconds(timestamp)
    val ld = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val month = ld.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)
    val day = ld.day.toString().padStart(2, '0')
    return "$day $month ${ld.year}"
}

private fun currencySymbol(code: CurrencyCode?): String {
    val value = code?.value ?: return "$"
    return when (value) {
        "EUR" -> "€"
        "GBP" -> "£"
        "PLN" -> "zł"
        "JPY" -> "¥"
        else -> "$"
    }
}

private val previewAccounts = listOf(
    Account(
        id = AccountId("preview-acc-1"),
        workspaceId = WorkspaceId("preview-ws-1"),
        name = "Everyday",
        type = AccountType.CARD,
        currencyCode = CurrencyCode("EUR"),
        balance = Money.fromMajor(2480),
    ),
    Account(
        id = AccountId("preview-acc-2"),
        workspaceId = WorkspaceId("preview-ws-1"),
        name = "Savings",
        type = AccountType.SAVINGS,
        currencyCode = CurrencyCode("EUR"),
        balance = Money.fromMajor(8300),
    ),
)

private val previewCategories = listOf(
    Category(CategoryId("preview-cat-1"), WorkspaceId("preview-ws-1"), "Groceries", CategoryType.EXPENSE, null, kotlin.time.Instant.fromEpochMilliseconds(0)),
    Category(CategoryId("preview-cat-2"), WorkspaceId("preview-ws-1"), "Transport", CategoryType.EXPENSE, null, kotlin.time.Instant.fromEpochMilliseconds(0)),
    Category(CategoryId("preview-cat-3"), WorkspaceId("preview-ws-1"), "Dining", CategoryType.EXPENSE, null, kotlin.time.Instant.fromEpochMilliseconds(0)),
    Category(CategoryId("preview-cat-4"), WorkspaceId("preview-ws-1"), "Home", CategoryType.EXPENSE, null, kotlin.time.Instant.fromEpochMilliseconds(0)),
    Category(CategoryId("preview-cat-5"), WorkspaceId("preview-ws-1"), "Leisure", CategoryType.EXPENSE, null, kotlin.time.Instant.fromEpochMilliseconds(0)),
    Category(CategoryId("preview-cat-6"), WorkspaceId("preview-ws-1"), "Health", CategoryType.EXPENSE, null, kotlin.time.Instant.fromEpochMilliseconds(0)),
    Category(CategoryId("preview-cat-7"), WorkspaceId("preview-ws-1"), "Utilities", CategoryType.EXPENSE, null, kotlin.time.Instant.fromEpochMilliseconds(0)),
)

@Preview
@Composable
private fun TransactionCreationFilledPreview() {
    AppTheme {
        TransactionCreationContent(
            state = TransactionCreationState.Content(
                amount = "48.20",
                note = "Lidl — weekly shop",
                type = TransactionTypeUi.Expense,
                accounts = previewAccounts,
                categories = previewCategories,
                selectedAccount = previewAccounts.first(),
                selectedCategory = previewCategories.first(),
                isEditMode = false,
                editingTransactionId = null,
                timestamp = 0L,
                categoryUsageCounts = emptyMap(),
                displayCategories = previewCategories,
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
                amount = "",
                note = "",
                type = TransactionTypeUi.Transfer,
                accounts = previewAccounts,
                categories = previewCategories,
                selectedAccount = previewAccounts.first(),
                selectedCategory = previewCategories.first(),
                isEditMode = false,
                editingTransactionId = null,
                timestamp = 0L,
                categoryUsageCounts = emptyMap(),
                displayCategories = previewCategories,
            ),
            onEvent = {},
        )
    }
}
