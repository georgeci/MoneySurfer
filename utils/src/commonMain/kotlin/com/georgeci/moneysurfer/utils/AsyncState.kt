package com.georgeci.moneysurfer.utils

sealed interface AsyncState<out C : Any> {
    data object Loading : AsyncState<Nothing>

    data class Content<C : Any>(
        val value: C,
        val pending: Boolean = false,
    ) : AsyncState<C>
}
