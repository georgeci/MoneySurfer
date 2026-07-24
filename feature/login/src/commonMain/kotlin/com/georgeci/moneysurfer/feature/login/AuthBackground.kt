package com.georgeci.moneysurfer.feature.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Fixed brand palette shared by the pre-auth screens (onboarding + sign-in). These screens stay
 * MoneySurfer green regardless of the user's accent seed — the theme picker only starts applying
 * once they're inside the app.
 */
internal object AuthPalette {
    val GreenTop = Color(0xFF7ED321)
    val GreenBottom = Color(0xFF5FB011)
    val Ink = Color(0xFF0F2E12)
    val OnBrand = Color(0xFFFFFFFF)
    val OnBrandMuted = Color(0xE6FFFFFF)
    val OnBrandSubtle = Color(0xCCFFFFFF)
    val Sheet = Color(0xFFFFFFFF)
    val SheetMuted = Color(0xFF445844)
    val SheetSubtle = Color(0xFF7A8C7B)
    val Divider = Color(0xFFD6E4D7)
    val PrimaryDark = Color(0xFF1B5E20)
    val GreenSoft = Color(0xFFE8F3E9)
    val Mint = Color(0xFF2E9A6A)
    val BrandTile = Color(0x29FFFFFF)
    val ProgressRest = Color(0x47FFFFFF)
    val WaveBack = Color(0xFF1B5E20)
    val WaveMid = Color(0xFF2E7D32)
    val WaveFront = Color(0xFF2E9A3E)
}

private val WaveBandHeight: Dp = 220.dp

private const val DefaultCp1X: Float = 0.22f
private const val DefaultCp1Y: Float = -30f
private const val DefaultCp2X: Float = 0.42f
private const val DefaultCp2Y: Float = 30f
private const val DefaultMidX: Float = 0.63f
private const val DefaultCp3X: Float = 0.78f
private const val DefaultCp3Y: Float = -25f
private const val DefaultCp4X: Float = 0.92f
private const val DefaultCp4Y: Float = 15f

private data class WaveCrest(
    val startY: Float,
    val midY: Float,
    val endY: Float,
    val cp1X: Float = DefaultCp1X,
    val cp1Y: Float = DefaultCp1Y,
    val cp2X: Float = DefaultCp2X,
    val cp2Y: Float = DefaultCp2Y,
    val midX: Float = DefaultMidX,
    val cp3X: Float = DefaultCp3X,
    val cp3Y: Float = DefaultCp3Y,
    val cp4X: Float = DefaultCp4X,
    val cp4Y: Float = DefaultCp4Y,
)

private val BackCrest = WaveCrest(startY = 0.40f, midY = 0.60f, endY = 0.50f)
private val MidCrest = WaveCrest(startY = 0.60f, midY = 0.78f, endY = 0.65f)
private const val BackCrestAlpha: Float = 0.55f
private const val MidCrestAlpha: Float = 0.85f
private const val FrontCrestStartY: Float = 0.82f
private const val FrontCp1X: Float = 0.20f
private const val FrontCp1Y: Float = 0.74f
private const val FrontCp2X: Float = 0.45f
private const val FrontCp2Y: Float = 0.93f
private const val FrontMidX: Float = 0.66f
private const val FrontMidY: Float = 0.84f
private const val FrontCp3X: Float = 0.83f
private const val FrontCp3Y: Float = 0.76f
private const val FrontCp4X: Float = 0.95f
private const val FrontCp4Y: Float = 0.88f
private const val FrontEndY: Float = 0.82f
private const val FrontGradientStartY: Float = 0.74f
private const val FrontGradientAlpha: Float = 0.55f

/**
 * The green gradient + wave band behind every pre-auth screen. Draw it edge to edge (outside of
 * [com.georgeci.moneysurfer.uikit.modifier.surferSafeInsets]) so it tints the status bar and the
 * navigation bar / gesture ribbon as well.
 */
@Composable
internal fun AuthBackground(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(AuthPalette.GreenTop, AuthPalette.GreenBottom),
            ),
        ),
    ) {
        WaveDecoration(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(WaveBandHeight),
        )
    }
}

@Composable
private fun WaveDecoration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        fun crestPath(crest: WaveCrest): Path {
            val startY = h * crest.startY
            val midY = h * crest.midY
            val endY = h * crest.endY
            return Path().apply {
                moveTo(0f, startY)
                cubicTo(
                    w * crest.cp1X,
                    startY + crest.cp1Y,
                    w * crest.cp2X,
                    midY + crest.cp2Y,
                    w * crest.midX,
                    midY,
                )
                cubicTo(
                    w * crest.cp3X,
                    midY + crest.cp3Y,
                    w * crest.cp4X,
                    endY + crest.cp4Y,
                    w,
                    endY,
                )
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
        }

        drawPath(
            path = crestPath(BackCrest),
            color = AuthPalette.WaveBack,
            alpha = BackCrestAlpha,
        )
        drawPath(
            path = crestPath(MidCrest),
            color = AuthPalette.WaveMid,
            alpha = MidCrestAlpha,
        )
        drawPath(
            path = Path().apply {
                moveTo(0f, h * FrontCrestStartY)
                cubicTo(
                    w * FrontCp1X,
                    h * FrontCp1Y,
                    w * FrontCp2X,
                    h * FrontCp2Y,
                    w * FrontMidX,
                    h * FrontMidY,
                )
                cubicTo(
                    w * FrontCp3X,
                    h * FrontCp3Y,
                    w * FrontCp4X,
                    h * FrontCp4Y,
                    w,
                    h * FrontEndY,
                )
                lineTo(w, h)
                lineTo(0f, h)
                close()
            },
            brush = Brush.verticalGradient(
                colors = listOf(
                    AuthPalette.WaveFront.copy(alpha = 0f),
                    AuthPalette.WaveFront.copy(alpha = FrontGradientAlpha),
                ),
                startY = h * FrontGradientStartY,
                endY = h,
            ),
        )
    }
}
