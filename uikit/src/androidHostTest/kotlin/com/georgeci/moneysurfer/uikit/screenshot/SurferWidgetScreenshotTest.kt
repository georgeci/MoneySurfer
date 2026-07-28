package com.georgeci.moneysurfer.uikit.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.screenshot.ScreenshotQualifiers
import com.georgeci.moneysurfer.screenshot.ScreenshotSdk
import com.georgeci.moneysurfer.screenshot.captureLightAndDark
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.budget.SurferBudgetStatus
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.widgets.SurferAccountItem
import com.georgeci.moneysurfer.uikit.widgets.SurferAccountsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferAddAccountCta
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceFootnote
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceVariant
import com.georgeci.moneysurfer.uikit.widgets.SurferBalanceWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferCategorySpendCap
import com.georgeci.moneysurfer.uikit.widgets.SurferCategorySpendItem
import com.georgeci.moneysurfer.uikit.widgets.SurferQuickActionsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferRecentTransactionItem
import com.georgeci.moneysurfer.uikit.widgets.SurferRecentTransactionsWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferSafeToSpendData
import com.georgeci.moneysurfer.uikit.widgets.SurferSafeToSpendEmpty
import com.georgeci.moneysurfer.uikit.widgets.SurferSafeToSpendWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferSpentByCategoryEmpty
import com.georgeci.moneysurfer.uikit.widgets.SurferSpentByCategoryVariant
import com.georgeci.moneysurfer.uikit.widgets.SurferSpentByCategoryWidget
import com.georgeci.moneysurfer.uikit.widgets.SurferWidgetSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The dashboard's minimum widget height — see `DASHBOARD_WIDGET_MIN_HEIGHT` in `:feature:dashboard`. */
private val SafeToSpendEmptyHeight = 180.dp

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

    /**
     * Both sizes plus the empty state in one gallery: the compact card is the one that drops the
     * caption line, and the empty card is a different layout altogether rather than a blank body.
     */
    @Test
    fun surferSafeToSpendWidget() = captureLightAndDark("surfer_safe_to_spend_widget") {
        val data = SurferSafeToSpendData(
            amount = "€642.30",
            caption = "of €1,800 · Everyday",
            perDay = "€53.52 a day",
            daysLeft = "12 days left",
            progress = 0.64f,
            paceFraction = 0.6f,
            status = SurferBudgetStatus.Ok,
        )
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferWidgetSize.entries.forEach { size ->
                SurferSafeToSpendWidget(
                    title = "Safe to spend",
                    data = data,
                    modifier = Modifier.fillMaxWidth(),
                    size = size,
                )
            }
            SurferSafeToSpendWidget(
                title = "Safe to spend",
                data = null,
                // The placeholder centres itself in whatever height it is given; the gallery has
                // the whole page, so it is pinned to the dashboard's own minimum widget height.
                modifier = Modifier.fillMaxWidth().height(SafeToSpendEmptyHeight),
                empty = SurferSafeToSpendEmpty(
                    title = "No budget yet",
                    subtitle = "Set a cap to see what is safe to spend.",
                    actionLabel = "Set a budget",
                    onActionClick = {},
                ),
            )
        }
    }

    /** Overspent: the headline turns to the error colour and the bar fills past the pace tick. */
    @Test
    fun surferSafeToSpendWidgetOver() = captureLightAndDark("surfer_safe_to_spend_widget_over") {
        Column(modifier = Modifier.padding(16.dp)) {
            SurferSafeToSpendWidget(
                title = "Safe to spend",
                data = SurferSafeToSpendData(
                    amount = "−€120.00",
                    caption = "over €1,800 · Everyday",
                    perDay = "€0.00 a day",
                    daysLeft = "4 days left",
                    progress = 1.07f,
                    paceFraction = 0.87f,
                    status = SurferBudgetStatus.Over,
                ),
                modifier = Modifier.fillMaxWidth(),
                size = SurferWidgetSize.Expanded,
            )
        }
    }

    /**
     * All five treatments at Hero. The sample deliberately mixes a capped-and-warned category, an
     * overspent one and three uncapped ones: what separates the variants is not only their geometry
     * but where each puts the status colour and the status word, and a uniform sample would hide
     * both.
     */
    @Test
    fun surferSpentByCategoryWidget() = captureLightAndDark("surfer_spent_by_category_widget") {
        SpentByCategoryGallery(size = SurferWidgetSize.Expanded)
    }

    /** The same five at Compact — fewer rows, a tighter type scale, the same captions. */
    @Test
    fun surferSpentByCategoryWidgetCompact() =
        captureLightAndDark("surfer_spent_by_category_widget_compact") {
            SpentByCategoryGallery(size = SurferWidgetSize.Compact)
        }

    /** No spend this period: the card keeps its heading and says so rather than disappearing. */
    @Test
    fun surferSpentByCategoryWidgetEmpty() =
        captureLightAndDark("surfer_spent_by_category_widget_empty") {
            Column(modifier = Modifier.padding(16.dp)) {
                SurferSpentByCategoryWidget(
                    title = SpentByCategoryTitle,
                    items = emptyList(),
                    modifier = Modifier.fillMaxWidth().height(SafeToSpendEmptyHeight),
                    empty = SurferSpentByCategoryEmpty(
                        title = "Nothing spent yet",
                        subtitle = "Expenses you log this month break down here.",
                    ),
                )
            }
        }
}

private const val SpentByCategoryTitle = "Spent by category"

@Composable
private fun SpentByCategoryGallery(size: SurferWidgetSize) {
    val tints = SurferCategoryPalette.tints
    val items = listOf(
        SurferCategorySpendItem(
            id = "rent",
            name = "Rent",
            amount = "€760.00",
            share = 0.45f,
            caption = "45% of spending",
            tint = tints[0],
        ),
        SurferCategorySpendItem(
            id = "groceries",
            name = "Groceries",
            amount = "€142.10",
            share = 0.22f,
            caption = "€142.10 of €150",
            tint = tints[1],
            cap = SurferCategorySpendCap(
                progress = 0.95f,
                status = SurferBudgetStatus.Warn,
                statusLabel = "Near limit",
            ),
        ),
        SurferCategorySpendItem(
            id = "dining",
            name = "Eating out",
            amount = "€96.40",
            share = 0.12f,
            caption = "over €80",
            tint = tints[2],
            cap = SurferCategorySpendCap(
                progress = 1.2f,
                status = SurferBudgetStatus.Over,
                statusLabel = "Over",
            ),
        ),
        SurferCategorySpendItem(
            id = "transport",
            name = "Transport",
            amount = "€54.00",
            share = 0.08f,
            caption = "8% of spending",
            tint = tints[3],
        ),
        SurferCategorySpendItem(
            id = "leisure",
            name = "Leisure",
            amount = "€38.20",
            share = 0.06f,
            caption = "6% of spending",
            tint = tints[4],
        ),
    )
    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SurferSpentByCategoryVariant.entries.forEach { variant ->
            SurferSpentByCategoryWidget(
                title = SpentByCategoryTitle,
                items = items,
                modifier = Modifier.fillMaxWidth(),
                size = size,
                variant = variant,
            )
        }
    }
}
