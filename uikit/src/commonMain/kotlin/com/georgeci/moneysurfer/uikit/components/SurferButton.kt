package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

enum class SurferButtonStyle {
    Filled,
    Tonal,
    Outlined,
    Text,
}

enum class SurferButtonSize(val height: Int) {
    Biggest(52),
    Regular(48),
    Small(36),
}

@Composable
fun SurferButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SurferButtonStyle = SurferButtonStyle.Filled,
    size: SurferButtonSize = SurferButtonSize.Regular,
    enabled: Boolean = true,
    startIcon: ImageVector? = null,
    endIcon: ImageVector? = null,
) {
    val sizedModifier = modifier.height(size.height.dp)
    // Match SurferCard's corner radius so a button placed alongside a card row reads as
    // the same family. AppTheme.shapes.large == RoundedCornerShape(16.dp).
    val shape = AppTheme.shapes.large

    when (style) {
        SurferButtonStyle.Filled -> Button(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = shape,
        ) {
            SurferButtonContent(
                text = text,
                startIcon = startIcon,
                endIcon = endIcon,
            )
        }

        SurferButtonStyle.Tonal -> FilledTonalButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = shape,
        ) {
            SurferButtonContent(
                text = text,
                startIcon = startIcon,
                endIcon = endIcon,
            )
        }

        SurferButtonStyle.Outlined -> OutlinedButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = shape,
        ) {
            SurferButtonContent(
                text = text,
                startIcon = startIcon,
                endIcon = endIcon,
            )
        }

        SurferButtonStyle.Text -> TextButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = shape,
        ) {
            SurferButtonContent(
                text = text,
                startIcon = startIcon,
                endIcon = endIcon,
            )
        }
    }
}

@Composable
private fun SurferButtonContent(
    text: String,
    startIcon: ImageVector?,
    endIcon: ImageVector?,
) {
    Row {
        if (startIcon != null) {
            Icon(
                imageVector = startIcon,
                // decorative — button text provides the accessible label
                contentDescription = null,
            )
            Spacer(Modifier.width(AppTheme.spacing.small))
        }

        Text(text)

        if (endIcon != null) {
            Spacer(Modifier.width(AppTheme.spacing.small))
            Icon(
                imageVector = endIcon,
                // decorative — button text provides the accessible label
                contentDescription = null,
            )
        }
    }
}

// One preview per [SurferButtonStyle] showing both [SurferButtonSize] heights side-by-side
// plus an icons + disabled row. Compact matrix — pick the variant you need without opening
// a dozen previews.

@Preview
@Composable
private fun SurferButtonFilledPreview() {
    SurferComponentPreview { ButtonStyleMatrix(SurferButtonStyle.Filled, "Save") }
}

@Preview
@Composable
private fun SurferButtonTonalPreview() {
    SurferComponentPreview { ButtonStyleMatrix(SurferButtonStyle.Tonal, "Tonal") }
}

@Preview
@Composable
private fun SurferButtonOutlinedPreview() {
    SurferComponentPreview { ButtonStyleMatrix(SurferButtonStyle.Outlined, "Cancel") }
}

@Preview
@Composable
private fun SurferButtonTextPreview() {
    SurferComponentPreview { ButtonStyleMatrix(SurferButtonStyle.Text, "Skip") }
}

@Composable
private fun ButtonStyleMatrix(style: SurferButtonStyle, label: String) {
    Column(
        modifier = Modifier.padding(16.dp),
    ) {
        SurferButtonSize.entries.forEach { size ->
            SurferButton(
                text = "$label · ${size.name}",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                style = style,
                size = size,
            )
            Spacer(Modifier.height(8.dp))
        }
        SurferButton(
            text = "With icons",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            style = style,
            startIcon = SurferIcons.Check,
            endIcon = SurferIcons.ChevronRight,
        )
        Spacer(Modifier.height(8.dp))
        SurferButton(
            text = "Disabled",
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
            style = style,
            enabled = false,
        )
    }
}
