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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsGroup
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRow
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsSwitch
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsValuePill
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.utils.HandleSideEffect
import moneysurfer.feature.settings.generated.resources.Res
import moneysurfer.feature.settings.generated.resources.settings_preferences_auto_categorize_supporting
import moneysurfer.feature.settings.generated.resources.settings_preferences_auto_categorize_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_behavior_footnote
import moneysurfer.feature.settings.generated.resources.settings_preferences_currency_pill
import moneysurfer.feature.settings.generated.resources.settings_preferences_currency_supporting
import moneysurfer.feature.settings.generated.resources.settings_preferences_currency_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_default_txn_pill
import moneysurfer.feature.settings.generated.resources.settings_preferences_default_txn_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_hide_supporting
import moneysurfer.feature.settings.generated.resources.settings_preferences_hide_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_hour_pill
import moneysurfer.feature.settings.generated.resources.settings_preferences_hour_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_language_pill
import moneysurfer.feature.settings.generated.resources.settings_preferences_language_supporting
import moneysurfer.feature.settings.generated.resources.settings_preferences_language_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_locale_footnote
import moneysurfer.feature.settings.generated.resources.settings_preferences_number_format_pill
import moneysurfer.feature.settings.generated.resources.settings_preferences_number_format_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_region_pill
import moneysurfer.feature.settings.generated.resources.settings_preferences_region_supporting
import moneysurfer.feature.settings.generated.resources.settings_preferences_region_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_round_up_supporting
import moneysurfer.feature.settings.generated.resources.settings_preferences_round_up_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_section_behavior
import moneysurfer.feature.settings.generated.resources.settings_preferences_section_calendar
import moneysurfer.feature.settings.generated.resources.settings_preferences_section_locale
import moneysurfer.feature.settings.generated.resources.settings_preferences_section_money
import moneysurfer.feature.settings.generated.resources.settings_preferences_title
import moneysurfer.feature.settings.generated.resources.settings_preferences_week_pill
import moneysurfer.feature.settings.generated.resources.settings_preferences_week_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PreferencesScreen(
    onNavigateBack: () -> Unit,
    viewModel: PreferencesViewModel = koinViewModel(),
) {
    val state by viewModel.collectAsStateWithLifecycle()

    viewModel.HandleSideEffect { effect ->
        when (effect) {
            PreferencesEffect.NavigateBack -> onNavigateBack()
            PreferencesEffect.OpenLanguagePicker,
            PreferencesEffect.OpenRegionPicker,
            PreferencesEffect.OpenCurrencyPicker,
            PreferencesEffect.OpenNumberFormatPicker,
            PreferencesEffect.OpenWeekStartPicker,
            PreferencesEffect.OpenHourFormatPicker,
            PreferencesEffect.OpenDefaultTxnPicker,
            -> Unit
        }
    }

    PreferencesContent(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun PreferencesContent(
    state: PreferencesState,
    onEvent: (PreferencesEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier.surferSafeInsets(),
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
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(rememberScrollState()),
        ) {
            SurferSettingsGroup(
                title = stringResource(Res.string.settings_preferences_section_locale),
                footnote = stringResource(Res.string.settings_preferences_locale_footnote),
            ) {
                SurferSettingsRow(
                    icon = SurferIcons.Globe,
                    title = stringResource(Res.string.settings_preferences_language_title),
                    supportingText = stringResource(Res.string.settings_preferences_language_supporting),
                    onClick = { onEvent(PreferencesEvent.OnLanguageClick) },
                    trailing = {
                        SurferSettingsValuePill(stringResource(Res.string.settings_preferences_language_pill))
                    },
                )
                SurferSettingsRow(
                    icon = SurferIcons.Sparkle,
                    title = stringResource(Res.string.settings_preferences_region_title),
                    supportingText = stringResource(Res.string.settings_preferences_region_supporting),
                    onClick = { onEvent(PreferencesEvent.OnRegionClick) },
                    trailing = {
                        SurferSettingsValuePill(stringResource(Res.string.settings_preferences_region_pill))
                    },
                )
            }

            SurferSettingsGroup(title = stringResource(Res.string.settings_preferences_section_money)) {
                SurferSettingsRow(
                    icon = SurferIcons.Wallet,
                    title = stringResource(Res.string.settings_preferences_currency_title),
                    supportingText = stringResource(Res.string.settings_preferences_currency_supporting),
                    onClick = { onEvent(PreferencesEvent.OnCurrencyClick) },
                    trailing = {
                        SurferSettingsValuePill(stringResource(Res.string.settings_preferences_currency_pill))
                    },
                )
                SurferSettingsRow(
                    icon = SurferIcons.Code,
                    title = stringResource(Res.string.settings_preferences_number_format_title),
                    onClick = { onEvent(PreferencesEvent.OnNumberFormatClick) },
                    trailing = {
                        SurferSettingsValuePill(stringResource(Res.string.settings_preferences_number_format_pill))
                    },
                )
                SurferSettingsRow(
                    icon = SurferIcons.Visibility,
                    title = stringResource(Res.string.settings_preferences_hide_title),
                    supportingText = stringResource(Res.string.settings_preferences_hide_supporting),
                    onClick = { onEvent(PreferencesEvent.OnHideAmountsToggle(!state.hideAmounts)) },
                    trailing = {
                        SurferSettingsSwitch(
                            checked = state.hideAmounts,
                            onCheckedChange = { onEvent(PreferencesEvent.OnHideAmountsToggle(it)) },
                        )
                    },
                )
            }

            SurferSettingsGroup(title = stringResource(Res.string.settings_preferences_section_calendar)) {
                SurferSettingsRow(
                    icon = SurferIcons.Calendar,
                    title = stringResource(Res.string.settings_preferences_week_title),
                    onClick = { onEvent(PreferencesEvent.OnWeekStartClick) },
                    trailing = {
                        SurferSettingsValuePill(stringResource(Res.string.settings_preferences_week_pill))
                    },
                )
                SurferSettingsRow(
                    icon = SurferIcons.Clock,
                    title = stringResource(Res.string.settings_preferences_hour_title),
                    onClick = { onEvent(PreferencesEvent.OnHourFormatClick) },
                    trailing = {
                        SurferSettingsValuePill(stringResource(Res.string.settings_preferences_hour_pill))
                    },
                )
            }

            SurferSettingsGroup(
                title = stringResource(Res.string.settings_preferences_section_behavior),
                footnote = stringResource(Res.string.settings_preferences_behavior_footnote),
            ) {
                SurferSettingsRow(
                    icon = SurferIcons.Add,
                    title = stringResource(Res.string.settings_preferences_default_txn_title),
                    onClick = { onEvent(PreferencesEvent.OnDefaultTxnClick) },
                    trailing = {
                        SurferSettingsValuePill(stringResource(Res.string.settings_preferences_default_txn_pill))
                    },
                )
                SurferSettingsRow(
                    icon = SurferIcons.Sparkle,
                    title = stringResource(Res.string.settings_preferences_auto_categorize_title),
                    supportingText = stringResource(Res.string.settings_preferences_auto_categorize_supporting),
                    onClick = { onEvent(PreferencesEvent.OnAutoCategorizeToggle(!state.autoCategorize)) },
                    trailing = {
                        SurferSettingsSwitch(
                            checked = state.autoCategorize,
                            onCheckedChange = { onEvent(PreferencesEvent.OnAutoCategorizeToggle(it)) },
                        )
                    },
                )
                SurferSettingsRow(
                    icon = SurferIcons.Notifications,
                    title = stringResource(Res.string.settings_preferences_round_up_title),
                    supportingText = stringResource(Res.string.settings_preferences_round_up_supporting),
                    onClick = { onEvent(PreferencesEvent.OnRoundUpToggle(!state.roundUp)) },
                    trailing = {
                        SurferSettingsSwitch(
                            checked = state.roundUp,
                            onCheckedChange = { onEvent(PreferencesEvent.OnRoundUpToggle(it)) },
                        )
                    },
                )
            }
            Spacer(Modifier.height(padding.calculateBottomPadding() + 24.dp))
        }
    }
}
