package com.georgeci.moneysurfer.domain.model

import com.georgeci.moneysurfer.domain.primitives.CategorySystemKind

/**
 * Semantic catalogue behind a category's stored `iconKey` and `hue`.
 *
 * Both are stored as *values*, never as palette indices: an index is positional, so inserting
 * one icon into the middle of the picker grid would silently re-icon and recolour every
 * category already saved on every device.
 *
 * The catalogue also supplies the deterministic default a category falls back to when it
 * carries no stored appearance — a row written by an older client, or the additive
 * `iconKey`/`hue` backfill in the Room migration. Deriving both from a stable hash of the
 * category id keeps pre-existing data varied (a blank backfill would render every category in
 * one identical colour) and stable across devices, and reproduces exactly what the UI drew
 * before the fields existed.
 */
object CategoryAppearance {

    /** Design palette hues in degrees, in colour-picker display order. */
    val HUES: List<Int> = listOf(162, 35, 258, 68, 303, 340, 8, 90)

    /** Icon keys in icon-picker display order. Positionally paired with [HUES] for defaults. */
    val ICON_KEYS: List<String> = listOf(
        "category",
        "card",
        "savings",
        "cash",
        "wallet",
        "receipt",
        "event",
        "tag",
    )

    /** Appearance of the seeded system Transfer category. Fixed, so it is always recognisable. */
    const val TRANSFER_ICON_KEY: String = "transfer"
    const val TRANSFER_HUE: Int = 258

    /** Sentinel stored by rows that predate the fields — resolved through [defaultHue]. */
    const val UNSET_HUE: Int = -1

    /** Exclusive upper bound of the hue circle. */
    const val HUE_DEGREES: Int = 360

    /** Whether [hue] is a usable stored value rather than the [UNSET_HUE] sentinel or junk. */
    fun isStoredHue(hue: Int): Boolean = hue in 0 until HUE_DEGREES

    /** Whether [iconKey] is a usable stored value rather than the blank sentinel. */
    fun isStoredIconKey(iconKey: String): Boolean = iconKey.isNotBlank()

    /**
     * Deterministic icon key for a category that has none stored, derived from its id so the
     * choice is stable across devices and identical to what the pre-field UI rendered.
     */
    fun defaultIconKey(id: String, systemKind: CategorySystemKind? = null): String =
        if (systemKind == CategorySystemKind.TRANSFER) TRANSFER_ICON_KEY else ICON_KEYS[paletteSlot(id)]

    /** Deterministic hue for a category that has none stored. See [defaultIconKey]. */
    fun defaultHue(id: String, systemKind: CategorySystemKind? = null): Int =
        if (systemKind == CategorySystemKind.TRANSFER) TRANSFER_HUE else HUES[paletteSlot(id)]

    /**
     * Palette slot an id hashes into. Both defaults read the same slot, so a backfilled
     * category keeps the icon/colour pairing the old index-based rendering gave it.
     */
    fun paletteSlot(id: String): Int = nonNegativeMod(stableHash(id), HUES.size)

    /**
     * Stable string hash. Deliberately spelled out rather than delegating to `String.hashCode()`,
     * which carries no cross-platform guarantee — a backfilled category must land in the same
     * slot on Android and iOS or the same workspace renders two different colours.
     */
    fun stableHash(value: String): Long = value.fold(0L) { acc, c -> acc * HASH_MULTIPLIER + c.code }

    private fun nonNegativeMod(value: Long, size: Int): Int {
        val bound = size.toLong()
        val rem = value % bound
        return (if (rem < 0) rem + bound else rem).toInt()
    }

    private const val HASH_MULTIPLIER = 31L
}
