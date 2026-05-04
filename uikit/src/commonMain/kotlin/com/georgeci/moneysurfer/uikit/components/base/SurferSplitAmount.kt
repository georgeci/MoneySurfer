package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/** Size tier for [SurferSplitAmount]. Mirrors the design system's hero / stat / body sizes. */
enum class SurferSplitAmountTier(
    internal val integerSize: TextUnit,
    internal val signSize: TextUnit,
    internal val fractionSize: TextUnit,
    internal val letterSpacing: TextUnit,
) {
    Hero(integerSize = 56.sp, signSize = 20.sp, fractionSize = 22.sp, letterSpacing = (-1.8).sp),
    Stat(integerSize = 32.sp, signSize = 14.sp, fractionSize = 14.sp, letterSpacing = (-0.8).sp),
    Body(integerSize = 22.sp, signSize = 12.sp, fractionSize = 12.sp, letterSpacing = (-0.4).sp),
}

/**
 * Renders a monetary amount with a superscripted currency sign, large integer part, and smaller
 * fractional decimals. Mirrors the dashboard "Total balance" hero treatment so every balance in
 * the app reads the same.
 *
 * Feed it a pre-formatted string such as "€2,480.32" or "−€48.20" — the leading sign/currency
 * prefix, integer portion, and fractional portion are split automatically.
 */
@Composable
fun SurferSplitAmount(
    formattedAmount: String,
    modifier: Modifier = Modifier,
    tier: SurferSplitAmountTier = SurferSplitAmountTier.Hero,
    color: Color = LocalContentColor.current,
    signAlpha: Float = 0.7f,
    fractionAlpha: Float = 0.6f,
) {
    val parts = rememberSplitAmount(formattedAmount)
    val annotated = remember(parts, color, signAlpha, fractionAlpha, tier) {
        buildAnnotatedString {
            if (parts.sign.isNotEmpty()) {
                withStyle(
                    SpanStyle(
                        fontSize = tier.signSize,
                        fontWeight = FontWeight.Medium,
                        color = color.copy(alpha = signAlpha),
                        baselineShift = BaselineShift(0F),
                    ),
                ) { append(parts.sign) }
            }
            withStyle(
                SpanStyle(
                    fontSize = tier.integerSize,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = tier.letterSpacing,
                    color = color,
                ),
            ) { append(parts.integer) }
            withStyle(
                SpanStyle(
                    fontSize = tier.fractionSize,
                    fontWeight = FontWeight.Medium,
                    color = color.copy(alpha = fractionAlpha),
                    baselineShift = BaselineShift(0F),
                ),
            ) {
                append(parts.delimiter)
                append(parts.fraction)
            }
        }
    }
    Text(
        text = annotated,
        modifier = modifier,
        maxLines = 1,
        style = AppTheme.typography.displayLarge.copy(lineHeight = 1.em),
    )
}

internal data class SplitAmountParts(
    val sign: String,
    val integer: String,
    val fraction: String,
    val delimiter: Char,
)

@Composable
private fun rememberSplitAmount(formatted: String): SplitAmountParts =
    remember(formatted) { splitForDisplay(formatted) }

internal fun splitForDisplay(raw: String): SplitAmountParts {
    val trimmed = raw.trim()
    val negative = trimmed.startsWith('-') || trimmed.startsWith('−')
    val withoutMinus = if (negative) trimmed.substring(1).trimStart() else trimmed
    val firstDigit = withoutMinus.indexOfFirst { it.isDigit() }
    val currencyPrefix = if (firstDigit >= 0) withoutMinus.substring(0, firstDigit) else ""
    val numberPart = if (firstDigit >= 0) withoutMinus.substring(firstDigit) else withoutMinus

    val dotIndex = numberPart.indexOfLast { it == '.' || it == ',' }
        .takeIf { it >= 0 && numberPart.length - it - 1 in 1..2 } ?: -1
    val integer = if (dotIndex >= 0) numberPart.substring(0, dotIndex) else numberPart
    val fraction = if (dotIndex >= 0) numberPart.substring(dotIndex + 1).padEnd(2, '0') else "00"
    val delimiter = if (dotIndex >= 0) numberPart[dotIndex] else '.'

    val sign = buildString {
        if (negative) append('−')
        append(currencyPrefix.trim())
    }
    return SplitAmountParts(
        sign = sign,
        integer = integer,
        fraction = fraction,
        delimiter = delimiter,
    )
}

@Preview
@Composable
private fun SurferSplitAmountPreview() {
    SurferComponentPreview {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SurferSplitAmount("€2.480.32", tier = SurferSplitAmountTier.Hero)
            SurferSplitAmount("€3,200.00", tier = SurferSplitAmountTier.Stat)
            SurferSplitAmount("−€48.20", tier = SurferSplitAmountTier.Body)
        }
    }
}
