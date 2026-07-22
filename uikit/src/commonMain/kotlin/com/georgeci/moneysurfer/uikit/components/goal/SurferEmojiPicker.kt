package com.georgeci.moneysurfer.uikit.components.goal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Emoji grid for the goal editor. The design system has no other emoji input — goal icons and
 * workspace icons are the only two places emoji are allowed (decision 8 in md/goals.md).
 */
@Composable
fun SurferEmojiPicker(
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    emojis: List<String> = SurferGoalEmojis.All,
    columns: Int = SurferEmojiPickerDefaults.COLUMNS,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxWidth().heightIn(max = SurferEmojiPickerDefaults.MaxHeight),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        items(emojis, key = { it }) { emoji ->
            EmojiCell(
                emoji = emoji,
                selected = emoji == selected,
                onClick = { onSelect(emoji) },
            )
        }
    }
}

@Composable
private fun EmojiCell(
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = AppTheme.materialColors
    val shape = RoundedCornerShape(SurferGoalIconDefaults.CORNER_PERCENT)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (selected) colors.secondaryContainer else colors.surfaceContainerHigh)
            .then(if (selected) Modifier.border(2.dp, colors.primary, shape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, style = TextStyle(fontSize = SurferEmojiPickerDefaults.EMOJI_SIZE_SP.sp))
    }
}

/** Hue swatches for the goal's icon tile — the second half of "what does this goal look like". */
@Composable
fun SurferGoalHueRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    hues: List<Int> = SurferGoalEmojis.Hues,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small),
    ) {
        hues.forEach { hue ->
            val isSelected = hue == selected
            Box(
                modifier = Modifier
                    .size(SurferEmojiPickerDefaults.SwatchSize)
                    .clip(CircleShape)
                    .background(goalAccentColor(hue))
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, AppTheme.materialColors.onSurface, CircleShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(hue) },
            )
        }
    }
}

object SurferEmojiPickerDefaults {
    const val COLUMNS: Int = 6
    const val EMOJI_SIZE_SP: Int = 22
    val MaxHeight = 220.dp
    val SwatchSize = 28.dp
}

/** A small curated set — enough to label a savings goal without shipping a full emoji keyboard. */
object SurferGoalEmojis {
    val All: List<String> = listOf(
        "🎯", "💰", "🏦", "☂️", "🏠", "🚗",
        "🚲", "✈️", "🏖️", "🎓", "💍", "👶",
        "🐶", "🎁", "💻", "📱", "🎸", "📷",
        "🛋️", "🔧", "🏥", "🎄", "🍾", "🌱",
    )

    /** Hues that stay distinguishable side by side in both themes. */
    val Hues: List<Int> = listOf(210, 260, 320, 0, 30, 60, 140, 175)
}

@Preview
@Composable
private fun SurferEmojiPickerPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferEmojiPicker(selected = "☂️", onSelect = {})
            SurferGoalHueRow(selected = 210, onSelect = {})
        }
    }
}
