package com.georgeci.moneysurfer.domain.model

/**
 * Result of comparing the local build against the remote app-version gate.
 *
 * - [Supported] — version is current or newer than `latestAppVersionCode`.
 * - [UpdateAvailable] — soft prompt; sync still allowed.
 * - [Unsupported] — hard block; sync and write-usecases must refuse.
 *
 * See block.md.
 */
sealed interface AppVersionStatus {

    data object Supported : AppVersionStatus

    data class UpdateAvailable(val message: String?) : AppVersionStatus

    data class Unsupported(val message: String) : AppVersionStatus
}
