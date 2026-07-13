package com.georgeci.moneysurfer.navigation

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.jetbrains.compose.resources.StringResource
import org.koin.core.annotation.Single

/**
 * One-shot message shown by the single app-level [SnackbarHost][androidx.compose.material3.SnackbarHost].
 *
 * The message is carried as an unresolved [StringResource] so feature ViewModels stay free of
 * Compose resource loading (and remain unit-testable); the root scaffold resolves it. [onAction]
 * is invoked when the user taps the action button (e.g. "Undo") and runs on an app-scoped
 * coroutine, so it survives the screen that produced the message being popped.
 */
data class SnackbarRequest(
    val message: StringResource,
    val messageArgs: List<Any> = emptyList(),
    val actionLabel: StringResource? = null,
    val onAction: (suspend () -> Unit)? = null,
)

/**
 * App-wide bus for transient Snackbar feedback. Feature ViewModels post here after a
 * create / update / delete; the root scaffold collects and renders the messages. A channel
 * (not a flow) keeps each message single-delivery so navigation transitions never replay one.
 */
@Single
class SnackbarController {

    private val channel = Channel<SnackbarRequest>(Channel.UNLIMITED)

    val requests: Flow<SnackbarRequest> = channel.receiveAsFlow()

    fun show(
        message: StringResource,
        messageArgs: List<Any> = emptyList(),
        actionLabel: StringResource? = null,
        onAction: (suspend () -> Unit)? = null,
    ) {
        channel.trySend(SnackbarRequest(message, messageArgs, actionLabel, onAction))
    }
}
