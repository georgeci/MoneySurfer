package com.georgeci.moneysurfer.feature.account.creation

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.model.AccountExtraDetailKey
import com.georgeci.moneysurfer.domain.model.AccountExtraDetails
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.primitives.AccountType
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.feature.account.extraDetailLabel
import com.georgeci.moneysurfer.feature.account.generated.resources.Res
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_add_another_label
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_balance_error_negative
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_balance_helper
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_balance_label
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_cancel
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_currency_label
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_custom_field_add
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_custom_field_dialog_title
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_custom_field_name_label
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_extra_details_label
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_extra_details_optional
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_field_custom
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_first_run_subtitle
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_first_run_title
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_name_error_required
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_name_label
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_remove
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_remove_content_description
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_save
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_title
import com.georgeci.moneysurfer.feature.account.generated.resources.account_creation_type_label
import com.georgeci.moneysurfer.feature.account.isMonospaceExtraDetail
import com.georgeci.moneysurfer.feature.account.labelRes
import com.georgeci.moneysurfer.uikit.components.base.SurferSegmentedControl
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarButtonAction
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AccountCreationScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDashboard: () -> Unit = {},
    accountId: com.georgeci.moneysurfer.domain.primitives.AccountId? = null,
    firstRun: Boolean = false,
    initialType: AccountType = AccountType.SAVINGS,
    viewModel: AccountCreationViewModel = koinViewModel(
        key = accountId?.value.orEmpty(),
    ) { org.koin.core.parameter.parametersOf(accountId, firstRun, initialType) },
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            AccountCreationEffect.NavigateBack -> onNavigateBack()
            AccountCreationEffect.NavigateToDashboard -> onNavigateToDashboard()
        }
    }

    when (val current = state) {
        is AccountCreationState.Loading -> AccountCreationLoading(
            firstRun = firstRun,
            onEvent = viewModel::onEvent,
        )
        is AccountCreationState.Content -> AccountCreationContent(
            state = current,
            firstRun = firstRun,
            onEvent = viewModel::onEvent,
        )
    }
}

