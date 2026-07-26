package com.georgeci.moneysurfer.data.config

import com.georgeci.moneysurfer.appconfig.Config
import com.georgeci.moneysurfer.appconfig.ConfigKey
import com.georgeci.moneysurfer.appconfig.ConfigKeyGroup
import com.georgeci.moneysurfer.appconfig.HostConfigKeys
import com.georgeci.moneysurfer.appconfig.SettingKey
import com.georgeci.moneysurfer.domain.config.SyncSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import org.koin.core.annotation.Single

/**
 * The two non-host terms of [SyncSettings.isEnabled]. The build term is
 * [HostConfigKeys.syncEnabled], because hosts have to declare it themselves.
 */
internal object SyncConfigKeys {

    /**
     * Server kill switch. Default `true` = "not killed", so a missing remote document leaves the
     * decision to the build and the user rather than switching sync off for everyone.
     *
     * The one `remoteOverridable` key in the app today. It is not a `SettingKey`, so no client can
     * write it.
     */
    val remoteEnabled: ConfigKey<Boolean> =
        ConfigKey.bool("sync.remote_enabled", default = true, remoteOverridable = true)

    /** User toggle. Synced, so turning sync off on one device turns it off on the others. */
    val userEnabled: SettingKey<Boolean> =
        SettingKey.bool("sync.user_enabled", default = true, sync = true)

    val all: List<ConfigKey<*>> = listOf(remoteEnabled, userEnabled)
}

@Single(binds = [ConfigKeyGroup::class])
class SyncConfigKeyGroup : ConfigKeyGroup {
    override val keys: List<ConfigKey<*>> = SyncConfigKeys.all
}

/**
 * A kill switch must not *replace* a user toggle, it must *zero* it — so the three terms are
 * combined here rather than composed inside the key-value layer.
 *
 * The composition lives in the facade rather than a use case: a use case injecting `Config` would
 * have nowhere legal to live, since features cannot see `app-config` and `domain` must not depend
 * on it.
 */
@Single(binds = [SyncSettings::class])
class SyncSettingsImpl(config: Config) : SyncSettings {

    override val isEnabled: Flow<Boolean> = combine(
        config.observe(HostConfigKeys.syncEnabled),
        config.observe(SyncConfigKeys.remoteEnabled),
        config.observe(SyncConfigKeys.userEnabled),
    ) { build, remote, user -> build && remote && user }
}
