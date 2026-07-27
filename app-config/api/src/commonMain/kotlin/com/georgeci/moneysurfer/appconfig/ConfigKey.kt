package com.georgeci.moneysurfer.appconfig

/**
 * One configuration value: a compile-time constant, never a DI participant. The number of Koin
 * bindings therefore does not grow with the number of flags.
 *
 * [T] is `Any`-bounded because the engine already spends `null` on "absent in this layer" — an
 * "empty" value has to be a sentinel inside the codec instead.
 *
 * Plain `ConfigKey` is **read-only**: host-owned facts and server-owned flags. Only [SettingKey]
 * can be written, so `Config.handle` on a server flag fails to compile.
 */
open class ConfigKey<T : Any>(
    val name: String,
    val default: T,
    val codec: ConfigCodec<T>,
    /**
     * Opt-in: only these keys are served (or mirrored) by the RemoteGlobal layer. Without the
     * opt-in a free-form remote map could override anything it names — `host.is_offline` would
     * flip the online build onto the offline start route while its DI graph stays online, and any
     * `SettingKey` the user never wrote has an absent Local layer, so a remote
     * `ui.onboarding_completed` would skip onboarding on every fresh install.
     */
    val remoteOverridable: Boolean = false,
) {
    override fun toString(): String = "ConfigKey($name)"

    companion object {
        fun bool(
            name: String,
            default: Boolean,
            remoteOverridable: Boolean = false,
        ): ConfigKey<Boolean> = ConfigKey(name, default, BooleanConfigCodec, remoteOverridable)

        fun string(
            name: String,
            default: String,
            remoteOverridable: Boolean = false,
        ): ConfigKey<String> = ConfigKey(name, default, StringConfigCodec, remoteOverridable)

        inline fun <reified E : Enum<E>> enum(
            name: String,
            default: E,
            remoteOverridable: Boolean = false,
        ): ConfigKey<E> = ConfigKey(name, default, EnumConfigCodec(enumValues<E>().toList()), remoteOverridable)
    }
}

/**
 * Writable key — a user setting. Only settings get a setter, so a server- or host-owned flag
 * cannot be written by mistake.
 *
 * [sync] `true` marks the value for per-user replication; `false` keeps it device-scoped.
 * Device-scoped is mandatory for onboarding and demo state — replicating those would replay
 * onboarding across devices and let demo data leak into a real account.
 *
 * [sync] also decides where the value is *stored*: `true` routes it into the account-scoped Room
 * `config_entry` table, which is wiped on account change and pushed through the outbox, while
 * `false` keeps it in DataStore, where nothing wipes it. `LocalConfigSource` owns that routing, so
 * no caller sees the split.
 *
 * `remoteOverridable` is hard-coded `false` and not exposed as a parameter, so a user setting
 * cannot become server-controlled by oversight.
 */
class SettingKey<T : Any>(
    name: String,
    default: T,
    codec: ConfigCodec<T>,
    val sync: Boolean,
) : ConfigKey<T>(name, default, codec, remoteOverridable = false) {

    companion object {
        fun bool(name: String, default: Boolean, sync: Boolean): SettingKey<Boolean> =
            SettingKey(name, default, BooleanConfigCodec, sync)

        fun string(name: String, default: String, sync: Boolean): SettingKey<String> =
            SettingKey(name, default, StringConfigCodec, sync)

        inline fun <reified E : Enum<E>> enum(name: String, default: E, sync: Boolean): SettingKey<E> =
            SettingKey(name, default, EnumConfigCodec(enumValues<E>().toList()), sync)

        fun <T : Any> custom(
            name: String,
            default: T,
            codec: ConfigCodec<T>,
            sync: Boolean,
        ): SettingKey<T> = SettingKey(name, default, codec, sync)
    }
}

/**
 * Every key a module owns, contributed to a runtime registry. The debug panel and the
 * remote-config pull both need *all* keys, and keys are `internal` to their owning module, so
 * they cannot see the symbols — they read the registry instead.
 *
 * Bind one class per group to this shared interface (the `SyncEntityPlugin` precedent). Several
 * modules binding a bare `ConfigKeyGroup` would overwrite one another in Koin's definition index
 * and `getAll()` would return a single surviving group.
 */
interface ConfigKeyGroup {
    val keys: List<ConfigKey<*>>
}
