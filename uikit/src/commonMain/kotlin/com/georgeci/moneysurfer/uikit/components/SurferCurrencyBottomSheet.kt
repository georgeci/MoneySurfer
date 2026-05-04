package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme

data class SurferCurrencyOption(
    val code: String,
    val symbol: String,
    val name: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurferCurrencyBottomSheet(
    title: String,
    searchPlaceholder: String,
    currencies: List<SurferCurrencyOption>,
    selectedCode: String?,
    onSelect: (SurferCurrencyOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(currencies, query) {
        if (query.isBlank()) {
            currencies
        } else {
            val q = query.trim().lowercase()
            currencies.filter { c ->
                c.code.lowercase().contains(q) ||
                    c.name.lowercase().contains(q) ||
                    c.symbol.contains(q)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppTheme.materialColors.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp),
        ) {
            Text(
                text = title,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.materialColors.onSurface,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(searchPlaceholder) },
                leadingIcon = {
                    Icon(
                        imageVector = SurferIcons.Search,
                        contentDescription = null,
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AppTheme.materialColors.surfaceContainerHigh,
                    unfocusedContainerColor = AppTheme.materialColors.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .heightIn(min = 44.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
            ) {
                items(filtered, key = { it.code }) { currency ->
                    CurrencyRow(
                        currency = currency,
                        selected = currency.code == selectedCode,
                        onClick = { onSelect(currency) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrencyRow(
    currency: SurferCurrencyOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val highlight = AppTheme.materialColors.primary.copy(alpha = 0.10f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) highlight else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(AppTheme.materialColors.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = currency.symbol,
                style = AppTheme.typography.titleMedium,
                color = AppTheme.materialColors.onSurface,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currency.code,
                style = AppTheme.typography.titleSmall,
                color = AppTheme.materialColors.onSurface,
            )
            Text(
                text = currency.name,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.materialColors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Icon(
                imageVector = SurferIcons.Check,
                contentDescription = null,
                tint = AppTheme.materialColors.primary,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Spacer(Modifier.width(20.dp))
        }
    }
}
