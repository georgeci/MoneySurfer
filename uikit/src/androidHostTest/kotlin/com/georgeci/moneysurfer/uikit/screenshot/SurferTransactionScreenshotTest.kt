package com.georgeci.moneysurfer.uikit.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.screenshot.ScreenshotQualifiers
import com.georgeci.moneysurfer.screenshot.ScreenshotSdk
import com.georgeci.moneysurfer.screenshot.captureLightAndDark
import com.georgeci.moneysurfer.uikit.components.SurferCategoryBubble
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.category.SurferCategoryCell
import com.georgeci.moneysurfer.uikit.components.transaction.SurferTransactionCard
import com.georgeci.moneysurfer.uikit.components.transaction.SurferTransactionLine
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class SurferTransactionScreenshotTest {

    @Test
    fun surferTransactionRows() = captureLightAndDark("surfer_transaction_rows") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferTransactionCard(
                title = "Lidl — weekly shop",
                amount = "−€48.20",
                modifier = Modifier.fillMaxWidth(),
                amountColor = AppTheme.materialColors.error,
                onClick = {},
            )
            SurferTransactionLine(
                icon = SurferIcons.Receipt,
                title = "Lidl — weekly shop",
                formattedAmount = "−€48.20",
                modifier = Modifier.fillMaxWidth(),
                categoryHueSeed = "groceries",
                meta = "Groceries",
                dateLabel = "12 Mar",
                onClick = {},
            )
            SurferTransactionLine(
                icon = SurferIcons.Wallet,
                title = "March payroll",
                formattedAmount = "+€3,200.00",
                modifier = Modifier.fillMaxWidth(),
                categoryHueSeed = "salary",
                meta = "Salary",
                amountColor = AppTheme.materialColors.primary,
                dateLabel = "01 Mar",
                onClick = {},
            )
            SurferTransactionLine(
                icon = SurferIcons.SwapHoriz,
                title = "Everyday → Emergency Fund",
                formattedAmount = "€250.00",
                modifier = Modifier.fillMaxWidth(),
                meta = "Transfer",
                dateLabel = "28 Feb",
            )
        }
    }

    @Test
    fun surferCategoryComponents() = captureLightAndDark("surfer_category_components") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SurferCategoryPalette.tints.take(6).forEachIndexed { index, tint ->
                    SurferCategoryBubble(
                        icon = SurferCategoryPalette.icons[index],
                        tint = tint,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SurferCategoryCell(
                    name = "Groceries",
                    icon = SurferIcons.Receipt,
                    tint = SurferCategoryPalette.tints[0],
                    selected = false,
                    onClick = {},
                )
                SurferCategoryCell(
                    name = "Transport",
                    icon = SurferIcons.SwapHoriz,
                    tint = SurferCategoryPalette.tints[2],
                    selected = true,
                    onClick = {},
                )
                SurferCategoryCell(
                    name = "Bills",
                    icon = SurferIcons.Bolt,
                    tint = SurferCategoryPalette.tints[3],
                    selected = false,
                    onClick = {},
                )
            }
        }
    }
}
