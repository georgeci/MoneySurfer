package com.georgeci.moneysurfer.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.NonRestartableComposable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
abstract class MviViewModel<STATE : Any, EVENT, SIDE_EFFECT : Any>(
    private val stateHolder: StateHolder<STATE>,
) : ViewModel(), StateFlow<STATE> by stateHolder {

    constructor(initialState: STATE) : this(StateHolder(initialState))

    val sideEffects = Effect<SIDE_EFFECT>()

    val currentState: STATE get() = stateHolder.value

    abstract fun onEvent(event: EVENT)

    protected fun updateState(reducer: STATE.() -> STATE) {
        stateHolder.updateState { it.reducer() }
    }

    protected fun postSideEffect(effect: SIDE_EFFECT) {
        sideEffects.post(effect)
    }

    /**
     * Runs [block] on [viewModelScope], catching whatever it throws.
     *
     * Failures are never swallowed: the throwable is always logged at Error severity,
     * which is what `CrashReportingLogWriter` turns into a Crashlytics non-fatal. What
     * [onError] controls is only who renders it — pass a handler to show the failure
     * inside the screen's own state, or leave it null to escalate to the global error
     * boundary via [UnhandledErrors] (issue #78).
     */
    protected fun launch(
        onError: ((Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        viewModelScope.launch {
            try {
                block()
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                Logger.withTag(logTag).e(e) { "Unhandled error in $logTag" }
                if (onError != null) onError(e) else UnhandledErrors.report(e)
            }
        }
    }

    private val logTag: String get() = this::class.simpleName ?: "MviViewModel"
}

@NonRestartableComposable
@Composable
fun <SIDE_EFFECT : Any> Effect<SIDE_EFFECT>.HandleSideEffect(
    handler: (SIDE_EFFECT) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            effectFlow.collect(handler)
        }
    }
}

@NonRestartableComposable
@Composable
fun <SIDE_EFFECT : Any> MviViewModel<*, *, SIDE_EFFECT>.HandleSideEffect(
    handler: (SIDE_EFFECT) -> Unit,
) {
    sideEffects.HandleSideEffect(handler)
}
