package com.georgeci.moneysurfer.navigation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.github.irgaly.navigation3.resultstate.LocalNavigationResultConsumer
import io.github.irgaly.navigation3.resultstate.SerializableNavigationResultKey
import io.github.irgaly.navigation3.resultstate.clearResult
import io.github.irgaly.navigation3.resultstate.getResultState

/**
 * Reads the current navigation result for [key] and clears it after observation. Returns the
 * deserialized value (or `null` when no result is pending). Each consumer entry only sees the
 * result on the composition where it was written; the next composition observes `null`.
 */
@Composable
fun <T> rememberNavigationResult(key: SerializableNavigationResultKey<T>): T? {
    val consumer = LocalNavigationResultConsumer.current
    val raw by remember(consumer) { consumer.getResultState(key) }
    val value = raw?.getResult()
    LaunchedEffect(value) {
        value ?: return@LaunchedEffect
        consumer.clearResult(key)
    }
    return value
}
