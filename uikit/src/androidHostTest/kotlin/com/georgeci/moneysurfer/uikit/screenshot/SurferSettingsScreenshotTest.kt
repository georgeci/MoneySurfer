package com.georgeci.moneysurfer.uikit.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.settings.SurferNameBlock
import com.georgeci.moneysurfer.uikit.components.settings.SurferPendingBadge
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsChevron
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsGroup
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRadio
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRow
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsSwitch
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsValuePill
import com.georgeci.moneysurfer.uikit.components.settings.SurferStatusHeroCard
import com.georgeci.moneysurfer.uikit.components.settings.SurferStatusHeroTone
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class SurferSettingsScreenshotTest {

    @Test
    fun surferSettingsRows() = captureLightAndDark("surfer_settings_rows") {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SurferNameBlock(
                name = "Alex Morgan",
                email = "alex.morgan@example.com",
                initial = "A",
                onClick = {},
            )
            SurferSettingsGroup(title = "Preferences") {
                SurferSettingsRow(
                    title = "Appearance",
                    icon = SurferIcons.Palette,
                    supportingText = "System · Dynamic colour off",
                    trailing = { SurferSettingsChevron() },
                    onClick = {},
                )
                SurferSettingsRow(
                    title = "Notifications",
                    icon = SurferIcons.Notifications,
                    trailing = { SurferSettingsSwitch(checked = true, onCheckedChange = {}) },
                )
                SurferSettingsRow(
                    title = "Biometric lock",
                    icon = SurferIcons.Fingerprint,
                    trailing = { SurferSettingsSwitch(checked = false, onCheckedChange = {}) },
                )
                SurferSettingsRow(
                    title = "Default currency",
                    icon = SurferIcons.Globe,
                    trailing = { SurferSettingsValuePill(text = "EUR") },
                    onClick = {},
                )
                SurferSettingsRow(
                    title = "Weekly digest",
                    icon = SurferIcons.Mail,
                    trailing = { SurferSettingsRadio(selected = true) },
                    onClick = {},
                )
            }
            SurferSettingsGroup(
                title = "Workspace",
                footnote = "Invites expire after 7 days.",
            ) {
                SurferSettingsRow(
                    title = "Members",
                    icon = SurferIcons.People,
                    supportingText = "3 members",
                    trailing = { SurferPendingBadge(text = "1 pending") },
                    onClick = {},
                )
                SurferSettingsRow(
                    title = "Delete account",
                    icon = SurferIcons.Delete,
                    danger = true,
                    onClick = {},
                )
            }
        }
    }

    @Test
    fun surferStatusHeroCards() = captureLightAndDark("surfer_status_hero_cards") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferStatusHeroTone.entries.forEach { tone ->
                SurferStatusHeroCard(
                    title = "Sync is up to date",
                    icon = SurferIcons.Cloud,
                    supporting = "Last synced 2 minutes ago · tone ${tone.name}",
                    tone = tone,
                )
            }
        }
    }
}
