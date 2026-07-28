package com.georgeci.moneysurfer.feature.settings.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.preferences.AppLanguage
import com.georgeci.moneysurfer.domain.preferences.AppRegion
import com.georgeci.moneysurfer.domain.preferences.DefaultTransactionType
import com.georgeci.moneysurfer.domain.preferences.HourFormat
import com.georgeci.moneysurfer.domain.preferences.NumberFormatStyle
import com.georgeci.moneysurfer.domain.preferences.WeekStart
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.uikit.components.SurferCurrencyBottomSheet
import com.georgeci.moneysurfer.uikit.components.SurferCurrencyOption
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsGroup
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRow
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsSwitch
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsValuePill
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferContentContainer
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.modifier.surferTestTagAsId
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.settings.generated.resources.Res
import moneysurfer.feature.settings.generated.resources.settings_preferences_auto_categorize_supporting
import moneysurfer.feature.settings.generated.resources.settings_preferences_auto_categorize_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_behavior_footnote
import moneysurfer.feature.settings.generated.resources.settings_preferences_currency_search_placeholder
import moneysurfer.feature.settings.generated.resources.settings_preferences_currency_supporting
import moneysurfer.feature.settings.generated.resources.settings_preferences_currency_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_default_txn_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_hide_supporting
import moneysurfer.feature.settings.generated.resources.settings_preferences_hide_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_hour_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_language_supporting
import moneysurfer.feature.settings.generated.resources.settings_preferences_language_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_locale_footnote
import moneysurfer.feature.settings.generated.resources.settings_preferences_number_format_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_region_supporting
import moneysurfer.feature.settings.generated.resources.settings_preferences_region_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_round_up_supporting
import moneysurfer.feature.settings.generated.resources.settings_preferences_round_up_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_section_behavior
import moneysurfer.feature.settings.generated.resources.settings_preferences_section_calendar
import moneysurfer.feature.settings.generated.resources.settings_preferences_section_locale
import moneysurfer.feature.settings.generated.resources.settings_preferences_section_money
import moneysurfer.feature.settings.generated.resources.settings_preferences_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_week_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stable selectors for the Preferences screen — see docs/testing/testing-strategy.md.
 *
 * Rows and options are named after the [PreferencePicker] and the enum entry they store, not after
 * the localized label, so a re-worded row never breaks a flow.
 */
object PreferencesTestTags {
    const val Root = "preferences:root"

    fun row(picker: PreferencePicker): String = "preferences:row:${picker.name.lowercase()}"

    fun option(picker: PreferencePicker, value: String): String =
        "preferences:option:${picker.name.lowercase()}:${value.lowercase()}"

    fun toggle(name: String): String = "preferences:toggle:$name"
}

@Composable
fun PreferencesScreen(
    onNavigateBack: () -> Unit,
    viewModel: PreferencesViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            PreferencesEffect.NavigateBack -> onNavigateBack()
        }
    }

    PreferencesContent(state = state, onEvent = viewModel::onEvent)
}

/**
 * Stateless half of the screen — public so `:composeApp` desktop UI tests can mount it with an
 * injected state, the way [DashboardCustomizeContent][com.georgeci.moneysurfer.feature.dashboard.customize]
 * is mounted. That cover is the point of this screen: the bug being fixed was pills that rendered a
 * fixed string, and only a real render can tell a pill bound to state from one that is not.
 */
