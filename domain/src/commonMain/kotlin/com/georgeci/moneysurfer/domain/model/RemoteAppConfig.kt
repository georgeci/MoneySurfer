package com.georgeci.moneysurfer.domain.model

/**
 * Server-driven configuration backing the app-version gate.
 *
 * Lives in Firestore at `appConfig/mobile`; pulled fresh on demand via
 * [com.georgeci.moneysurfer.domain.repositories.AppConfigRepository].
 *
 * See block.md.
 */
data class RemoteAppConfig(
    val minSupportedAppVersionCode: Int,
    val latestAppVersionCode: Int,
    val forceUpdate: Boolean,
    val message: String?,
)
