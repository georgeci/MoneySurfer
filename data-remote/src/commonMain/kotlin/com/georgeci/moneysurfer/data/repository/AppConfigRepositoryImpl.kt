package com.georgeci.moneysurfer.data.repository

import co.touchlab.kermit.Logger
import com.georgeci.moneysurfer.data.remote.AppConfigRemoteSource
import com.georgeci.moneysurfer.domain.model.RemoteAppConfig
import com.georgeci.moneysurfer.domain.repositories.AppConfigRepository
import org.koin.core.annotation.Single

@Single(binds = [AppConfigRepository::class])
class AppConfigRepositoryImpl(
    private val remoteSource: AppConfigRemoteSource,
) : AppConfigRepository {

    private val log = Logger.withTag("AppConfigRepository")

    override suspend fun fetch(): RemoteAppConfig? = runCatching {
        remoteSource.fetchMobile()?.let { dto ->
            RemoteAppConfig(
                minSupportedAppVersionCode = dto.minSupportedAppVersionCode,
                latestAppVersionCode = dto.latestAppVersionCode,
                forceUpdate = dto.forceUpdate,
                message = dto.message,
            )
        }
    }.onFailure { error ->
        log.w(error) { "fetch failed — gate falls back to Supported" }
    }.getOrNull()
}
