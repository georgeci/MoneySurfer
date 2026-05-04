package com.georgeci.moneysurfer.sync.internal

import com.georgeci.moneysurfer.sync.api.SyncError
import kotlinx.serialization.SerializationException
import kotlin.coroutines.cancellation.CancellationException

internal fun Throwable.toSyncError(): SyncError = when (this) {
    is CancellationException -> SyncError.Cancelled
    is SerializationException -> SyncError.Unknown(this)
    else -> SyncError.Unknown(this)
}
