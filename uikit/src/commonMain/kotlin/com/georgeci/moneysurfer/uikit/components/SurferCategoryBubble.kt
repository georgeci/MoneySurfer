package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview

/**
 * Circular bubble used to represent a category across the app (dashboard rows, transaction
 * details, pickers, creation preview). Foreground keeps the full category tint; background
 * is a low-alpha wash of the same tint, so light/dark themes both read correctly.
 */
@Composable
fun SurferCategoryBubble(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            // decorative — category colour/icon indicator; the surrounding text provides the accessible label
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/**
 * Catalogue of category tints + icons. Matches the design's 8-hue palette and its 12-slot
 * icon grid (Groceries, Dining, Transport, etc.). Lookup helpers derive a stable tint/icon
 * from any `Long` identifier so categories that only carry a name/id still render
 * consistently until proper icon/hue fields land on the domain model.
 */
object SurferCategoryPalette {
    val tints: List<Color> = listOf(
        Color(0xFF2F8E6E), // mint   — hue 162
        Color(0xFFB5533B), // amber  — hue  35
        Color(0xFF2E5AA8), // indigo — hue 258
        Color(0xFFA06A1E), // citrus — hue  68
        Color(0xFF6B4E99), // plum   — hue 303
        Color(0xFFB54A6B), // rose   — hue 340
        Color(0xFFB54744), // clay   — hue   8
        Color(0xFF6B7D3D), // moss   — hue  90
    )

    val icons: List<ImageVector> = listOf(
        SurferIcons.Category,
        SurferIcons.CreditCard,
        SurferIcons.Savings,
        SurferIcons.Cash,
        SurferIcons.Wallet,
        SurferIcons.Receipt,
        SurferIcons.Event,
        SurferIcons.Tag,
    )

    /** Marker for the seeded system Transfer category — matches `CategorySystemKind.TRANSFER.name`. */
    const val SYSTEM_KIND_TRANSFER: String = "TRANSFER"

    /** Tint for the system Transfer category. Kept fixed (not hashed) so it's always recognisable. */
    val TransferTint: Color = Color(0xFF2E5AA8)
    val TransferIcon: ImageVector = SurferIcons.SwapHoriz

    fun tintFor(id: String, systemKind: String? = null): Color {
        if (systemKind == SYSTEM_KIND_TRANSFER) return TransferTint
        return tints[nonNegativeMod(stringHash(id), tints.size)]
    }

    fun iconFor(id: String, systemKind: String? = null): ImageVector {
        if (systemKind == SYSTEM_KIND_TRANSFER) return TransferIcon
        return icons[nonNegativeMod(stringHash(id), icons.size)]
    }

    fun tintForName(name: String): Color =
        if (name.isEmpty()) {
            tints[0]
        } else {
            tints[
                nonNegativeMod(
                    name.fold(0L) { acc, c -> acc * 31 + c.code },
                    tints.size,
                ),
            ]
        }

    private fun nonNegativeMod(value: Long, size: Int): Int {
        val s = size.toLong()
        val r = value % s
        return ((if (r < 0) r + s else r)).toInt()
    }

    private fun stringHash(value: String): Long =
        value.fold(0L) { acc, c -> acc * 31 + c.code }
}

@Preview
@Composable
private fun SurferCategoryBubblePreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SurferCategoryPalette.tints.zip(SurferCategoryPalette.icons).forEach { (tint, icon) ->
                    SurferCategoryBubble(icon = icon, tint = tint)
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SurferCategoryBubble(
                    icon = SurferIcons.Category,
                    tint = SurferCategoryPalette.tints[0],
                    size = 32.dp,
                )
                SurferCategoryBubble(
                    icon = SurferIcons.Category,
                    tint = SurferCategoryPalette.tints[0],
                    size = 44.dp,
                )
                SurferCategoryBubble(
                    icon = SurferIcons.Category,
                    tint = SurferCategoryPalette.tints[0],
                    size = 56.dp,
                )
                SurferCategoryBubble(
                    icon = SurferIcons.Category,
                    tint = SurferCategoryPalette.tints[0],
                    size = 72.dp,
                )
            }
        }
    }
}
