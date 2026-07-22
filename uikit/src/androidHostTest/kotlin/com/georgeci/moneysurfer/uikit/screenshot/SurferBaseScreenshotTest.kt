package com.georgeci.moneysurfer.uikit.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.base.SurferDashboardToolbar
import com.georgeci.moneysurfer.uikit.components.base.SurferFilterChipRow
import com.georgeci.moneysurfer.uikit.components.base.SurferSegmentedControl
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmount
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmountTier
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbar
import com.georgeci.moneysurfer.uikit.components.base.SurferToolbarAction
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class SurferBaseScreenshotTest {

    @Test
    fun surferToolbars() = captureLightAndDark("surfer_toolbars") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SurferToolbar(title = "Accounts", onBack = {})
            SurferToolbar(
                title = "Transactions",
                onBack = {},
                actions = {
                    SurferToolbarAction(
                        icon = SurferIcons.Search,
                        contentDescription = "Search",
                        onClick = {},
                    )
                    SurferToolbarAction(
                        icon = SurferIcons.MoreVert,
                        contentDescription = "More",
                        onClick = {},
                    )
                },
            )
            SurferDashboardToolbar(
                letter = "G",
                primaryText = "Good evening",
                secondaryText = "Household budget",
                actions = {
                    SurferToolbarAction(
                        icon = SurferIcons.Settings,
                        contentDescription = "Settings",
                        onClick = {},
                    )
                },
            )
        }
    }

    @Test
    fun surferSelectors() = captureLightAndDark("surfer_selectors") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SurferSegmentedControl(
                options = listOf("Week", "Month", "Year"),
                selected = "Month",
                label = { it },
                onSelect = {},
                modifier = Modifier.fillMaxWidth(),
            )
            SurferFilterChipRow(
                options = listOf("All", "Income", "Expense", "Transfer"),
                selected = "Expense",
                label = { it },
                onSelect = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @Test
    fun surferSplitAmounts() = captureLightAndDark("surfer_split_amounts") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferSplitAmountTier.entries.forEach { tier ->
                SurferSplitAmount(formattedAmount = "−€1,284.50", tier = tier)
            }
        }
    }
}
