package com.georgeci.moneysurfer.data.config

import com.georgeci.moneysurfer.appconfig.ConfigCodec
import com.georgeci.moneysurfer.appconfig.ConfigValueKind
import com.georgeci.moneysurfer.data.preferences.DashboardLayoutCodec
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.preferences.AccentSeed
import com.georgeci.moneysurfer.domain.preferences.PaletteSource

/**
 * Wire format, unchanged from the previous hand-rolled adapters:
 *  - `BRAND`
 *  - `PRESET:<seedName>`, e.g. `PRESET:Plum`
 *  - `DYNAMIC`
 *
 * Anything else — including a `PRESET:` with an unknown seed — is undecodable rather than silently
 * `Brand`, so a value written by a newer build falls through to the layer below instead of
 * overwriting the user's choice with a default.
 */
internal object PaletteSourceCodec : ConfigCodec<PaletteSource> {

    private const val BRAND = "BRAND"
    private const val DYNAMIC = "DYNAMIC"
    private const val PRESET_PREFIX = "PRESET:"

    override fun encode(value: PaletteSource): String = when (value) {
        PaletteSource.Brand -> BRAND
        PaletteSource.Dynamic -> DYNAMIC
        is PaletteSource.Preset -> PRESET_PREFIX + value.seed.name
    }

    override fun decode(raw: String): PaletteSource? = when {
        raw == BRAND -> PaletteSource.Brand
        raw == DYNAMIC -> PaletteSource.Dynamic
        raw.startsWith(PRESET_PREFIX) -> decodePreset(raw.removePrefix(PRESET_PREFIX))
        else -> null
    }

    private fun decodePreset(seedName: String): PaletteSource? =
        AccentSeed.entries.firstOrNull { it.name == seedName }?.let(PaletteSource::Preset)

    /**
     * Enumerated rather than free text: `PRESET:<seed>` is exactly the format that makes typing a
     * raw override a routine way to fail, so the debug panel gets a picker instead of a text field.
     */
    override val valueKind: ConfigValueKind = ConfigValueKind.Choice(
        listOf(BRAND, DYNAMIC) + AccentSeed.entries.map { PRESET_PREFIX + it.name },
    )
}

/**
 * Delegates to the existing flat layout encoding. An empty string is reported as absent, not as an
 * empty layout: "never customised" is what the key default already means.
 */
internal object DashboardLayoutConfigCodec : ConfigCodec<DashboardLayoutConfig> {

    override fun encode(value: DashboardLayoutConfig): String = DashboardLayoutCodec.encode(value)

    override fun decode(raw: String): DashboardLayoutConfig? =
        if (raw.isEmpty()) null else DashboardLayoutCodec.decode(raw)

    override val valueKind: ConfigValueKind = ConfigValueKind.FreeText
}