@Composable
private fun AccountCreationLoading(
    firstRun: Boolean,
    onEvent: (AccountCreationEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(accountCreationTitle(firstRun)),
                // No way back out of the first-launch step — there is nowhere to go yet.
                onBack = if (firstRun) null else ({ onEvent(AccountCreationEvent.OnBackClick) }),
            )
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
private fun AccountCreationContent(
    state: AccountCreationState.Content,
    firstRun: Boolean,
    onEvent: (AccountCreationEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(accountCreationTitle(firstRun)),
                onBack = if (firstRun) null else ({ onEvent(AccountCreationEvent.OnBackClick) }),
                actions = {
                    SurferToolbarButtonAction(
                        icon = SurferIcons.Check,
                        text = stringResource(Res.string.account_creation_save),
                        onClick = { onEvent(AccountCreationEvent.OnSaveClick) },
                        enabled = state.canSave,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppTheme.spacing.large)
                .padding(top = AppTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.large),
        ) {
            if (firstRun) {
                Text(
                    text = stringResource(Res.string.account_creation_first_run_subtitle),
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
            }

            TypePicker(
                selected = state.type,
                onSelect = { onEvent(AccountCreationEvent.OnTypeChanged(it)) },
            )

            OutlinedTextField(
                value = state.name,
                onValueChange = { onEvent(AccountCreationEvent.OnNameChanged(it)) },
                label = { Text(stringResource(Res.string.account_creation_name_label)) },
                singleLine = true,
                isError = state.nameMissing,
                supportingText = if (state.nameMissing) {
                    { Text(stringResource(Res.string.account_creation_name_error_required)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )

            if (!state.isEditMode) {
                if (state.currencies.isNotEmpty()) {
                    CurrencyPicker(
                        currencies = state.currencies,
                        selected = state.currency,
                        onSelect = { onEvent(AccountCreationEvent.OnCurrencyChanged(it)) },
                    )
                }

                val balanceError = state.balanceError
                OutlinedTextField(
                    value = state.balance,
                    onValueChange = { onEvent(AccountCreationEvent.OnBalanceChanged(it)) },
                    label = { Text(stringResource(Res.string.account_creation_balance_label)) },
                    prefix = { Text(state.currencySymbol) },
                    supportingText = {
                        Text(
                            if (balanceError == null) {
                                stringResource(Res.string.account_creation_balance_helper)
                            } else {
                                stringResource(balanceError.messageRes())
                            },
                        )
                    },
                    isError = balanceError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.extraDetailsEnabled) {
                ExtraDetailsSection(
                    addedFields = state.extraFields,
                    availableKeys = state.availableExtraFieldKeys,
                    canAddField = state.canAddExtraField,
                    onValueChanged = { key, value ->
                        onEvent(AccountCreationEvent.OnExtraFieldValueChanged(key, value))
                    },
                    onAdd = { onEvent(AccountCreationEvent.OnAddExtraField(it)) },
                    onAddCustom = { onEvent(AccountCreationEvent.OnAddCustomExtraField(it)) },
                    onRemove = { onEvent(AccountCreationEvent.OnRemoveExtraField(it)) },
                )
            }

            Spacer(Modifier.height(padding.calculateBottomPadding() + AppTheme.spacing.large))
        }
    }
}

@Composable
private fun TypePicker(
    selected: AccountType,
    onSelect: (AccountType) -> Unit,
) {
    SectionLabel(stringResource(Res.string.account_creation_type_label))
    SurferSegmentedControl(
        options = listOf(AccountType.CASH, AccountType.BANK, AccountType.CARD, AccountType.SAVINGS),
        selected = selected,
        label = { type -> stringResource(type.labelRes()) },
        onSelect = onSelect,
    )
}

@Composable
private fun CurrencyPicker(
    currencies: List<Currency>,
    selected: CurrencyCode,
    onSelect: (CurrencyCode) -> Unit,
) {
    val resolved = currencies.firstOrNull { it.code == selected } ?: currencies.first()
    SectionLabel(stringResource(Res.string.account_creation_currency_label))
    SurferSegmentedControl(
        options = currencies,
        selected = resolved,
        label = { "${it.code.value} ${it.symbol}" },
        onSelect = { onSelect(it.code) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExtraDetailsSection(
    addedFields: List<AccountExtraField>,
    availableKeys: List<AccountExtraDetailKey>,
    canAddField: Boolean,
    onValueChanged: (String, String) -> Unit,
    onAdd: (AccountExtraDetailKey) -> Unit,
    onAddCustom: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var namingCustomField by remember { mutableStateOf(false) }

    if (namingCustomField) {
        CustomFieldNameDialog(
            onDismiss = { namingCustomField = false },
            onConfirm = { name ->
                namingCustomField = false
                onAddCustom(name)
            },
        )
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = AppTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.account_creation_extra_details_label),
                style = AppTheme.typography.labelLarge,
                color = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(Res.string.account_creation_extra_details_optional),
                style = AppTheme.typography.labelSmall,
                color = AppTheme.materialColors.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.default)) {
            addedFields.forEach { field ->
                ExtraFieldItem(
                    field = field,
                    onValueChanged = { onValueChanged(field.key, it) },
                    onRemove = { onRemove(field.key) },
                )
            }
        }

        if (canAddField) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(Res.string.account_creation_add_another_label),
                style = AppTheme.typography.labelMedium,
                color = AppTheme.materialColors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = AppTheme.spacing.small),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
            ) {
                availableKeys.forEach { key ->
                    AddFieldChip(
                        label = stringResource(key.labelRes()),
                        onClick = { onAdd(key) },
                    )
                }
                AddFieldChip(
                    label = stringResource(Res.string.account_creation_field_custom),
                    onClick = { namingCustomField = true },
                )
            }
        }
    }
}

/**
 * Names a "Custom field…" before it is added. Confirming with a blank name is blocked here; a
 * name that duplicates a field already on the form is dropped by the ViewModel, which is the only
 * place that knows the current rows.
 */
@Composable
private fun CustomFieldNameDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.account_creation_custom_field_dialog_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= AccountExtraDetails.MAX_KEY_LENGTH) name = it },
                label = { Text(stringResource(Res.string.account_creation_custom_field_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(Res.string.account_creation_custom_field_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.account_creation_cancel))
            }
        },
        containerColor = AppTheme.materialColors.surface,
    )
}

