package com.georgeci.moneysurfer.uikit.components.category

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeci.moneysurfer.uikit.components.SurferCategoryBubble
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmount
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmountTier
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Hero block on the Category Details screen, washed in the category's own [tint].
 *
 * The tint arrives resolved rather than as a raw hue — [SurferCategoryPalette] owns the
 * hue-to-colour mapping, and taking a `Color` keeps this component usable for the system
 * Transfer category, whose colour is fixed rather than derived.
 *
 * The wash is a low-alpha gradient over the theme surface instead of a solid tonal container:
 * a category hue is user-chosen and arbitrary, so painting a whole card in it at full strength
 * would fight the theme and leave text at unpredictable contrast. Every glyph here stays on
 * theme colours; only the background carries the hue.
 */
@Composable
fun SurferCategoryHeroCard(
    icon: ImageVector,
    tint: Color,
    name: String,
    label: String,
    formattedAmount: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    listOf(
                        tint.copy(alpha = HeroTintAlpha),
                        AppTheme.materialColors.surface,
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = AppTheme.materialColors.outlineVariant,
                shape = AppTheme.shapes.large,
            ),
    ) {
        // Decorative tonal blob, clipped by the card. Anchored past the corner so only the
        // falloff shows, echoing the dashboard balance hero at a smaller scale.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = BlobOffset, y = -BlobOffset)
                .size(BlobSize)
                .clip(CircleShape)
                .background(tint.copy(alpha = BlobAlpha)),
        )

        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SurferCategoryBubble(icon = icon, tint = tint, size = 56.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = AppTheme.typography.labelMedium.copy(letterSpacing = 0.5.sp),
                        color = AppTheme.materialColors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = name,
                        style = AppTheme.typography.titleLarge,
                        color = AppTheme.materialColors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            SurferSplitAmount(
                formattedAmount = formattedAmount,
                tier = SurferSplitAmountTier.Hero,
                color = AppTheme.materialColors.onSurface,
            )

            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = AppTheme.typography.bodyMedium,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
            }
        }
    }
}

private const val HeroTintAlpha = 0.22f
private const val BlobAlpha = 0.18f
private val BlobSize = 160.dp
private val BlobOffset = 40.dp

@Preview
@Composable
private fun SurferCategoryHeroCardPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferCategoryHeroCard(
                icon = SurferIcons.Receipt,
                tint = SurferCategoryPalette.tints[5],
                name = "Dining",
                label = "SPENT IN APRIL",
                formattedAmount = "€168.55",
                subtitle = "14 transactions · 2 subcategories",
            )
            SurferCategoryHeroCard(
                icon = SurferIcons.Cash,
                tint = SurferCategoryPalette.tints[0],
                name = "Salary",
                label = "INCOME · APRIL",
                formattedAmount = "€3,200.00",
            )
        }
    }
}
