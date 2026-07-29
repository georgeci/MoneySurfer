package com.georgeci.moneysurfer.data.config

import com.georgeci.moneysurfer.appconfig.Config
import com.georgeci.moneysurfer.appconfig.HostConfigKeys
import com.georgeci.moneysurfer.domain.config.HostCapabilities
import org.koin.core.annotation.Single

/**
 * Reads the host's Build layer through the engine, so a QA override can shadow any of these
 * locally.
 *
 * Getters rather than constructor-time reads: `Config.snapshot` is only valid after hydration, and
 * this singleton may well be constructed before the startup coroutine has run.
 */
@Single(binds = [HostCapabilities::class])
class HostCapabilitiesImpl(private val config: Config) : HostCapabilities {

    override val isOffline: Boolean get() = config.snapshot(HostConfigKeys.isOffline)

    override val signInEmailPassword: Boolean get() = config.snapshot(HostConfigKeys.signInEmailPassword)

    override val signInAnonymous: Boolean get() = config.snapshot(HostConfigKeys.signInAnonymous)

    override val signInDemo: Boolean get() = config.snapshot(HostConfigKeys.signInDemo)

    override val transferEnabled: Boolean get() = config.snapshot(HostConfigKeys.transferEnabled)

    override val dashboardWidgetStyle: Boolean get() = config.snapshot(HostConfigKeys.dashboardWidgetStyle)
}
