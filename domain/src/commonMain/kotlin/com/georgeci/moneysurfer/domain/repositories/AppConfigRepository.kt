package com.georgeci.moneysurfer.domain.repositories

import com.georgeci.moneysurfer.domain.model.RemoteAppConfig

/**
 * Reads the `appConfig/mobile` Firestore document. Implementations should
 * tolerate a missing doc / network failure and return `null` rather than
 * throwing — the gate treats `null` as "unknown, default to Supported".
 *
 * See block.md.
 */
interface AppConfigRepository {
    suspend fun fetch(): RemoteAppConfig?
}
