package com.georgeci.moneysurfer.feature.settings.preferences

import com.georgeci.moneysurfer.utils.MviViewModel
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class PreferencesViewModel : MviViewModel<PreferencesState, PreferencesEvent, PreferencesEffect>(
    initialState = PreferencesState(),
) {

    override fun onEvent(event: PreferencesEvent) {
        when (event) {
            PreferencesEvent.OnBackClick -> postSideEffect(PreferencesEffect.NavigateBack)

            PreferencesEvent.OnLanguageClick -> postSideEffect(PreferencesEffect.OpenLanguagePicker)
            PreferencesEvent.OnRegionClick -> postSideEffect(PreferencesEffect.OpenRegionPicker)
            PreferencesEvent.OnCurrencyClick -> postSideEffect(PreferencesEffect.OpenCurrencyPicker)
            PreferencesEvent.OnNumberFormatClick -> postSideEffect(PreferencesEffect.OpenNumberFormatPicker)
            PreferencesEvent.OnWeekStartClick -> postSideEffect(PreferencesEffect.OpenWeekStartPicker)
            PreferencesEvent.OnHourFormatClick -> postSideEffect(PreferencesEffect.OpenHourFormatPicker)
            PreferencesEvent.OnDefaultTxnClick -> postSideEffect(PreferencesEffect.OpenDefaultTxnPicker)

            is PreferencesEvent.OnHideAmountsToggle ->
                updateState { copy(hideAmounts = event.enabled) }
            is PreferencesEvent.OnAutoCategorizeToggle ->
                updateState { copy(autoCategorize = event.enabled) }
            is PreferencesEvent.OnRoundUpToggle ->
                updateState { copy(roundUp = event.enabled) }
        }
    }
}

data class PreferencesState(
    val hideAmounts: Boolean = false,
    val autoCategorize: Boolean = true,
    val roundUp: Boolean = false,
)

sealed interface PreferencesEvent {
    data object OnBackClick : PreferencesEvent
    data object OnLanguageClick : PreferencesEvent
    data object OnRegionClick : PreferencesEvent
    data object OnCurrencyClick : PreferencesEvent
    data object OnNumberFormatClick : PreferencesEvent
    data object OnWeekStartClick : PreferencesEvent
    data object OnHourFormatClick : PreferencesEvent
    data object OnDefaultTxnClick : PreferencesEvent
    data class OnHideAmountsToggle(val enabled: Boolean) : PreferencesEvent
    data class OnAutoCategorizeToggle(val enabled: Boolean) : PreferencesEvent
    data class OnRoundUpToggle(val enabled: Boolean) : PreferencesEvent
}

sealed interface PreferencesEffect {
    data object NavigateBack : PreferencesEffect
    data object OpenLanguagePicker : PreferencesEffect
    data object OpenRegionPicker : PreferencesEffect
    data object OpenCurrencyPicker : PreferencesEffect
    data object OpenNumberFormatPicker : PreferencesEffect
    data object OpenWeekStartPicker : PreferencesEffect
    data object OpenHourFormatPicker : PreferencesEffect
    data object OpenDefaultTxnPicker : PreferencesEffect
}
