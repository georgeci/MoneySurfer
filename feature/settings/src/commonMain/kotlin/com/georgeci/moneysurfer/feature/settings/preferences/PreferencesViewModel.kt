package com.georgeci.moneysurfer.feature.settings.preferences

import com.georgeci.moneysurfer.domain.model.Currency
import com.georgeci.moneysurfer.domain.preferences.AppLanguage
import com.georgeci.moneysurfer.domain.preferences.AppRegion
import com.georgeci.moneysurfer.domain.preferences.DefaultTransactionType
import com.georgeci.moneysurfer.domain.preferences.HourFormat
import com.georgeci.moneysurfer.domain.preferences.NumberFormatStyle
import com.georgeci.moneysurfer.domain.preferences.Pref
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.domain.preferences.WeekStart
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode
import com.georgeci.moneysurfer.domain.usecase.GetCurrenciesUseCase
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.KoinViewModel

/**
 * Every row on the Preferences screen is a [UiPreferences] handle: state is a projection of the
 * stored values and a tap writes straight through, exactly as `AppearanceViewModel` does. Nothing
 * is held in the ViewModel that a pref does not already hold — the one exception is
 * [PreferencesState.activePicker], which is which sheet is open and dies with the screen on
 * purpose.
 *
 * The choices are stored, not yet obeyed: no locale, calendar or behaviour code reads these keys
 * today (see the `UiPreferences` KDoc). The screen therefore promises persistence and nothing more.
 */
@KoinViewModel
class PreferencesViewModel(
    private val uiPreferences: UiPreferences,
    getCurrencies: GetCurrenciesUseCase,
) : MviViewModel<PreferencesState, PreferencesEvent, PreferencesEffect>(
    initialState = PreferencesState(),
) {

    init {
        observe(uiPreferences.appLanguage) { copy(language = it) }
        observe(uiPreferences.appRegion) { copy(region = it) }
        observe(uiPreferences.defaultCurrency) { copy(currency = it) }
        observe(uiPreferences.numberFormat) { copy(numberFormat = it) }
        observe(uiPreferences.weekStart) { copy(weekStart = it) }
        observe(uiPreferences.hourFormat) { copy(hourFormat = it) }
        observe(uiPreferences.defaultTransactionType) { copy(defaultTransactionType = it) }
        observe(uiPreferences.hideAmounts) { copy(hideAmounts = it) }
        observe(uiPreferences.autoCategorize) { copy(autoCategorize = it) }
        observe(uiPreferences.roundUpSavings) { copy(roundUp = it) }

        launch {
            getCurrencies()
                .onEach { currencies -> updateState { copy(currencies = currencies) } }
                .collect()
        }
    }

    override fun onEvent(event: PreferencesEvent) {
        when (event) {
            PreferencesEvent.OnBackClick -> postSideEffect(PreferencesEffect.NavigateBack)
            is PreferencesEvent.OnPickerOpen -> updateState { copy(activePicker = event.picker) }
            PreferencesEvent.OnPickerDismiss -> closePicker()

            is PreferencesEvent.OnLanguageSelect -> select(uiPreferences.appLanguage, event.value)
            is PreferencesEvent.OnRegionSelect -> select(uiPreferences.appRegion, event.value)
            is PreferencesEvent.OnCurrencySelect -> select(uiPreferences.defaultCurrency, event.value)
            is PreferencesEvent.OnNumberFormatSelect -> select(uiPreferences.numberFormat, event.value)
            is PreferencesEvent.OnWeekStartSelect -> select(uiPreferences.weekStart, event.value)
            is PreferencesEvent.OnHourFormatSelect -> select(uiPreferences.hourFormat, event.value)
            is PreferencesEvent.OnDefaultTransactionTypeSelect ->
                select(uiPreferences.defaultTransactionType, event.value)

            is PreferencesEvent.OnHideAmountsToggle -> write(uiPreferences.hideAmounts, event.enabled)
            is PreferencesEvent.OnAutoCategorizeToggle -> write(uiPreferences.autoCategorize, event.enabled)
            is PreferencesEvent.OnRoundUpToggle -> write(uiPreferences.roundUpSavings, event.enabled)
        }
    }

    private fun <T> observe(pref: Pref<T>, reduce: PreferencesState.(T) -> PreferencesState) {
        launch {
            pref.flow
                .onEach { value -> updateState { reduce(value) } }
                .collect()
        }
    }

    /**
     * Closes the sheet and writes. The pill is *not* updated here — it re-renders when the pref
     * flow emits, so what the row shows is what the store accepted and nothing else.
     */
    private fun <T> select(pref: Pref<T>, value: T) {
        closePicker()
        write(pref, value)
    }

    private fun <T> write(pref: Pref<T>, value: T) {
        launch { pref.set(value) }
    }

    private fun closePicker() = updateState { copy(activePicker = null) }
}

