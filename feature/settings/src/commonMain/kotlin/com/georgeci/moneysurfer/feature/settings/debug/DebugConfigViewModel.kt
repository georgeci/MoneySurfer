package com.georgeci.moneysurfer.feature.settings.debug

import com.georgeci.moneysurfer.domain.config.ConfigDebugRow
import com.georgeci.moneysurfer.domain.config.DebugConfigInspector
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.KoinViewModel

/**
 * QA configuration panel. Reads and writes only through [DebugConfigInspector], so no
 * configuration key, codec or layer type reaches this module — rows arrive pre-rendered and
 * overrides go back out keyed by name.
 */
@KoinViewModel
class DebugConfigViewModel(
    private val inspector: DebugConfigInspector,
) : MviViewModel<DebugConfigState, DebugConfigEvent, DebugConfigEffect>(
    initialState = DebugConfigState(isAvailable = inspector.isAvailable),
) {

    init {
        launch {
            inspector.rows
                .onEach { rows -> updateState { copy(rows = rows) } }
                .collect()
        }
    }

    override fun onEvent(event: DebugConfigEvent) {
        when (event) {
            DebugConfigEvent.OnBackClick -> postSideEffect(DebugConfigEffect.NavigateBack)
            is DebugConfigEvent.OnOverride -> override(event.name, event.raw)
            is DebugConfigEvent.OnClearOverride -> launch { inspector.clearOverride(event.name) }
            DebugConfigEvent.OnResetAllClick -> launch { inspector.resetAll() }
        }
    }

    /**
     * The inspector validates through the key's codec, so a bad free-text value surfaces as a
     * message rather than a stored value nothing can read.
     */
    private fun override(name: String, raw: String) {
        launch {
            inspector.override(name, raw)
                .onFailure { postSideEffect(DebugConfigEffect.NotifyInvalidValue(name = name, raw = raw)) }
        }
    }
}

data class DebugConfigState(
    val isAvailable: Boolean = false,
    val rows: List<ConfigDebugRow> = emptyList(),
)

sealed interface DebugConfigEvent {
    data object OnBackClick : DebugConfigEvent
    data class OnOverride(val name: String, val raw: String) : DebugConfigEvent
    data class OnClearOverride(val name: String) : DebugConfigEvent
    data object OnResetAllClick : DebugConfigEvent
}

sealed interface DebugConfigEffect {
    data object NavigateBack : DebugConfigEffect
    data class NotifyInvalidValue(val name: String, val raw: String) : DebugConfigEffect
}
