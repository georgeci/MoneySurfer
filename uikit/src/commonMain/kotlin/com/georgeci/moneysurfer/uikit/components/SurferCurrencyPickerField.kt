package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.semantics.SurferSemantics
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Outlined field that opens [SurferCurrencyBottomSheet]. Reads as a text field rather than a
 * card so it sits in a form next to the real [androidx.compose.material3.OutlinedTextField]s —
 * 56dp tall, 12dp radius, symbol badge, `{name} · {code}` value, trailing chevron. The outline
 * turns primary while [expanded] so the field keeps the focused-field look of the open sheet.
 */
@Composable
fun SurferCurrencyPickerField(
    symbol: String,
    code: String,
    name: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
) {
    val colors = AppTheme.materialColors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clip(shape)
            .border(1.dp, if (expanded) colors.primary else colors.outline, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = symbol,
            style = AppTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text = "$name · $code",
            style = AppTheme.typography.bodyLarge,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = SurferIcons.DropDown,
            contentDescription = SurferSemantics.Decorative,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Preview
@Composable
private fun SurferCurrencyPickerFieldPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferCurrencyPickerField(symbol = "€", code = "EUR", name = "Euro", onClick = {})
            SurferCurrencyPickerField(
                symbol = "zł",
                code = "PLN",
                name = "Polish Zloty",
                onClick = {},
                expanded = true,
            )
        }
    }
}
