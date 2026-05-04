package com.georgeci.moneysurfer.feature.settings.appearance

import com.georgeci.moneysurfer.domain.preferences.AccentSeed
import com.georgeci.moneysurfer.domain.preferences.ContainerStyle
import com.georgeci.moneysurfer.domain.preferences.PaletteSource
import com.georgeci.moneysurfer.domain.preferences.ThemeMode
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AppearanceViewModel(
    private val uiPreferences: UiPreferences,
) : MviViewModel<AppearanceState, AppearanceEvent, Nothing>(
    initialState = AppearanceState(isDynamicColorAvailable = uiPreferences.isDynamicColorAvailable),
) {

    init {
        launch {
            uiPreferences.paletteSource.flow
                .onEach { source -> updateState { copy(paletteSource = source) } }
                .collect()
        }
        launch {
            uiPreferences.themeMode.flow
                .onEach { mode -> updateState { copy(themeMode = mode) } }
                .collect()
        }
        launch {
            uiPreferences.containerStyle.flow
                .onEach { style -> updateState { copy(containerStyle = style) } }
                .collect()
        }
    }

    override fun onEvent(event: AppearanceEvent) {
        when (event) {
            AppearanceEvent.OnDynamicSelect -> launch {
                uiPreferences.paletteSource.set(PaletteSource.Dynamic)
            }
            AppearanceEvent.OnBrandSelect -> launch {
                uiPreferences.paletteSource.set(PaletteSource.Brand)
            }
            is AppearanceEvent.OnPresetSelect -> launch {
                uiPreferences.paletteSource.set(PaletteSource.Preset(event.seed))
            }
            is AppearanceEvent.OnThemeModeSelect -> launch {
                uiPreferences.themeMode.set(event.mode)
            }
            is AppearanceEvent.OnContainerStyleSelect -> launch {
                uiPreferences.containerStyle.set(event.style)
            }
        }
    }
}

data class AppearanceState(
    val isDynamicColorAvailable: Boolean = false,
    val paletteSource: PaletteSource = PaletteSource.DEFAULT,
    val themeMode: ThemeMode = ThemeMode.System,
    val containerStyle: ContainerStyle = ContainerStyle.Card,
) {
    val isDynamicColorEnabled: Boolean get() = paletteSource is PaletteSource.Dynamic
    val isBrandSelected: Boolean get() = paletteSource is PaletteSource.Brand
    val selectedSeed: AccentSeed? get() = (paletteSource as? PaletteSource.Preset)?.seed
}

sealed interface AppearanceEvent {
    data object OnDynamicSelect : AppearanceEvent
    data object OnBrandSelect : AppearanceEvent
    data class OnPresetSelect(val seed: AccentSeed) : AppearanceEvent
    data class OnThemeModeSelect(val mode: ThemeMode) : AppearanceEvent
    data class OnContainerStyleSelect(val style: ContainerStyle) : AppearanceEvent
}
