package com.georgeci.moneysurfer.uikit.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.widgets.SurferAccountItem
import com.georgeci.moneysurfer.uikit.widgets.SurferAccountsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferAddAccountCta
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceFootnote
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceVariant
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferQuickActionsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferRecentTransactionItem
import com.georgeci.moneysurfer.uikit.widgets.SurferRecentTransactionsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferWidgetSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class SurferWidgetScreenshotTest {

    @Test
    fun surferBalanceWidget() = captureLightAndDark("surfer_balance_widget") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferWidgetSize.entries.forEach { size ->
                SurferBalanceWidget(
                    title = "Total balance",
                    balance = "€11,575.32",
                    modifier = Modifier.fillMaxWidth(),
                    size = size,
                    footnote = SurferBalanceFootnote.Trend("+€412 this month"),
                )
            }
        }
    }

    @Test
    fun surferBalanceWidgetVariants() = captureLightAndDark("surfer_balance_widget_variants") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferBalanceVariant.entries.forEach { variant ->
                SurferBalanceWidget(
                    title = "Total balance",
                    balance = "€11,575.32",
                    modifier = Modifier.fillMaxWidth(),
                    variant = variant,
                    footnote = SurferBalanceFootnote.Trend("+€412 this month"),
                )
            }
        }
    }

    @Test
    fun surferAccountsWidget() = captureLightAndDark("surfer_accounts_widget") {
        Column(modifier = Modifier.padding(16.dp)) {
            SurferAccountsWidget(
                items = listOf(
                    SurferAccountItem(
                        id = "acc-1",
                        name = "Everyday",
                        subtitle = "Checking · EUR",
                        balance = "€2,480.32",
                    ),
                    SurferAccountItem(
                        id = "acc-2",
                        name = "Emergency Fund",
                        subtitle = "Savings · EUR",
                        balance = "€8,915.00",
                        icon = SurferIcons.Savings,
                    ),
                ),
                addCta = SurferAddAccountCta(label = "Add account", onClick = {}),
                modifier = Modifier.fillMaxWidth(),
                size = SurferWidgetSize.Expanded,
            )
        }
    }

    @Test
    fun surferRecentTransactionsWidget() = captureLightAndDark("surfer_recent_transactions_widget") {
        Column(modifier = Modifier.padding(16.dp)) {
            SurferRecentTransactionsWidget(
                items = listOf(
                    SurferRecentTransactionItem(
                        id = "tx-1",
                        title = "Lidl — weekly shop",
                        subtitle = "Groceries",
                        amount = "−€48.20",
                        isExpense = true,
                        iconBgColor = Color(0xFF2F8E6E),
                        iconFgColor = Color.White,
                        iconInitial = "L",
                    ),
                    SurferRecentTransactionItem(
                        id = "tx-2",
                        title = "March payroll",
                        subtitle = "Salary",
                        amount = "+€3,200.00",
                        isExpense = false,
                        iconBgColor = Color(0xFF2E5AA8),
                        iconFgColor = Color.White,
                        iconInitial = "M",
                    ),
                ),
                title = "Recent",
                seeAllLabel = "See all",
                onSeeAllClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @Test
    fun surferQuickActionsWidget() = captureLightAndDark("surfer_quick_actions_widget") {
        Column(modifier = Modifier.padding(16.dp)) {
            SurferQuickActionsWidget(
                primaryLabel = "Add expense",
                primaryIcon = SurferIcons.Add,
                onPrimaryClick = {},
                secondaryLabel = "Transfer",
                secondaryIcon = SurferIcons.SwapHoriz,
                onSecondaryClick = {},
                modifier = Modifier.fillMaxWidth(),
                size = SurferWidgetSize.Expanded,
            )
        }
    }
}
