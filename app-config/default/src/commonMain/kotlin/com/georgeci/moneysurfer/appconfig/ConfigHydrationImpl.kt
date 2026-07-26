package com.georgeci.moneysurfer.appconfig

import com.georgeci.moneysurfer.domain.config.ConfigHydration
import org.koin.core.annotation.Single

/**
 * Exposes [Config.hydrate] to `navigation`, which — like feature modules — must not depend on
 * `app-config`.
 */
@Single(binds = [ConfigHydration::class])
class ConfigHydrationImpl(private val config: Config) : ConfigHydration {
    override suspend fun hydrate() = config.hydrate()
}
