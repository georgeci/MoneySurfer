package com.georgeci.moneysurfer.appconfig

import org.koin.core.annotation.Single

/**
 * Every registered key, by name. Consumers that need *all* keys — the debug panel and, later, the
 * remote-config pull — cannot see the key symbols, because keys are `internal` to the module that
 * owns their facade. They read this registry instead.
 *
 * `getAll<ConfigKeyGroup>()` order is irrelevant here, so collecting the groups from Koin is safe
 * (unlike layer order, which is declared explicitly in [ConfigModule]).
 *
 * Duplicate key names would silently collapse in [byName]; a per-host test asserts uniqueness
 * against the assembled Koin graph, which is the only place `internal` keys are all visible at
 * once.
 */
@Single
class ConfigRegistry(groups: List<ConfigKeyGroup>) {

    /** Sorted by name so the debug panel renders in a stable order regardless of group order. */
    val keys: List<ConfigKey<*>> = groups.flatMap { it.keys }.sortedBy { it.name }

    private val byName: Map<String, ConfigKey<*>> = keys.associateBy { it.name }

    fun find(name: String): ConfigKey<*>? = byName[name]
}

/**
 * Host keys are public in `app-config/api` (hosts have to enumerate them), but they still need a
 * group so the debug panel lists them. Registered here rather than in a host module so both hosts
 * get it for free.
 */
@Single(binds = [ConfigKeyGroup::class])
class HostConfigKeyGroup : ConfigKeyGroup {
    override val keys: List<ConfigKey<*>> = HostConfigKeys.all
}