@Composable
private fun ExtraFieldItem(
    field: AccountExtraField,
    onValueChanged: (String) -> Unit,
    onRemove: () -> Unit,
) {
    val label = extraDetailLabel(field.key)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onRemove,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = AppTheme.spacing.small,
                    vertical = 0.dp,
                ),
            ) {
                Icon(
                    imageVector = SurferIcons.Close,
                    contentDescription = stringResource(
                        Res.string.account_creation_remove_content_description,
                        label,
                    ),
                    tint = AppTheme.materialColors.primary,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(Res.string.account_creation_remove),
                    style = AppTheme.typography.labelMedium,
                    color = AppTheme.materialColors.primary,
                )
            }
        }
        OutlinedTextField(
            value = field.value,
            onValueChange = onValueChanged,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = !field.isMultiline,
            minLines = if (field.isMultiline) 3 else 1,
            maxLines = if (field.isMultiline) 5 else 1,
            textStyle = if (isMonospaceExtraDetail(field.key)) {
                AppTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
            } else {
                AppTheme.typography.bodyLarge
            },
        )
    }
}

@Composable
private fun AddFieldChip(
    label: String,
    onClick: () -> Unit,
) {
    val outline = AppTheme.materialColors.outline
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = SurferIcons.Add,
            contentDescription = null,
            tint = AppTheme.materialColors.onSurface,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = AppTheme.typography.labelMedium,
            color = AppTheme.materialColors.onSurface,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = AppTheme.typography.labelLarge,
        color = AppTheme.materialColors.onSurfaceVariant,
        modifier = Modifier.padding(bottom = AppTheme.spacing.small),
    )
}

private val AccountExtraField.isMultiline: Boolean
    get() = wellKnownKey == AccountExtraDetailKey.DESCRIPTION

private fun InitialBalanceError.messageRes(): StringResource = when (this) {
    InitialBalanceError.NEGATIVE_NOT_ALLOWED -> Res.string.account_creation_balance_error_negative
}

/** First launch gets a welcoming title; every other entry keeps the plain screen title. */
private fun accountCreationTitle(firstRun: Boolean): StringResource =
    if (firstRun) Res.string.account_creation_first_run_title else Res.string.account_creation_title

@Preview
@Composable
private fun AccountCreationScreenPreview() {
    AppTheme {
        AccountCreationContent(
            state = AccountCreationState.Content(
                name = "Emergency fund",
                balance = "2,480.00",
                type = AccountType.BANK,
                currency = CurrencyCode("EUR"),
                currencies = listOf(
                    Currency(CurrencyCode("EUR"), symbol = "€", displayName = "Euro"),
                    Currency(CurrencyCode("USD"), symbol = "$", displayName = "US Dollar"),
                    Currency(CurrencyCode("GBP"), symbol = "£", displayName = "British Pound"),
                    Currency(CurrencyCode("PLN"), symbol = "zł", displayName = "Polish Zloty"),
                ),
                extraFields = listOf(
                    AccountExtraField(
                        key = AccountExtraDetailKey.IBAN.name,
                        value = "PL61 1090 1014 0000 0712 1981 2874",
                    ),
                    AccountExtraField(
                        key = AccountExtraDetailKey.DESCRIPTION.name,
                        value = "Joint emergency reserve — 6 months of expenses.",
                    ),
                    AccountExtraField(key = "Broker code", value = "MS-4417"),
                ),
                editingAccountId = null,
            ),
            firstRun = false,
            onEvent = {},
        )
    }
}
