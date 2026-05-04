package com.georgeci.moneysurfer.utils

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
class StateHolder<STATE> private constructor(
    private val mutableStateFlow: MutableStateFlow<STATE>,
    private val stateFlow: StateFlow<STATE> = mutableStateFlow.asStateFlow(),
) : StateFlow<STATE> by stateFlow {
    constructor(
        initialState: STATE,
    ) : this(
        MutableStateFlow(initialState),
    )

    fun updateState(updater: (STATE) -> STATE) {
        mutableStateFlow.update(updater)
    }
}
