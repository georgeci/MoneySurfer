package com.georgeci.moneysurfer.feature.settings.debug

import com.georgeci.moneysurfer.domain.config.ConfigDebugRow
import com.georgeci.moneysurfer.domain.config.DebugConfigInspector
import com.georgeci.moneysurfer.domain.debug.DebugDataPrefiller
import com.georgeci.moneysurfer.domain.debug.DebugPrefillError
import com.georgeci.moneysurfer.domain.debug.DebugPrefillReport
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import org.koin.core.annotation.KoinViewModel

/**
 * QA configuration panel. Reads and writes only through [DebugConfigInspector], so no
 * configuration key, codec or layer type reaches this module — rows arrive pre-rendered and
 * overrides go back out keyed by name.
 *
 * [DebugDataPrefiller] is the panel's second half: the same screen is where a tester fills an
 * empty install with something to look at.
 */
@KoinViewModel
class DebugConfigViewModel(
    private val inspector: DebugConfigInspector,
    private val prefiller: DebugDataPrefiller,
) : MviViewModel<DebugConfigState, DebugConfigEvent, DebugConfigEffect>(
    initialState = DebugConfigState(
        isAvailable = inspector.isAvailable,
        degradedLayers = inspector.degradedLayers,
    ),
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
            DebugConfigEvent.OnLogsClick -> postSideEffect(DebugConfigEffect.NavigateToLogs)
            is DebugConfigEvent.OnOverride -> override(event.name, event.raw)
            is DebugConfigEvent.OnClearOverride -> launch { inspector.clearOverride(event.name) }
            DebugConfigEvent.OnResetAllClick -> launch { inspector.resetAll() }
            DebugConfigEvent.OnPrefillClick -> prefill()
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

    /**
     * Guarded by [DebugConfigState.inFlight]: the run writes hundreds of rows, and a second tap
     * while the first is still going would double the batch for no reason a tester would want.
     *
     * The reset is in a `finally` because not every failure comes back as a `Left`: the prefiller
     * reads the session pointers and asks for a sync outside its own `Either.catch`, so a throw is
     * reachable. Resetting only on the happy path would strand the flag at `true`, and since the
     * row expresses "busy" by dropping its click handler, that leaves it permanently unclickable.
     */
    private fun prefill() {
        if (currentState.inFlight) return
        launch {
            updateState { copy(inFlight = true) }
            try {
                val outcome = prefiller.prefill().fold(
                    ifLeft = { error ->
                        when (error) {
                            DebugPrefillError.NoWorkspace -> PrefillOutcome.NoWorkspace
                            is DebugPrefillError.WriteFailed -> PrefillOutcome.Failed
                        }
                    },
                    ifRight = { PrefillOutcome.Done(it) },
                )
                postSideEffect(DebugConfigEffect.NotifyPrefillResult(outcome))
            } finally {
                updateState { copy(inFlight = false) }
            }
        }
    }
}

data class DebugConfigState(
    val isAvailable: Boolean = false,
    val rows: List<ConfigDebugRow> = emptyList(),
    /**
     * Layers whose store could not be read at startup. Their rows resolve as if the layer were
     * empty, so the panel has to say so rather than presenting the fallback as the real answer.
     */
    val degradedLayers: List<String> = emptyList(),
    /** A prefill run is writing. Blocks a second run and puts the row in a busy state. */
    val inFlight: Boolean = false,
)

sealed interface DebugConfigEvent {
    data object OnBackClick : DebugConfigEvent
    data object OnLogsClick : DebugConfigEvent
    data class OnOverride(val name: String, val raw: String) : DebugConfigEvent
    data class OnClearOverride(val name: String) : DebugConfigEvent
    data object OnResetAllClick : DebugConfigEvent
    data object OnPrefillClick : DebugConfigEvent
}

sealed interface DebugConfigEffect {
    data object NavigateBack : DebugConfigEffect
    data object NavigateToLogs : DebugConfigEffect
    data class NotifyInvalidValue(val name: String, val raw: String) : DebugConfigEffect
    data class NotifyPrefillResult(val outcome: PrefillOutcome) : DebugConfigEffect
}

/**
 * What the panel says after a prefill run. The failure cases are separate because they call for
 * different actions: `NoWorkspace` means the tester is on the wrong screen, `Failed` means the
 * detail is in the log buffer one row up.
 */
sealed interface PrefillOutcome {
    data class Done(val report: DebugPrefillReport) : PrefillOutcome
    data object NoWorkspace : PrefillOutcome
    data object Failed : PrefillOutcome
}
