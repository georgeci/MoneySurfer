package com.georgeci.moneysurfer.uikit.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.screenshot.ScreenshotQualifiers
import com.georgeci.moneysurfer.screenshot.ScreenshotSdk
import com.georgeci.moneysurfer.screenshot.captureFullScreenAtWidths
import com.georgeci.moneysurfer.uikit.components.SurferDetailPlaceholder
import com.georgeci.moneysurfer.uikit.components.SurferPickerRow
import com.georgeci.moneysurfer.uikit.components.base.SurferDrawerItem
import com.georgeci.moneysurfer.uikit.components.base.SurferDrawerSection
import com.georgeci.moneysurfer.uikit.components.base.SurferNavigationDrawer
import com.georgeci.moneysurfer.uikit.components.base.SurferPaneAction
import com.georgeci.moneysurfer.uikit.components.base.SurferPaneScaffold
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.window.LocalSurferPane
import com.georgeci.moneysurfer.uikit.window.SurferPane
import com.georgeci.moneysurfer.uikit.window.SurferPaneRole
import com.georgeci.moneysurfer.uikit.window.SurferWindowSize
import com.georgeci.moneysurfer.uikit.window.currentSurferWindowSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The app shell — permanent navigation plus the section it hosts — captured at three window widths
 * (issue #392, gap G9).
 *
 * The shell composable itself lives in `:navigation`, where it is `internal` and pulls a Koin
 * `ViewModel` for the drawer footer; what it is *made of* is all here, so the widths are exercised
 * against the design-system pieces instead. [Shell] therefore reproduces the two decisions the host
 * makes — which navigation presentation a width gets, and which pane role each screen composes in —
 * from the same [currentSurferWindowSize] input. `NavigationShellTest` and
 * `SurferPaneSceneStrategyTest` in `:navigation` pin that those two mappings are what the real host
 * applies; these frames are what the mapping *looks like*, which is the part a unit test cannot see.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class SurferShellScreenshotTest {

    /**
     * A section with nothing selected: one pane on a phone, list plus placeholder once the window
     * is wide enough for two.
     */
    @Test
    fun surferShell() = captureFullScreenAtWidths("surfer_shell") {
        Shell(detail = null)
    }

    /**
     * The same section with a detail open. The frame a reviewer checks for the regression issue
     * #388 named: at the two wide widths exactly one top app bar and one FAB may appear, both in
     * the list pane, while the detail pane draws its contextual header instead.
     */
    @Test
    fun surferShellDetail() = captureFullScreenAtWidths("surfer_shell_detail") {
        Shell(detail = "Everyday")
    }

    /**
     * @param detail the selected item's name, or `null` for the placeholder. On a compact window it
     *   only decides which of the two screens is on screen — there is no second pane to put it in.
     */
    @Composable
    private fun Shell(detail: String?) {
        val windowSize = currentSurferWindowSize()
        if (windowSize < SurferWindowSize.Expanded) {
            // One screen at a time, and whichever one it is owns the whole section's chrome.
            if (detail == null) {
                ListPane(role = SurferPaneRole.Single)
            } else {
                DetailPane(name = detail, role = SurferPaneRole.Single)
            }
            return
        }
        Row(modifier = Modifier.fillMaxSize()) {
            SurferNavigationDrawer(
                sections = listOf(
                    SurferDrawerSection(
                        items = listOf(
                            item("Dashboard", SurferIcons.Dashboard),
                            item("Accounts", SurferIcons.Wallet, selected = true),
                            item("Transactions", SurferIcons.SwapHoriz),
                        ),
                    ),
                    SurferDrawerSection(
                        label = "Manage",
                        items = listOf(item("Settings", SurferIcons.Settings)),
                    ),
                ),
                userName = "Georgy",
                workspaceName = "Household budget",
                onUserClick = {},
            )
            Column(modifier = Modifier.width(ListPaneWidth).fillMaxHeight()) {
                ListPane(role = SurferPaneRole.List)
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                if (detail == null) {
                    SurferDetailPlaceholder(text = "Select an account to see its details")
                } else {
                    DetailPane(name = detail, role = SurferPaneRole.Detail)
                }
            }
        }
    }

    /**
     * @param role [SurferPaneRole.Single] on a phone, [SurferPaneRole.List] beside a detail pane —
     *   both own the section chrome, so this pane draws the top app bar and the FAB either way.
     */
    @Composable
    private fun ListPane(role: SurferPaneRole) {
        CompositionLocalProvider(LocalSurferPane provides SurferPane(role = role)) {
            SurferPaneScaffold(
                title = "Accounts",
                primaryAction = SurferPaneAction(label = "Add account", onClick = {}),
            ) { padding ->
                Column(
                    modifier = Modifier.padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SurferPickerRow(
                        label = "Everyday",
                        value = "€2,480.32",
                        icon = SurferIcons.CreditCard,
                        selected = true,
                        onClick = {},
                    )
                    SurferPickerRow(
                        label = "Emergency Fund",
                        value = "€8,915.00",
                        icon = SurferIcons.Savings,
                        onClick = {},
                    )
                }
            }
        }
    }

    /**
     * @param role [SurferPaneRole.Single] on a phone, where the detail is a route of its own and
     *   draws the section chrome; [SurferPaneRole.Detail] beside a list pane, where it gives both
     *   up for a contextual header. The two frames next to each other are the whole point.
     */
    @Composable
    private fun DetailPane(name: String, role: SurferPaneRole) {
        CompositionLocalProvider(LocalSurferPane provides SurferPane(role = role)) {
            SurferPaneScaffold(
                title = name,
                onBack = {},
                primaryAction = SurferPaneAction(label = "Add transaction", onClick = {}),
            ) { padding ->
                Column(
                    modifier = Modifier.padding(padding).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SurferPickerRow(
                        label = "Lidl — weekly shop",
                        value = "−€48.20",
                        icon = SurferIcons.Category,
                        onClick = {},
                    )
                    SurferPickerRow(
                        label = "March payroll",
                        value = "+€3,200.00",
                        icon = SurferIcons.Wallet,
                        onClick = {},
                    )
                }
            }
        }
    }

    private fun item(
        label: String,
        icon: ImageVector,
        selected: Boolean = false,
    ) = SurferDrawerItem(label = label, icon = icon, selected = selected, onClick = {})

    private companion object {
        /** `SurferPaneSceneStrategy.ListPanePreferredWidth`, which `:uikit` cannot see. */
        val ListPaneWidth = 300.dp
    }
}
