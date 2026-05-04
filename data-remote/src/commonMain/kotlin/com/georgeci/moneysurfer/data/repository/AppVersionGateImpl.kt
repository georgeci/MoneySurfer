package com.georgeci.moneysurfer.data.repository

import com.georgeci.moneysurfer.domain.AppInfo
import com.georgeci.moneysurfer.domain.model.AppVersionStatus
import com.georgeci.moneysurfer.domain.model.RemoteAppConfig
import com.georgeci.moneysurfer.domain.repositories.AppConfigRepository
import com.georgeci.moneysurfer.domain.repositories.AppVersionGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.koin.core.annotation.Single

private const val DEFAULT_UNSUPPORTED_MESSAGE: String =
    "This app version is no longer supported. Please update."

@Single(binds = [AppVersionGate::class])
class AppVersionGateImpl(
    private val appConfigRepository: AppConfigRepository,
    private val appInfo: AppInfo,
) : AppVersionGate {

    private val _status = MutableStateFlow<AppVersionStatus?>(null)
    override val status: StateFlow<AppVersionStatus?> = _status.asStateFlow()

    override suspend fun refresh(): AppVersionStatus {
        val config = appConfigRepository.fetch()
        val resolved = if (config == null) AppVersionStatus.Supported else evaluate(config)
        _status.value = resolved
        return resolved
    }

    override fun isSyncAllowed(): Boolean = when (status.value) {
        null,
        AppVersionStatus.Supported,
        is AppVersionStatus.UpdateAvailable,
        -> true
        is AppVersionStatus.Unsupported -> false
    }

    private fun evaluate(config: RemoteAppConfig): AppVersionStatus = when {
        appInfo.versionCode < config.minSupportedAppVersionCode || config.forceUpdate ->
            AppVersionStatus.Unsupported(
                message = config.message ?: DEFAULT_UNSUPPORTED_MESSAGE,
            )
        appInfo.versionCode < config.latestAppVersionCode ->
            AppVersionStatus.UpdateAvailable(message = config.message)
        else -> AppVersionStatus.Supported
    }
}
