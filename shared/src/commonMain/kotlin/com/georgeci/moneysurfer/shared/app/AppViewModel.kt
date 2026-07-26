package com.georgeci.moneysurfer.shared.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.georgeci.moneysurfer.domain.preferences.ContainerStyle
import com.georgeci.moneysurfer.domain.preferences.PaletteSource
import com.georgeci.moneysurfer.domain.preferences.ThemeMode
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class AppViewModel(
    private val uiPreferences: UiPreferences,
) : ViewModel() {

    val state: StateFlow<AppState>
        field = MutableStateFlow(AppState())

    init {
        state.update { it.copy(isDynamicColorAvailable = uiPreferences.isDynamicColorAvailable) }

        // Theming reads the *effective* palette: a `Dynamic` value synced from an Android phone is
        // not renderable here, and clamping on read is what keeps the stored choice intact.
        uiPreferences.effectivePaletteSource
            .onEach { source -> state.update { it.copy(paletteSource = source) } }
            .launchIn(viewModelScope)

        uiPreferences.themeMode.flow
            .onEach { mode -> state.update { it.copy(themeMode = mode) } }
            .launchIn(viewModelScope)

        uiPreferences.containerStyle.flow
            .onEach { style -> state.update { it.copy(containerStyle = style) } }
            .launchIn(viewModelScope)
    }
}

data class AppState(
    val isDynamicColorAvailable: Boolean = false,
    val paletteSource: PaletteSource = PaletteSource.DEFAULT,
    val themeMode: ThemeMode = ThemeMode.System,
    val containerStyle: ContainerStyle = ContainerStyle.Card,
) {
    val isDynamicColorEnabled: Boolean
        get() = paletteSource is PaletteSource.Dynamic
}
