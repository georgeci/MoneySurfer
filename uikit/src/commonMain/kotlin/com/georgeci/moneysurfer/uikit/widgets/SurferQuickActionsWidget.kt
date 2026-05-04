package com.georgeci.moneysurfer.uikit.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.SurferButton
import com.georgeci.moneysurfer.uikit.components.SurferButtonSize
import com.georgeci.moneysurfer.uikit.components.SurferButtonStyle
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview

@Composable
fun SurferQuickActionsWidget(
    primaryLabel: String,
    primaryIcon: ImageVector,
    onPrimaryClick: () -> Unit,
    secondaryLabel: String,
    secondaryIcon: ImageVector,
    onSecondaryClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: SurferWidgetSize = LocalSurferWidgetSize.current,
) {
    val hero = size == SurferWidgetSize.Hero
    val buttonSize = if (hero) SurferButtonSize.Biggest else SurferButtonSize.Regular
    val gap = if (hero) 10.dp else 8.dp

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        SurferButton(
            text = primaryLabel,
            onClick = onPrimaryClick,
            modifier = Modifier.weight(1f),
            style = SurferButtonStyle.Filled,
            size = buttonSize,
            startIcon = primaryIcon,
        )
        SurferButton(
            text = secondaryLabel,
            onClick = onSecondaryClick,
            modifier = Modifier.weight(1f),
            style = SurferButtonStyle.Tonal,
            size = buttonSize,
            startIcon = secondaryIcon,
        )
    }
}

@Preview
@Composable
private fun SurferQuickActionsHeroPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferQuickActionsWidget(
                primaryLabel = "Add transaction",
                primaryIcon = SurferIcons.Add,
                onPrimaryClick = {},
                secondaryLabel = "Transfer",
                secondaryIcon = Icons.Filled.SwapHoriz,
                onSecondaryClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
            )
        }
    }
}

@Preview
@Composable
private fun SurferQuickActionsCompactPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferQuickActionsWidget(
                primaryLabel = "Add",
                primaryIcon = SurferIcons.Add,
                onPrimaryClick = {},
                secondaryLabel = "Transfer",
                secondaryIcon = Icons.Filled.SwapHoriz,
                onSecondaryClick = {},
                size = SurferWidgetSize.Compact,
                modifier = Modifier
                    .width(260.dp)
                    .height(64.dp),
            )
        }
    }
}
