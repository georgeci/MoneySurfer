package com.georgeci.moneysurfer.feature.dashboard.screenshot

import com.georgeci.moneysurfer.domain.primitives.AccountId
import com.georgeci.moneysurfer.domain.primitives.TransactionId
import com.georgeci.moneysurfer.feature.dashboard.AccountUi
import com.georgeci.moneysurfer.feature.dashboard.DashboardContent
import com.georgeci.moneysurfer.feature.dashboard.DashboardState
import com.georgeci.moneysurfer.feature.dashboard.TransactionUi
import com.georgeci.moneysurfer.screenshot.ScreenshotQualifiers
import com.georgeci.moneysurfer.screenshot.ScreenshotSdk
import com.georgeci.moneysurfer.screenshot.captureFullScreenAtWidths
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The dashboard at three window widths (issue #392, gap G9).
 *
 * `DashboardWidgets` swaps its single column for a spanned grid at Expanded and above, and the
 * widgets inside it drop to a denser rendering once a cell is wide enough — two decisions taken
 * from the window, which is exactly what no test could see before these frames existed.
 *
 * Captured through the stateless [DashboardContent] rather than `DashboardScreen`, so the frames
 * carry a fixed state instead of whatever a Koin graph would resolve.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class DashboardScreenshotTest {

    @Test
    fun dashboard() = captureFullScreenAtWidths("dashboard") {
        DashboardContent(state = dashboardState(), onEvent = {})
    }

    /**
     * The state a fresh install renders: no accounts, so no FAB and every widget in its own empty
     * treatment. Worth a wide capture of its own — a grid of empty widgets is where a cell that
     * collapses to nothing leaves a hole the single column would never show.
     */
    @Test
    fun dashboardEmpty() = captureFullScreenAtWidths("dashboard_empty") {
        DashboardContent(
            state = DashboardState.Content(
                accounts = emptyList(),
                transactions = emptyList(),
                formattedTotalBalance = null,
                workspaceName = null,
                workspaceInitial = null,
                greeting = null,
                formattedTrendDelta = null,
            ),
            onEvent = {},
        )
    }

    private fun dashboardState() = DashboardState.Content(
        accounts = listOf(
            AccountUi(AccountId("screenshot-acc-1"), "Everyday", "€2,480.32", "EUR"),
            AccountUi(AccountId("screenshot-acc-2"), "Emergency Fund", "€8,915.00", "EUR"),
        ),
        transactions = listOf(
            TransactionUi(
                id = TransactionId("screenshot-tx-1"),
                title = "Lidl — weekly shop",
                formattedAmount = "−€48.20",
                isExpense = true,
                categoryHueSeed = "screenshot-cat-1",
            ),
            TransactionUi(
                id = TransactionId("screenshot-tx-2"),
                title = "March payroll",
                formattedAmount = "+€3,200.00",
                isExpense = false,
                categoryHueSeed = "screenshot-cat-2",
            ),
            TransactionUi(
                id = TransactionId("screenshot-tx-3"),
                title = "Coffee",
                formattedAmount = "−€9.99",
                isExpense = true,
                categoryHueSeed = "screenshot-cat-3",
            ),
        ),
        formattedTotalBalance = "€11,575.32",
        workspaceName = "Household budget",
        workspaceInitial = "H",
        greeting = "Good evening",
        formattedTrendDelta = "+€412",
        balanceSeries = listOf(9_800f, 10_240f, 9_950f, 10_610f, 11_160f, 11_575f),
        transferEnabled = true,
    )
}
