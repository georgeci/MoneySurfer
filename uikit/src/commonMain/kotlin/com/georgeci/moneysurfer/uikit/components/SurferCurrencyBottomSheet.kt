package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.theme.AppTheme

data class SurferCurrencyOption(
    val code: String,
    val symbol: String,
    val name: String,
)

/**
 * Currency chooser as a modal bottom sheet: title, search field, and one [SurferCurrencyRow]
 * card per currency. The list is as tall as the whole currency set needs, capped at
 * [ListMaxHeight] — a short set reads as a compact sheet instead of a full-height page, and a
 * search query narrows the rows without resizing the sheet under the user's fingers.
 *
 * Departure from the design: the mockup has no search field. The supported list keeps growing,
 * so the field stays — it is the reason this sheet replaced the segmented control at all.
 */
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppTheme.materialColors.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        SurferCurrencyBottomSheetContent(
            title = title,
            searchPlaceholder = searchPlaceholder,
            currencies = currencies,
            selectedCode = selectedCode,
            onSelect = onSelect,
        )
    }
}

/** Stable selectors for [SurferCurrencyBottomSheet] — see docs/testing/testing-strategy.md. */
object SurferCurrencyBottomSheetTestTags {
    const val Search = "currencySheet:search"
    const val List = "currencySheet:list"
}

/**
 * The sheet's body, without the modal shell — public so a UI test can mount it directly, the way
 * `DashboardCardStyleSheetContent` is.
 */
@Composable
fun SurferCurrencyBottomSheetContent(
    title: String,
    searchPlaceholder: String,
    currencies: List<SurferCurrencyOption>,
    selectedCode: String?,
    onSelect: (SurferCurrencyOption) -> Unit,
    modifier: Modifier = Modifier,
) {
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

    Column(modifier = modifier.fillMaxWidth().padding(bottom = 12.dp)) {
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
            modifier = Modifier.testTag(SurferCurrencyBottomSheetTestTags.Search),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            // Sized from the *unfiltered* list, so typing a query filters the rows without the
            // sheet collapsing around the one that matched and re-growing when it is cleared.
            modifier = Modifier
                .fillMaxWidth()
                .height(listHeight(currencies.size))
                .testTag(SurferCurrencyBottomSheetTestTags.List),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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

/**
 * How tall the list is for [currencyCount] currencies: every row it can show, capped at
 * [ListMaxHeight]. An estimate per row rather than a measured one — the only thing it has to get
 * right is that the height does not depend on the *filtered* count, and a few dp of slack under a
 * list too short to fill the sheet is cheaper than a sheet that resizes on every keystroke.
 */
private fun listHeight(currencyCount: Int): Dp =
    minOf(RowHeight * currencyCount, ListMaxHeight)

private val ListMaxHeight = 420.dp

/** One [SurferCurrencyRow] plus the 10dp gap under it. */
private val RowHeight = 76.dp
