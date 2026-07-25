package com.georgeci.moneysurfer.data.config

import com.georgeci.moneysurfer.appconfig.Config
import com.georgeci.moneysurfer.appconfig.ConfigKey
import com.georgeci.moneysurfer.appconfig.ConfigLayer
import com.georgeci.moneysurfer.appconfig.ConfigResolution
import com.georgeci.moneysurfer.appconfig.HostConfigKeys
import com.georgeci.moneysurfer.appconfig.LayerValue
import com.georgeci.moneysurfer.appconfig.SettingKey
import com.georgeci.moneysurfer.data.preferences.UiPreferencesImpl
import com.georgeci.moneysurfer.domain.preferences.PaletteSource
import com.georgeci.moneysurfer.domain.preferences.Pref
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * In-memory [Config] holding typed values by key name — enough to exercise the facades without
 * standing up the layered engine (which `app-config/default` covers on its own).
 */
private class InMemoryConfig(initial: Map<String, Any> = emptyMap()) : Config {

    private val state = MutableStateFlow(initial)

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> valueOf(key: ConfigKey<T>): T = state.value[key.name] as T? ?: key.default

    override fun <T : Any> observe(key: ConfigKey<T>): Flow<T> = state.map { valueOf(key) }

    override fun <T : Any> snapshot(key: ConfigKey<T>): T = valueOf(key)

    override fun <T : Any> handle(key: SettingKey<T>): Pref<T> = object : Pref<T> {
        override val flow: Flow<T> = observe(key)
        override suspend fun set(value: T) {
            state.value = state.value + (key.name to value)
        }
    }

    override fun <T : Any> resolve(key: ConfigKey<T>): ConfigResolution<T> = ConfigResolution(
        value = valueOf(key),
        winner = if (state.value.containsKey(key.name)) ConfigLayer.Local else null,
        perLayer = emptyMap<ConfigLayer, LayerValue<T>>(),
    )

    override val changes: Flow<Unit> = state.map { }

    override suspend fun hydrate() = Unit
}

class ConfigFacadesJvmTest : StringSpec({

    "sync is enabled only when build, server and user all say yes" {
        // Three terms, not two: sync is off in both hosts today because the feature is not shipped,
        // and a server/user pair would have no slot for that.
        val cases = listOf(
            Triple(false, true, true) to false,
            Triple(true, false, true) to false,
            Triple(true, true, false) to false,
            Triple(true, true, true) to true,
        )
        cases.forEach { (terms, expected) ->
            val (build, remote, user) = terms
            val config = InMemoryConfig(
                mapOf(
                    HostConfigKeys.syncEnabled.name to build,
                    SyncConfigKeys.remoteEnabled.name to remote,
                    SyncConfigKeys.userEnabled.name to user,
                ),
            )
            SyncSettingsImpl(config).isEnabled.first() shouldBe expected
        }
    }

    "sync stays off with nothing configured, because the build term defaults to false" {
        SyncSettingsImpl(InMemoryConfig()).isEnabled.first() shouldBe false
    }

    "host capabilities read straight through to the engine" {
        val config = InMemoryConfig(
            mapOf(
                HostConfigKeys.isOffline.name to true,
                HostConfigKeys.signInEmailPassword.name to false,
                HostConfigKeys.signInAnonymous.name to false,
                HostConfigKeys.signInDemo.name to true,
                HostConfigKeys.transferEnabled.name to false,
            ),
        )

        val host = HostCapabilitiesImpl(config)

        host.isOffline shouldBe true
        host.transferEnabled shouldBe false
        host.signInDemoOnly shouldBe true
    }

    "the stored palette keeps Dynamic while the effective one clamps it" {
        // The JVM has no Material You, but the palette is synced — so a phone can legitimately hand
        // this platform a value it cannot render. Clamping the stored `Pref` instead would let the
        // first tap on the picker write Brand over the user's Dynamic choice.
        val prefs = UiPreferencesImpl(InMemoryConfig())
        prefs.isDynamicColorAvailable shouldBe false

        prefs.paletteSource.set(PaletteSource.Dynamic)

        prefs.paletteSource.flow.first() shouldBe PaletteSource.Dynamic
        prefs.effectivePaletteSource.first() shouldBe PaletteSource.Brand
    }

    "a renderable palette passes through untouched" {
        val prefs = UiPreferencesImpl(InMemoryConfig())

        prefs.paletteSource.set(PaletteSource.Preset(com.georgeci.moneysurfer.domain.preferences.AccentSeed.Mint))

        prefs.effectivePaletteSource.first() shouldBe
            PaletteSource.Preset(com.georgeci.moneysurfer.domain.preferences.AccentSeed.Mint)
    }
})
