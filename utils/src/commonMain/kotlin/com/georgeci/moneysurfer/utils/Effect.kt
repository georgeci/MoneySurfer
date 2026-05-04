package com.georgeci.moneysurfer.utils

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

class Effect<SIDE_EFFECT : Any> {

    private val channel = Channel<SIDE_EFFECT>(Channel.UNLIMITED)

    val effectFlow: Flow<SIDE_EFFECT> = channel.receiveAsFlow()

    fun post(sideEffect: SIDE_EFFECT) {
        channel.trySend(sideEffect)
    }
}
