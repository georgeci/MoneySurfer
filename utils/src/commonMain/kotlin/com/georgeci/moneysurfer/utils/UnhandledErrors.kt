package com.georgeci.moneysurfer.utils

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Process-wide channel for throwables no feature handled itself: [MviViewModel.launch]
 * publishes here when the caller passed no `onError`, and the global error boundary in
 * `App.kt` renders them as a friendly error state (issue #78).
 *
 * A singleton rather than a DI-injected dependency on purpose — the alternative is
 * threading a reporter through the constructor of every one of the ~25 feature
 * ViewModels, for a channel that is genuinely global.
 *
 * Publishing here is *not* the reporting path: [MviViewModel.launch] logs the throwable
 * via Kermit at Error severity first, which is what reaches Crashlytics through
 * `CrashReportingLogWriter`. This flow only drives the UI fallback.
 */
object UnhandledErrors {

    private val mutableErrors = MutableSharedFlow<Throwable>(
        extraBufferCapacity = 1,
        // A burst of failures (e.g. every widget on the dashboard failing at once) should
        // surface the newest one rather than block the emitting coroutine.
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val errors: Flow<Throwable> = mutableErrors

    fun report(error: Throwable) {
        mutableErrors.tryEmit(error)
    }
}