@Composable
fun PreferencesContent(
    state: PreferencesState,
    onEvent: (PreferencesEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .surferSafeInsets()
            .testTag(PreferencesTestTags.Root)
            .surferTestTagAsId(),
        containerColor = AppTheme.materialColors.surface,
        topBar = {
            SurferToolbar(
                title = stringResource(Res.string.settings_preferences_title),
                onBack = { onEvent(PreferencesEvent.OnBackClick) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppTheme.materialColors.surface,
                    titleContentColor = AppTheme.materialColors.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .surferContentContainer()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            SurferSettingsGroup(
                title = stringResource(Res.string.settings_preferences_section_locale),
                footnote = stringResource(Res.string.settings_preferences_locale_footnote),
            ) {
                PickerRow(
                    picker = PreferencePicker.Language,
                    icon = SurferIcons.Globe,
                    title = stringResource(Res.string.settings_preferences_language_title),
                    supportingText = stringResource(Res.string.settings_preferences_language_supporting),
                    value = state.language.label(),
                    onEvent = onEvent,
                )
                PickerRow(
                    picker = PreferencePicker.Region,
                    icon = SurferIcons.Sparkle,
                    title = stringResource(Res.string.settings_preferences_region_title),
                    supportingText = stringResource(Res.string.settings_preferences_region_supporting),
                    value = state.region.label(),
                    onEvent = onEvent,
                )
            }

            SurferSettingsGroup(title = stringResource(Res.string.settings_preferences_section_money)) {
                PickerRow(
                    picker = PreferencePicker.Currency,
                    icon = SurferIcons.Wallet,
                    title = stringResource(Res.string.settings_preferences_currency_title),
                    supportingText = stringResource(Res.string.settings_preferences_currency_supporting),
                    value = currencyLabel(state.currency, state.currencies),
                    onEvent = onEvent,
                )
                PickerRow(
                    picker = PreferencePicker.NumberFormat,
                    icon = SurferIcons.Code,
                    title = stringResource(Res.string.settings_preferences_number_format_title),
                    value = state.numberFormat.sample,
                    onEvent = onEvent,
                )
                ToggleRow(
                    name = "hide_amounts",
                    icon = SurferIcons.Visibility,
                    title = stringResource(Res.string.settings_preferences_hide_title),
                    supportingText = stringResource(Res.string.settings_preferences_hide_supporting),
                    checked = state.hideAmounts,
                    onCheckedChange = { onEvent(PreferencesEvent.OnHideAmountsToggle(it)) },
                )
            }

            SurferSettingsGroup(title = stringResource(Res.string.settings_preferences_section_calendar)) {
                PickerRow(
                    picker = PreferencePicker.WeekStart,
                    icon = SurferIcons.Calendar,
                    title = stringResource(Res.string.settings_preferences_week_title),
                    value = state.weekStart.label(),
                    onEvent = onEvent,
                )
                PickerRow(
                    picker = PreferencePicker.HourFormat,
                    icon = SurferIcons.Clock,
                    title = stringResource(Res.string.settings_preferences_hour_title),
                    value = state.hourFormat.label(),
                    onEvent = onEvent,
                )
            }

            SurferSettingsGroup(
                title = stringResource(Res.string.settings_preferences_section_behavior),
                footnote = stringResource(Res.string.settings_preferences_behavior_footnote),
            ) {
                PickerRow(
                    picker = PreferencePicker.DefaultTransactionType,
                    icon = SurferIcons.Add,
                    title = stringResource(Res.string.settings_preferences_default_txn_title),
                    value = state.defaultTransactionType.label(),
                    onEvent = onEvent,
                )
                ToggleRow(
                    name = "auto_categorize",
                    icon = SurferIcons.Sparkle,
                    title = stringResource(Res.string.settings_preferences_auto_categorize_title),
                    supportingText = stringResource(Res.string.settings_preferences_auto_categorize_supporting),
                    checked = state.autoCategorize,
                    onCheckedChange = { onEvent(PreferencesEvent.OnAutoCategorizeToggle(it)) },
                )
                ToggleRow(
                    name = "round_up",
                    icon = SurferIcons.Notifications,
                    title = stringResource(Res.string.settings_preferences_round_up_title),
                    supportingText = stringResource(Res.string.settings_preferences_round_up_supporting),
                    checked = state.roundUp,
                    onCheckedChange = { onEvent(PreferencesEvent.OnRoundUpToggle(it)) },
                )
            }
            Spacer(Modifier.height(padding.calculateBottomPadding() + 24.dp))
        }
    }

    ActivePickerSheet(state = state, onEvent = onEvent)
}

/** Row whose pill shows the stored value and whose tap opens that value's chooser. */
@Composable
private fun PickerRow(
    picker: PreferencePicker,
    icon: ImageVector,
    title: String,
    value: String,
    onEvent: (PreferencesEvent) -> Unit,
    supportingText: String? = null,
) {
    SurferSettingsRow(
        icon = icon,
        title = title,
        supportingText = supportingText,
        onClick = { onEvent(PreferencesEvent.OnPickerOpen(picker)) },
        trailing = { SurferSettingsValuePill(value) },
        modifier = Modifier.testTag(PreferencesTestTags.row(picker)),
    )
}

@Composable
private fun ToggleRow(
    name: String,
    icon: ImageVector,
    title: String,
    supportingText: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SurferSettingsRow(
        icon = icon,
        title = title,
        supportingText = supportingText,
        onClick = { onCheckedChange(!checked) },
        trailing = { SurferSettingsSwitch(checked = checked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.testTag(PreferencesTestTags.toggle(name)),
    )
}

/**
 * The one open chooser, if any. Every sheet is driven from [PreferencesState.activePicker] rather
 * than from a side effect, so it survives a configuration change with the right row still open.
 */
@Composable
private fun ActivePickerSheet(
    state: PreferencesState,
    onEvent: (PreferencesEvent) -> Unit,
) {
    val onDismiss = { onEvent(PreferencesEvent.OnPickerDismiss) }
    when (state.activePicker) {
        null -> Unit

        PreferencePicker.Language -> PreferenceOptionsSheet(
            title = stringResource(Res.string.settings_preferences_language_title),
            options = AppLanguage.entries.map { it.asOption(PreferencePicker.Language, it.label()) },
            selected = state.language,
            onSelect = { onEvent(PreferencesEvent.OnLanguageSelect(it)) },
            onDismiss = onDismiss,
        )

        PreferencePicker.Region -> PreferenceOptionsSheet(
            title = stringResource(Res.string.settings_preferences_region_title),
            options = AppRegion.entries.map { it.asOption(PreferencePicker.Region, it.label()) },
            selected = state.region,
            onSelect = { onEvent(PreferencesEvent.OnRegionSelect(it)) },
            onDismiss = onDismiss,
        )

        PreferencePicker.Currency -> CurrencySheet(
            currencies = state.currencies,
            selected = state.currency,
            onSelect = { onEvent(PreferencesEvent.OnCurrencySelect(it)) },
            onDismiss = onDismiss,
        )

        PreferencePicker.NumberFormat -> PreferenceOptionsSheet(
            title = stringResource(Res.string.settings_preferences_number_format_title),
            options = NumberFormatStyle.entries.map { it.asOption(PreferencePicker.NumberFormat, it.sample) },
            selected = state.numberFormat,
            onSelect = { onEvent(PreferencesEvent.OnNumberFormatSelect(it)) },
            onDismiss = onDismiss,
        )

        PreferencePicker.WeekStart -> PreferenceOptionsSheet(
            title = stringResource(Res.string.settings_preferences_week_title),
            options = WeekStart.entries.map { it.asOption(PreferencePicker.WeekStart, it.label()) },
            selected = state.weekStart,
            onSelect = { onEvent(PreferencesEvent.OnWeekStartSelect(it)) },
            onDismiss = onDismiss,
        )

        PreferencePicker.HourFormat -> PreferenceOptionsSheet(
            title = stringResource(Res.string.settings_preferences_hour_title),
            options = HourFormat.entries.map { it.asOption(PreferencePicker.HourFormat, it.label()) },
            selected = state.hourFormat,
            onSelect = { onEvent(PreferencesEvent.OnHourFormatSelect(it)) },
            onDismiss = onDismiss,
        )

        PreferencePicker.DefaultTransactionType -> PreferenceOptionsSheet(
            title = stringResource(Res.string.settings_preferences_default_txn_title),
            options = DefaultTransactionType.entries.map {
                it.asOption(PreferencePicker.DefaultTransactionType, it.label())
            },
            selected = state.defaultTransactionType,
            onSelect = { onEvent(PreferencesEvent.OnDefaultTransactionTypeSelect(it)) },
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun CurrencySheet(
    currencies: List<Currency>,
    selected: CurrencyCode?,
    onSelect: (CurrencyCode) -> Unit,
    onDismiss: () -> Unit,
) {
    SurferCurrencyBottomSheet(
        title = stringResource(Res.string.settings_preferences_currency_title),
        searchPlaceholder = stringResource(Res.string.settings_preferences_currency_search_placeholder),
        currencies = currencies.map {
            SurferCurrencyOption(code = it.code.value, symbol = it.symbol, name = it.displayName)
        },
        selectedCode = selected?.value,
        onSelect = { onSelect(CurrencyCode(it.code)) },
        onDismiss = onDismiss,
    )
}

private fun <T : Enum<T>> T.asOption(picker: PreferencePicker, label: String): PreferenceOption<T> =
    PreferenceOption(value = this, label = label, tag = PreferencesTestTags.option(picker, name))

@Preview
@Composable
private fun PreferencesScreenPreview() {
    AppTheme {
        PreferencesContent(
            state = PreferencesState(
                region = AppRegion.Poland,
                currency = CurrencyCode("PLN"),
                currencies = listOf(Currency(CurrencyCode("PLN"), symbol = "zł", displayName = "Polish Zloty")),
                numberFormat = NumberFormatStyle.SpaceGroupCommaDecimal,
                hourFormat = HourFormat.TwentyFourHour,
            ),
            onEvent = {},
        )
    }
}
