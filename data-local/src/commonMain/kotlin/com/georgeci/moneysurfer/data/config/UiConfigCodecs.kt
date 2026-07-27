package com.georgeci.moneysurfer.data.config

import com.georgeci.moneysurfer.appconfig.ConfigCodec
import com.georgeci.moneysurfer.appconfig.ConfigValueKind
import com.georgeci.moneysurfer.data.preferences.DashboardLayoutCodec
import com.georgeci.moneysurfer.domain.dashboard.DashboardLayoutConfig
import com.georgeci.moneysurfer.domain.preferences.AccentSeed
import com.georgeci.moneysurfer.domain.preferences.PaletteSource
import com.georgeci.moneysurfer.domain.primitives.CurrencyCode

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
 * Delegates to the existing flat layout encoding, through the nullable entry point.
 *
 * `DashboardLayoutCodec.decode` substitutes the default layout for anything it cannot parse, which
 * is the right behaviour for a direct read but wrong here: a value the engine cannot decode has to
 * be reported as undecodable so the layer loses and the panel can show the raw string. A decoded
 * default would win and look deliberate.
 */
internal object DashboardLayoutConfigCodec : ConfigCodec<DashboardLayoutConfig> {

    override fun encode(value: DashboardLayoutConfig): String = DashboardLayoutCodec.encode(value)

    override fun decode(raw: String): DashboardLayoutConfig? = DashboardLayoutCodec.decodeOrNull(raw)

    override val valueKind: ConfigValueKind = ConfigValueKind.FreeText
}

/**
 * Bare ISO-4217 alphabetic code, e.g. `EUR`.
 *
 * Free text rather than a choice: the currency catalogue is data, not a compile-time enum, so the
 * codec cannot list the accepted values. It still validates the *shape* — three letters, upcased —
 * because a `CurrencyCode` is used unchecked as a formatter key, and reporting garbage as
 * undecodable lets the layer below win instead of persisting a code nothing can render.
 */
internal object CurrencyCodeCodec : ConfigCodec<CurrencyCode> {

    private const val ISO_4217_LENGTH = 3

    override fun encode(value: CurrencyCode): String = value.value

    override fun decode(raw: String): CurrencyCode? = raw.trim()
        .takeIf { it.length == ISO_4217_LENGTH && it.all(Char::isLetter) }
        ?.let { CurrencyCode(it.uppercase()) }

    override val valueKind: ConfigValueKind = ConfigValueKind.FreeText
}
