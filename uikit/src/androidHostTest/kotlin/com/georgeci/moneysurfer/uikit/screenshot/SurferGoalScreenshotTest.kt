package com.georgeci.moneysurfer.uikit.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.goal.SurferEmojiPicker
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalCard
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalContributionRow
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalHueRow
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalIcon
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalProgressBar
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalProgressRing
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalStatus
import com.georgeci.moneysurfer.uikit.components.goal.SurferGoalStatusPill
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class SurferGoalScreenshotTest {

    @Test
    fun surferGoalCards() = captureLightAndDark("surfer_goal_cards") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferGoalCard(
                title = "Emergency fund",
                emoji = "🚲",
                hue = 210,
                status = SurferGoalStatus.Active,
                statusLabel = "Active",
                progress = 0.74f,
                savedLabel = "€8,915",
                targetLabel = "€12,000",
                percentLabel = "74%",
                caption = "by Aug 2026",
            )
            SurferGoalCard(
                title = "Lisbon trip",
                emoji = "🏖️",
                hue = 30,
                status = SurferGoalStatus.Paused,
                statusLabel = "Paused",
                progress = 0.62f,
                savedLabel = "€1,480",
                targetLabel = "€2,400",
                percentLabel = "62%",
            )
        }
    }

    @Test
    fun surferGoalParts() = captureLightAndDark("surfer_goal_parts") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SurferGoalIcon(emoji = "🚲", hue = 210)
                SurferGoalStatusPill(SurferGoalStatus.Active, "Active")
                SurferGoalStatusPill(SurferGoalStatus.Completed, "Reached")
                SurferGoalStatusPill(SurferGoalStatus.Archived, "Archived")
            }
            SurferGoalProgressBar(progress = 0.62f)
            SurferGoalProgressRing(
                progress = 0.74f,
                percentLabel = "74%",
                savedLabel = "€8,915",
                targetLabel = "of €12,000",
            )
            SurferGoalContributionRow(
                amountLabel = "+€250",
                dateLabel = "12 Jul 2026",
                isWithdrawal = false,
                note = "Salary top-up",
            )
            SurferGoalContributionRow(
                amountLabel = "−€80",
                dateLabel = "03 Jul 2026",
                isWithdrawal = true,
            )
        }
    }

    @Test
    fun surferEmojiPicker() = captureLightAndDark("surfer_goal_emoji_picker") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferEmojiPicker(selected = "🚲", onSelect = {})
            SurferGoalHueRow(selected = 210, onSelect = {})
        }
    }
}
