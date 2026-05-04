package com.georgeci.moneysurfer.uikit.components.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

private val SquircleShape = RoundedCornerShape(percent = 35)

/**
 * Stateless transaction line per the design's transaction line:
 * - 48dp squircle leading tile with optional category-hue ring.
 * - Title (note / merchant) on the first line; optional meta line with a leading colored dot
 *   plus a single string the caller composes (category, account, etc).
 * - Amount stack on the trailing edge with optional date sub-line. Pass
 *   [amountPillBackground] when the row is income — the amount text gets a soft tinted pill;
 *   leave `null` and the row reads bare numerals.
 *
 * Lives in uikit without coupling to feature data — caller maps the domain model to the
 * visual slots and pre-formats the amount string.
 */
@Composable
fun SurferTransactionLine(
    icon: ImageVector,
    title: String,
    formattedAmount: String,
    modifier: Modifier = Modifier,
    categoryHueSeed: String? = null,
    meta: String? = null,
    metaDotColor: Color? = null,
    iconTint: Color = AppTheme.materialColors.onSurfaceVariant,
    iconBackground: Color = AppTheme.materialColors.surfaceContainerHighest,
    iconRingColor: Color = Color.Transparent,
    amountColor: Color = AppTheme.materialColors.onSurface,
    amountPillBackground: Color? = null,
    dateLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val categorySeed = categoryHueSeed?.takeIf { it.isNotBlank() }
    val categoryTint = categorySeed?.let(SurferCategoryPalette::tintFor)
    val leadingIcon = categorySeed?.let(SurferCategoryPalette::iconFor) ?: icon
    val leadingIconTint = categoryTint ?: iconTint
    val leadingIconBackground = categoryTint?.copy(alpha = 0.18f) ?: iconBackground

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(SquircleShape)
                .background(leadingIconBackground)
                .then(
                    if (iconRingColor != Color.Transparent) {
                        Modifier.border(1.dp, iconRingColor, SquircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = leadingIconTint,
                modifier = Modifier.size(22.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = AppTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = AppTheme.materialColors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!meta.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (metaDotColor != null) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(metaDotColor),
                        )
                    }
                    Text(
                        text = meta,
                        style = AppTheme.typography.bodySmall,
                        color = AppTheme.materialColors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val amountStyle = AppTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            if (amountPillBackground != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(amountPillBackground)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    Text(text = formattedAmount, style = amountStyle, color = amountColor)
                }
            } else {
                Text(text = formattedAmount, style = amountStyle, color = amountColor)
            }
            if (!dateLabel.isNullOrBlank()) {
                Text(
                    text = dateLabel,
                    style = AppTheme.typography.labelSmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SurferTransactionLinePreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SurferTransactionLine(
                icon = SurferIcons.Receipt,
                title = "Lidl — weekly shop",
                categoryHueSeed = "groceries",
                meta = "Groceries",
                formattedAmount = "−€48.20",
                dateLabel = "Today",
                onClick = {},
            )
            SurferTransactionLine(
                icon = SurferIcons.Wallet,
                title = "Salary",
                meta = "Income · Everyday",
                metaDotColor = Color(0xFF2EB46A),
                formattedAmount = "+€3,200.00",
                amountColor = Color(0xFF2E9A6A),
                amountPillBackground = Color(0x332EB46A),
                dateLabel = "Apr 1",
                onClick = {},
            )
        }
    }
}
