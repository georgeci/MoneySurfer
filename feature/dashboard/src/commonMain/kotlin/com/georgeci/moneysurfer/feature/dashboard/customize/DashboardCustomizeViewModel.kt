package com.georgeci.moneysurfer.feature.dashboard.customize

import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.dashboard.DashboardWidgetType
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import com.georgeci.moneysurfer.utils.AsyncState
import com.georgeci.moneysurfer.utils.MviViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class DashboardCustomizeViewModel(
    private val uiPreferences: UiPreferences,
) : MviViewModel<AsyncState<DashboardLayoutConfig>, DashboardCustomizeEvent, DashboardCustomizeEffect>(
    initialState = AsyncState.Loading,
) {

    /**
     * The layout waiting to be written, or null while nothing has been edited. A drag produces a
     * move every half row, and firing an independent write coroutine per move would let two of them
     * reach the store out of order and leave the older layout on disk. One writer draining a
     * conflated flow keeps the last edit last, and drops the intermediate layouts nobody needs.
     */
    private val pendingWrite = MutableStateFlow<DashboardLayoutConfig?>(null)

    init {
        seedLayout()
        launch {
            pendingWrite.filterNotNull().collect { uiPreferences.dashboardLayout.set(it) }
        }
    }

    override fun onEvent(event: DashboardCustomizeEvent) {
        when (event) {
            DashboardCustomizeEvent.OnBackClick -> postSideEffect(DashboardCustomizeEffect.NavigateBack)
            is DashboardCustomizeEvent.OnWidgetEnabledChange ->
                edit { it.withWidgetEnabled(event.type, event.enabled) }
            is DashboardCustomizeEvent.OnWidgetMove ->
                edit { it.withWidgetMoved(from = event.from, to = event.to) }
        }
    }

    /**
     * Read once instead of collected. This screen is the only writer of the layout, and every edit
     * below already updates the state it just persisted; staying subscribed would let the store
     * echo an earlier write back mid-drag and shuffle the rows out from under the finger.
     */
    private fun seedLayout() {
        launch {
            val stored = uiPreferences.dashboardLayout.flow.first().normalized()
            updateState { AsyncState.Content(stored) }
        }
    }

    /**
     * Applies [transform] to the layout on screen and persists the result. The state moves first so
     * a drag tracks the finger at frame rate rather than at the speed of a disk write.
     */
    private fun edit(transform: (DashboardLayoutConfig) -> DashboardLayoutConfig) {
        val current = (currentState as? AsyncState.Content)?.value ?: return
        val updated = transform(current)
        if (updated == current) return
        updateState { AsyncState.Content(updated) }
        pendingWrite.value = updated
    }
}

sealed interface DashboardCustomizeEvent {
    data object OnBackClick : DashboardCustomizeEvent
    data class OnWidgetEnabledChange(val type: DashboardWidgetType, val enabled: Boolean) : DashboardCustomizeEvent

    /** [from] was dragged onto the dashboard slot [to] holds right now. */
    data class OnWidgetMove(val from: DashboardWidgetType, val to: DashboardWidgetType) : DashboardCustomizeEvent
}

sealed interface DashboardCustomizeEffect {
    data object NavigateBack : DashboardCustomizeEffect
}
