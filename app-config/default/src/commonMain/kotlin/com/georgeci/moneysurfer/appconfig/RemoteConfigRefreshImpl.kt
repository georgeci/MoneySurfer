package com.georgeci.moneysurfer.appconfig

import com.georgeci.moneysurfer.domain.config.RemoteConfigRefresh
import org.koin.core.annotation.Single

/**
 * Exposes [RemoteGlobalConfigSource.refresh] to `navigation`, which — like feature modules — must
 * not depend on `app-config`.
 *
 * Bound in both hosts: the offline one resolves [RemoteGlobalConfigSource.Empty], whose `refresh()`
 * is the interface's no-op default, so the caller needs no host-specific branch.
 */
@Single(binds = [RemoteConfigRefresh::class])
class RemoteConfigRefreshImpl(private val remoteGlobal: RemoteGlobalConfigSource) : RemoteConfigRefresh {
    override suspend fun refresh() = remoteGlobal.refresh()
}
