package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            SurferSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = searchPlaceholder,
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp),
            ) {
                items(filtered, key = { it.code }) { currency ->
                    SurferCurrencyRow(
                        symbol = currency.symbol,
                        code = currency.code,
                        name = currency.name,
                        selected = currency.code == selectedCode,
                        onClick = { onSelect(currency) },
                    )
                }
            }
        }
    }
}
