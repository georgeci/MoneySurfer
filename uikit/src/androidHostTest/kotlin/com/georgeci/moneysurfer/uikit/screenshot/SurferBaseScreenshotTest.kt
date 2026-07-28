package com.georgeci.moneysurfer.uikit.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.screenshot.ScreenshotQualifiers
import com.georgeci.moneysurfer.screenshot.ScreenshotSdk
import com.georgeci.moneysurfer.screenshot.captureLightAndDark
import com.georgeci.moneysurfer.uikit.atom.SurferIconBubble
import com.georgeci.moneysurfer.uikit.atom.SurferSelectionRadio
import com.georgeci.moneysurfer.uikit.components.SurferAddNewCard
import com.georgeci.moneysurfer.uikit.components.base.SurferAddFab
import com.georgeci.moneysurfer.uikit.components.base.SurferDashboardToolbar
import com.georgeci.moneysurfer.uikit.components.base.SurferFilterChipRow
import com.georgeci.moneysurfer.uikit.components.base.SurferOutlinedChip
import com.georgeci.moneysurfer.uikit.components.base.SurferPeriodSwitch
import com.georgeci.moneysurfer.uikit.components.base.SurferSectionHeader
import com.georgeci.moneysurfer.uikit.components.base.SurferSectionHeaderHint
import com.georgeci.moneysurfer.uikit.components.base.SurferSectionLabel
import com.georgeci.moneysurfer.uikit.components.base.SurferSegmentedControl
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmount
import com.georgeci.moneysurfer.uikit.components.base.SurferSplitAmountTier
import com.georgeci.moneysurfer.uikit.components.base.SurferToggleChipButton
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

    /**
     * Captured apart from [surferSelectors] rather than beside the segmented control: this one
     * sizes to its labels, so a frame it shared with two full-width rows would render it as a
     * fragment in a corner — and the point of the capture is that a track carrying one raised
     * thumb still reads as a single control at header size, in both themes.
     */
    @Test
    fun surferPeriodSwitch() = captureLightAndDark("surfer_period_switch") {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SurferPeriodSwitch(
                options = listOf("Week", "Month"),
                selected = "Month",
                label = { it },
                onSelect = {},
            )
            SurferPeriodSwitch(
                options = listOf("Week", "Month"),
                selected = "Week",
                label = { it },
                onSelect = {},
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

    /**
     * The headings the form and list screens are built from. Captured together because the whole
     * point of extracting them was that the six private copies had drifted apart — a single frame
     * is where that would show up again.
     */
    @Test
    fun surferSectionHeadings() = captureLightAndDark("surfer_section_headings") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SurferSectionLabel(text = "Account type")
            SurferSectionHeader(title = "Active")
            SurferSectionHeader(
                title = "Active",
                trailing = { SurferSectionHeaderHint(text = "Drag to reorder") },
            )
        }
    }

    @Test
    fun surferChipsAndFab() = captureLightAndDark("surfer_chips_and_fab") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SurferOutlinedChip(label = "IBAN", icon = SurferIcons.Add, onClick = {})
                SurferOutlinedChip(label = "Description", icon = SurferIcons.Add, onClick = {})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SurferToggleChipButton(
                    label = "Edit",
                    icon = SurferIcons.Edit,
                    active = false,
                    onClick = {},
                )
                SurferToggleChipButton(
                    label = "Done",
                    icon = SurferIcons.Check,
                    active = true,
                    onClick = {},
                )
            }
            SurferAddFab(label = "Add account", onClick = {})
        }
    }

    /** Leading medallions and the selection dot the picker sheets are assembled from. */
    @Test
    fun surferPickerAtoms() = captureLightAndDark("surfer_picker_atoms") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SurferIconBubble(icon = SurferIcons.Wallet)
                SurferIconBubble(icon = SurferIcons.Add, outlined = true)
                SurferIconBubble(icon = SurferIcons.Savings, size = 36.dp)
                SurferSelectionRadio(selected = true)
                SurferSelectionRadio(selected = false)
            }
            SurferAddNewCard(
                label = "Add new account",
                subtitle = "Bank, savings, cash or card",
                onClick = {},
            )
            SurferAddNewCard(label = "Create new category", onClick = {})
        }
    }
}
