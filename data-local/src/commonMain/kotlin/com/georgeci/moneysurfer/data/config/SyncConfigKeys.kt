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

    /**
     * User toggle.
     *
     * `sync = false` is mandatory, and for a reason that only became visible once replication was
     * real: this key gates its own replication. `SyncCoordinatorWorkspaceSyncer` refuses to push or
     * pull while it is `false`, so the value `false` can never reach Firestore — only `true` can.
     * Were it `sync = true` it would live in the account-scoped `config_entry` table, which
     * `LocalDataResetRepositoryImpl.clearAll()` wipes on logout, and the next sign-in would resolve
     * it to the `true` default with nothing on the server able to restore the user's choice.
     * Turning sync off and logging out would silently turn it back on and start uploading.
     *
     * Device-scoped is also the honest scope: "do not talk to the server from this device" is a
     * decision about a device, not an account setting to be mirrored onto the user's other ones.
     */
    val userEnabled: SettingKey<Boolean> =
        SettingKey.bool("sync.user_enabled", default = true, sync = false)

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
