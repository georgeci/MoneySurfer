package com.georgeci.moneysurfer.data.preferences

import com.georgeci.moneysurfer.data.datastore.UiSettingsDataSource
import com.georgeci.moneysurfer.domain.preferences.AccentSeed
import com.georgeci.moneysurfer.domain.preferences.ContainerStyle
import com.georgeci.moneysurfer.domain.preferences.PaletteSource
import com.georgeci.moneysurfer.domain.preferences.Pref
import com.georgeci.moneysurfer.domain.preferences.ThemeMode
import com.georgeci.moneysurfer.domain.preferences.TransactionPeriodMode
import com.georgeci.moneysurfer.domain.preferences.UiPreferences
import org.koin.core.annotation.Single
import com.georgeci.moneysurfer.data.datastore.isDynamicColorAvailable as platformIsDynamicColorAvailable

@Single(binds = [UiPreferences::class])
class UiPreferencesImpl(
    src: UiSettingsDataSource,
) : UiPreferences {

    override val isDynamicColorAvailable: Boolean = platformIsDynamicColorAvailable

    override val paletteSource: Pref<PaletteSource> = src.paletteSource.asPref(
        fromStored = ::decodePaletteSource,
        toStored = ::encodePaletteSource,
    )

    override val themeMode: Pref<ThemeMode> = src.themeMode.asPref(
        fromStored = { stored -> runCatching { ThemeMode.valueOf(stored) }.getOrDefault(ThemeMode.System) },
        toStored = { mode -> mode.name },
    )

    override val containerStyle: Pref<ContainerStyle> = src.containerStyle.asPref(
        fromStored = { stored -> runCatching { ContainerStyle.valueOf(stored) }.getOrDefault(ContainerStyle.Card) },
        toStored = { style -> style.name },
    )

    override val transactionsPeriodMode: Pref<TransactionPeriodMode> = src.transactionsPeriodMode.asPref(
        fromStored = { stored ->
            runCatching { TransactionPeriodMode.valueOf(stored) }.getOrDefault(TransactionPeriodMode.DEFAULT)
        },
        toStored = { mode -> mode.name },
    )
}

/**
 * Encoding format:
 *  - "BRAND"
 *  - "PRESET:<seedName>"   e.g. "PRESET:Plum"
 *  - "DYNAMIC"
 */
private fun encodePaletteSource(source: PaletteSource): String = when (source) {
    PaletteSource.Brand -> "BRAND"
    PaletteSource.Dynamic -> "DYNAMIC"
    is PaletteSource.Preset -> "PRESET:${source.seed.name}"
}

private fun decodePaletteSource(stored: String): PaletteSource = when (stored) {
    "BRAND" -> PaletteSource.Brand
    "DYNAMIC" -> PaletteSource.Dynamic
    else -> decodePresetPaletteSource(stored)
}

private fun decodePresetPaletteSource(stored: String): PaletteSource {
    val parts = stored.split(':', limit = 2)
    if (parts.size != 2 || parts[0] != "PRESET") return PaletteSource.DEFAULT
    val seed = runCatching { AccentSeed.valueOf(parts[1]) }.getOrDefault(AccentSeed.Plum)
    return PaletteSource.Preset(seed)
}
