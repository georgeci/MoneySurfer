package com.georgeci.moneysurfer.uikit.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.SurferCurrencyRow
import com.georgeci.moneysurfer.uikit.components.SurferDropdown
import com.georgeci.moneysurfer.uikit.components.SurferPickerRow
import com.georgeci.moneysurfer.uikit.components.SurferSearchField
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class SurferInputScreenshotTest {

    @Test
    fun surferInputs() = captureLightAndDark("surfer_inputs") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferSearchField(
                value = "",
                onValueChange = {},
                placeholder = "Search transactions",
                modifier = Modifier.fillMaxWidth(),
            )
            SurferSearchField(
                value = "Lidl",
                onValueChange = {},
                placeholder = "Search transactions",
                modifier = Modifier.fillMaxWidth(),
            )
            SurferDropdown(
                items = listOf("EUR", "USD", "GBP"),
                selected = "EUR",
                label = "Currency",
                itemName = { it },
                onItemSelected = {},
                modifier = Modifier.fillMaxWidth(),
            )
            SurferPickerRow(
                label = "Account",
                value = "Everyday",
                icon = SurferIcons.Wallet,
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                supporting = "Checking · EUR",
            )
            SurferPickerRow(
                label = "Category",
                value = "Groceries",
                icon = SurferIcons.Category,
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                selected = true,
            )
        }
    }

    @Test
    fun surferCurrencyRows() = captureLightAndDark("surfer_currency_rows") {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            SurferCurrencyRow(
                symbol = "€",
                code = "EUR",
                name = "Euro",
                selected = true,
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
            SurferCurrencyRow(
                symbol = "$",
                code = "USD",
                name = "US Dollar",
                selected = false,
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
            SurferCurrencyRow(
                symbol = "£",
                code = "GBP",
                name = "British Pound",
                selected = false,
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
