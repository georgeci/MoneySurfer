package com.georgeci.moneysurfer.uikit.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.screenshot.ScreenshotQualifiers
import com.georgeci.moneysurfer.screenshot.ScreenshotSdk
import com.georgeci.moneysurfer.screenshot.captureLightAndDark
import com.georgeci.moneysurfer.uikit.components.base.SurferDrawerItem
import com.georgeci.moneysurfer.uikit.components.base.SurferDrawerSection
import com.georgeci.moneysurfer.uikit.components.base.SurferNavigationDrawer
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The wide-window navigation drawer.
 *
 * Captured inside a fixed-height [Box] because the drawer fills its host's height, and the
 * component gallery wraps content height — without one the footer would land directly under the
 * brand header and the "pinned to the bottom" part of the layout would go unverified.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class SurferNavigationScreenshotTest {

    @Test
    fun surferNavigationDrawer() = captureLightAndDark("surfer_navigation_drawer") {
        Box(modifier = Modifier.height(DrawerHeight)) {
            SurferNavigationDrawer(
                sections = listOf(
                    SurferDrawerSection(
                        items = listOf(
                            item("Dashboard", SurferIcons.Dashboard, selected = true),
                            item("Accounts", SurferIcons.Wallet),
                            item("Transactions", SurferIcons.SwapHoriz),
                            item("Budgets", SurferIcons.Savings),
                            item("Goals", SurferIcons.Flag),
                        ),
                    ),
                    SurferDrawerSection(
                        label = "Manage",
                        items = listOf(
                            item("Categories", SurferIcons.Category),
                            item("Settings", SurferIcons.Settings),
                        ),
                    ),
                ),
                userName = "Georgy",
                workspaceName = "Household budget",
                onUserClick = {},
            )
        }
    }

    /** No workspace selected yet, and a session with nothing to name the user by. */
    @Test
    fun surferNavigationDrawerGuest() = captureLightAndDark("surfer_navigation_drawer_guest") {
        Box(modifier = Modifier.height(DrawerHeight)) {
            SurferNavigationDrawer(
                sections = listOf(
                    SurferDrawerSection(
                        items = listOf(
                            item("Dashboard", SurferIcons.Dashboard),
                            item("Accounts", SurferIcons.Wallet, selected = true),
                        ),
                    ),
                ),
                userName = "Guest",
            )
        }
    }

    private fun item(
        label: String,
        icon: ImageVector,
        selected: Boolean = false,
    ) = SurferDrawerItem(label = label, icon = icon, selected = selected, onClick = {})

    private companion object {
        val DrawerHeight = 520.dp
    }
}
