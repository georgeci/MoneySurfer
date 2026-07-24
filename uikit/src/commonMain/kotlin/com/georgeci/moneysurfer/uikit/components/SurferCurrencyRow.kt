package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.atom.SurferCard
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Selectable currency card: symbol, display name over the code, and a trailing check when
 * [selected]. Wraps [SurferCard] so the rows inherit the active container style like every
 * other list in the app; the check follows `LocalContentColor` because the filled selected
 * variant inverts the content colour.
 */
@Composable
fun SurferCurrencyRow(
    symbol: String,
    code: String,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SurferCard(modifier = modifier.fillMaxWidth(), selected = selected, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = symbol,
                style = AppTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = AppTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = code,
                    style = AppTheme.typography.bodySmall,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
            if (selected) {
                Icon(
                    imageVector = SurferIcons.Check,
                    // decorative — selection state indicator; the currency name provides the accessible label
                    contentDescription = null,
                    tint = LocalContentColor.current,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Spacer(Modifier.width(20.dp))
            }
        }
    }
}

@Preview
@Composable
private fun SurferCurrencyRowPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SurferCurrencyRow(symbol = "€", code = "EUR", name = "Euro", selected = true, onClick = {})
            SurferCurrencyRow(symbol = "$", code = "USD", name = "US Dollar", selected = false, onClick = {})
        }
    }
}
