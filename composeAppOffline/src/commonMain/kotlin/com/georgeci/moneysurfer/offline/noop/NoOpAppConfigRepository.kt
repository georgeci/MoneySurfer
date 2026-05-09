package com.georgeci.moneysurfer.offline.noop

import com.georgeci.moneysurfer.domain.model.RemoteAppConfig
import com.georgeci.moneysurfer.domain.repositories.AppConfigRepository

/**
 * Offline build never reads `appConfig/mobile`. Returning `null` keeps the
 * version gate fail-open (treated as `Supported`).
 */
class NoOpAppConfigRepository : AppConfigRepository {
    override suspend fun fetch(): RemoteAppConfig? = null
}
