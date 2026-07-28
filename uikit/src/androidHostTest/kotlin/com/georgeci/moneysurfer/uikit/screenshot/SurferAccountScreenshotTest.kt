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
import com.georgeci.moneysurfer.uikit.components.account.SurferAccountCard
import com.georgeci.moneysurfer.uikit.components.account.SurferAccountManageCard
import com.georgeci.moneysurfer.uikit.components.account.SurferAccountStatCard
import com.georgeci.moneysurfer.uikit.components.account.SurferArchivedAccountCard
import com.georgeci.moneysurfer.uikit.components.account.SurferBalanceChartCard
import com.georgeci.moneysurfer.uikit.components.account.SurferTotalBalanceCard
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * `SurferAccountDetailsHeroCard` is intentionally not captured — its "synced" dot runs an
 * infinite pulse, which makes the rendered frame time-dependent.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class SurferAccountScreenshotTest {

    @Test
    fun surferAccountCards() = captureLightAndDark("surfer_account_cards") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferTotalBalanceCard(
                label = "Total balance",
                formattedTotal = "€11,575.32",
                activeChipText = "2 active",
                modifier = Modifier.fillMaxWidth(),
                archivedChipText = "1 archived",
                periodLabel = "March",
            )
            SurferAccountCard(
                name = "Everyday",
                balance = "€2,480.32",
                currency = "EUR",
                modifier = Modifier.fillMaxWidth(),
                onClick = {},
                onAddClick = {},
                addContentDescription = "Add transaction",
            )
            SurferAccountManageCard(
                name = "Emergency Fund",
                subtitle = "Savings · EUR",
                icon = SurferIcons.Savings,
                formattedBalance = "€8,915.00",
                modifier = Modifier.fillMaxWidth(),
                onClick = {},
            )
            SurferArchivedAccountCard(
                name = "Old Card",
                subtitle = "Archived · EUR",
                icon = SurferIcons.CreditCard,
                restoreLabel = "Restore",
                onRestoreClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @Test
    fun surferAccountStatsAndChart() = captureLightAndDark("surfer_account_stats") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SurferAccountStatCard(
                    label = "Income",
                    value = "+€3,200.00",
                    icon = SurferIcons.ArrowDown,
                    iconTint = AppTheme.materialColors.primary,
                    modifier = Modifier.weight(1f),
                )
                SurferAccountStatCard(
                    label = "Spent",
                    value = "−€1,284.50",
                    icon = SurferIcons.ArrowUp,
                    iconTint = AppTheme.materialColors.error,
                    modifier = Modifier.weight(1f),
                )
            }
            SurferBalanceChartCard(
                title = "Balance trend",
                delta = "+€412 this month",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
