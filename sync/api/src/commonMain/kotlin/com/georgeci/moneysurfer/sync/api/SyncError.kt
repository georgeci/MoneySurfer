package com.georgeci.moneysurfer.sync.api

import arrow.core.Either

/**
 * Standalone sync error type — NOT nested under any common `AppError`.
 * See SyncCoordinatorFAQ.md №11, №12.
 */
sealed interface SyncError {
    val isRetryable: Boolean

    data object Cancelled : SyncError {
        override val isRetryable: Boolean = false
    }

    data object NetworkUnavailable : SyncError {
        override val isRetryable: Boolean = true
    }

    data object AuthRequired : SyncError {
        override val isRetryable: Boolean = false
    }

    data object PermissionDenied : SyncError {
        override val isRetryable: Boolean = false
    }

    /**
     * The local app build is older than `appConfig/mobile.minSupportedAppVersionCode`.
     * UI must direct the user to the store; sync stays disabled until the
     * app is upgraded. See block.md.
     */
    data class UnsupportedAppVersion(val message: String) : SyncError {
        override val isRetryable: Boolean = false
    }

    data class StorageError(val cause: Throwable) : SyncError {
        override val isRetryable: Boolean = false
    }

    data class Unknown(val cause: Throwable) : SyncError {
        override val isRetryable: Boolean = true
    }
}

typealias SyncResult<T> = Either<SyncError, T>