/**
 * Defaults here mirror the `UiConfigKeys` defaults on purpose: they are what the screen shows for
 * the frame before the first flow emission, and a mismatch would be a value the store never held.
 *
 * [currency] is nullable because it has no such constant — its default is the platform locale's
 * currency, resolved by the config engine — so the row shows a placeholder rather than a guess
 * until the flow arrives.
 */
data class PreferencesState(
    val language: AppLanguage = AppLanguage.DEFAULT,
    val region: AppRegion = AppRegion.DEFAULT,
    val currency: CurrencyCode? = null,
    val currencies: List<Currency> = emptyList(),
    val numberFormat: NumberFormatStyle = NumberFormatStyle.DEFAULT,
    val weekStart: WeekStart = WeekStart.DEFAULT,
    val hourFormat: HourFormat = HourFormat.DEFAULT,
    val defaultTransactionType: DefaultTransactionType = DefaultTransactionType.DEFAULT,
    val hideAmounts: Boolean = false,
    val autoCategorize: Boolean = true,
    val roundUp: Boolean = false,
    val activePicker: PreferencePicker? = null,
) {
    /** The catalogue entry for [currency], or `null` while it loads or for a code we do not stock. */
    val selectedCurrency: Currency? get() = currencies.firstOrNull { it.code == currency }
}

/** Which row's chooser is open. One at a time — every picker is a modal sheet. */
enum class PreferencePicker {
    Language,
    Region,
    Currency,
    NumberFormat,
    WeekStart,
    HourFormat,
    DefaultTransactionType,
}

sealed interface PreferencesEvent {
    data object OnBackClick : PreferencesEvent
    data class OnPickerOpen(val picker: PreferencePicker) : PreferencesEvent
    data object OnPickerDismiss : PreferencesEvent

    data class OnLanguageSelect(val value: AppLanguage) : PreferencesEvent
    data class OnRegionSelect(val value: AppRegion) : PreferencesEvent
    data class OnCurrencySelect(val value: CurrencyCode) : PreferencesEvent
    data class OnNumberFormatSelect(val value: NumberFormatStyle) : PreferencesEvent
    data class OnWeekStartSelect(val value: WeekStart) : PreferencesEvent
    data class OnHourFormatSelect(val value: HourFormat) : PreferencesEvent
    data class OnDefaultTransactionTypeSelect(val value: DefaultTransactionType) : PreferencesEvent

    data class OnHideAmountsToggle(val enabled: Boolean) : PreferencesEvent
    data class OnAutoCategorizeToggle(val enabled: Boolean) : PreferencesEvent
    data class OnRoundUpToggle(val enabled: Boolean) : PreferencesEvent
}

/**
 * Only navigation is left: the pickers used to be side effects that the screen mapped to `Unit`,
 * and they are state now — which sheet is open is something the screen must be able to re-render
 * after a configuration change, not a one-shot signal.
 */
sealed interface PreferencesEffect {
    data object NavigateBack : PreferencesEffect
}
