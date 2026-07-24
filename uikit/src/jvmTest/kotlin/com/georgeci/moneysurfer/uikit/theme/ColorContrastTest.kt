package com.georgeci.moneysurfer.uikit.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.1 AA contrast gate for the static light/dark schemes (issue #77).
 *
 * Reads the real [LightColorScheme] / [DarkColorScheme] rather than a copy of the hexes, so
 * the surface containers Material fills in for us (`lightColorScheme()` leaves them at the
 * baseline tonal palette — we never override them) are the ones actually checked.
 *
 * Two things this deliberately does *not* assert:
 *  - `outlineVariant`, which sits at ~1.3–1.9:1 against every surface in both themes. It is
 *    M3's hairline-divider token and dividers are decorative, so SC 1.4.11 does not apply.
 *    `outline`, which does bound real controls, is checked at the 3:1 non-text threshold.
 *  - Seeded / dynamic schemes, which `material-kolor` derives at runtime and which carry
 *    their own tonal guarantees.
 *
 * Only the [SemanticColors] accents needed changing: they are the app's own tokens and had
 * no tonal-palette guarantee behind them.
 */
class ColorContrastTest : StringSpec({

    "light scheme: text tokens clear AA on every surface they land on" {
        LightColorScheme.assertTextContrast(LightSemanticColors)
    }

    "dark scheme: text tokens clear AA on every surface they land on" {
        DarkColorScheme.assertTextContrast(DarkSemanticColors)
    }

    "light scheme: paired on/container tokens clear AA" {
        LightColorScheme.assertContainerContrast()
    }

    "dark scheme: paired on/container tokens clear AA" {
        DarkColorScheme.assertContainerContrast()
    }

    // The transaction line's income amount and the creation screen's type pill both draw
    // accent-coloured text on a low-alpha wash of that same accent. Both call sites composite
    // the wash over `surface`, so the ratio is fixed rather than inherited from the parent.
    "light scheme: self-tinted amount and type pills clear AA" {
        LightColorScheme.assertPillContrast(LightSemanticColors)
    }

    "dark scheme: self-tinted amount and type pills clear AA" {
        DarkColorScheme.assertPillContrast(DarkSemanticColors)
    }
})

/** WCAG SC 1.4.3 — normal-size body text. */
private const val AA_TEXT = 4.5

/** WCAG SC 1.4.11 — non-text UI component boundaries. */
private const val AA_NON_TEXT = 3.0

/** `SurferRecentTransactionsWidget`'s income amount pill. */
private const val AMOUNT_PILL_ALPHA = 0.14f

/** `TransactionCreationScreen`'s type pill. */
private const val TYPE_PILL_ALPHA = 0.18f

/** Every background a body-text token can end up on, worst (darkest in light mode) last. */
private fun ColorScheme.surfaces(): List<Pair<String, Color>> = listOf(
    "surface" to surface,
    "surfaceContainerLow" to surfaceContainerLow,
    "surfaceContainer" to surfaceContainer,
    "surfaceContainerHigh" to surfaceContainerHigh,
    "surfaceContainerHighest" to surfaceContainerHighest,
)

private fun ColorScheme.assertTextContrast(semantic: SemanticColors) {
    val foregrounds = listOf(
        "onSurface" to onSurface,
        "onSurfaceVariant" to onSurfaceVariant,
        "primary" to primary,
        "error" to error,
        "semantic.income" to semantic.income,
        "semantic.expense" to semantic.expense,
        "semantic.transfer" to semantic.transfer,
        "semantic.warning" to semantic.warning,
    )
    for ((fgName, fg) in foregrounds) {
        for ((bgName, bg) in surfaces()) {
            assertContrast(fg, bg, AA_TEXT, "$fgName on $bgName")
        }
    }
    for ((bgName, bg) in surfaces()) {
        assertContrast(outline, bg, AA_NON_TEXT, "outline on $bgName")
    }
}

private fun ColorScheme.assertContainerContrast() {
    val pairs = listOf(
        Triple("onBackground/background", onBackground, background),
        Triple("onPrimary/primary", onPrimary, primary),
        Triple("onPrimaryContainer/primaryContainer", onPrimaryContainer, primaryContainer),
        Triple("onSecondaryContainer/secondaryContainer", onSecondaryContainer, secondaryContainer),
        Triple("onTertiaryContainer/tertiaryContainer", onTertiaryContainer, tertiaryContainer),
        Triple("onError/error", onError, error),
        Triple("onErrorContainer/errorContainer", onErrorContainer, errorContainer),
        Triple("inverseOnSurface/inverseSurface", inverseOnSurface, inverseSurface),
    )
    for ((label, fg, bg) in pairs) {
        assertContrast(fg, bg, AA_TEXT, label)
    }
}

private fun ColorScheme.assertPillContrast(semantic: SemanticColors) {
    val accents = listOf(
        "income" to semantic.income,
        "expense" to semantic.expense,
        "transfer" to semantic.transfer,
    )
    for ((name, accent) in accents) {
        for (alpha in listOf(AMOUNT_PILL_ALPHA, TYPE_PILL_ALPHA)) {
            val wash = accent.copy(alpha = alpha).compositeOver(surface)
            assertContrast(accent, wash, AA_TEXT, "$name on its own $alpha wash over surface")
        }
    }
}

private fun assertContrast(foreground: Color, background: Color, minimum: Double, label: String) {
    val ratio = contrastRatio(foreground, background)
    withClue(
        "$label — ${foreground.hex()} on ${background.hex()} is " +
            "${"%.2f".format(ratio)}:1, needs $minimum:1",
    ) {
        ratio shouldBeGreaterThanOrEqual minimum
    }
}

/** WCAG 2.1 relative-luminance contrast ratio, `(L1 + 0.05) / (L2 + 0.05)`. */
private fun contrastRatio(a: Color, b: Color): Double {
    val la = a.relativeLuminance()
    val lb = b.relativeLuminance()
    return (max(la, lb) + LUMINANCE_OFFSET) / (min(la, lb) + LUMINANCE_OFFSET)
}

private const val LUMINANCE_OFFSET = 0.05
private const val SRGB_LINEAR_CUTOFF = 0.03928
private const val SRGB_LINEAR_DIVISOR = 12.92
private const val SRGB_GAMMA_OFFSET = 0.055
private const val SRGB_GAMMA_SCALE = 1.055
private const val SRGB_GAMMA = 2.4
private const val LUMA_R = 0.2126
private const val LUMA_G = 0.7152
private const val LUMA_B = 0.0722

private fun Color.relativeLuminance(): Double =
    LUMA_R * linearise(red) + LUMA_G * linearise(green) + LUMA_B * linearise(blue)

private fun linearise(channel: Float): Double {
    val c = channel.toDouble()
    return if (c <= SRGB_LINEAR_CUTOFF) {
        c / SRGB_LINEAR_DIVISOR
    } else {
        ((c + SRGB_GAMMA_OFFSET) / SRGB_GAMMA_SCALE).pow(SRGB_GAMMA)
    }
}

private const val HEX_BYTE = 255f

private fun Color.hex(): String = "#%02X%02X%02X".format(
    (red * HEX_BYTE).toInt(),
    (green * HEX_BYTE).toInt(),
    (blue * HEX_BYTE).toInt(),
)
