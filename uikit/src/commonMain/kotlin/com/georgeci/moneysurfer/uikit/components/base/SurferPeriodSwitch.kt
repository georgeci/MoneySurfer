package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/** Track height; the thumb fills it minus [TrackInset] on every side. */
private val SwitchHeight = 32.dp

/** Breathing room between the track edge and the thumb, and between two thumbs. */
private val TrackInset = 3.dp

private val OptionPadding = 14.dp

/**
 * Segmented chip for the span a whole screen is read at — Week vs Month over the dashboard's
 * spend widgets. A pill track carrying one raised thumb, so the unselected options still read as
 * part of one control rather than as separate buttons.
 *
 * Distinct from [SurferSegmentedControl] by role, not just by paint: that one is a full-width
 * form field for picking a value the user is about to save, and states the selection twice (fill
 * plus a check) because getting it wrong costs a round trip. This is inline screen chrome, sized
 * to its labels, sitting beside content it re-reads immediately — so it drops the checks and the
 * dividers, which at header size are noise around two words.
 *
 * [optionTestTag] tags each segment individually. The row-level [modifier] cannot do that, and
 * without a per-segment tag an E2E driver has nothing to aim at but the segment's localized
 * label — which is exactly the coupling issue #352 removes.
 */
@Composable
fun <T> SurferPeriodSwitch(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionTestTag: ((T) -> String)? = null,
) {
    val pill = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .height(SwitchHeight)
            .clip(pill)
            .background(AppTheme.materialColors.surfaceContainerHighest)
            .padding(TrackInset)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(TrackInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(pill)
                    .background(if (isSelected) AppTheme.materialColors.surface else Color.Transparent)
                    // `selectable` rather than `clickable`: it publishes the selected state and the
                    // radio-button role, which is what a screen reader needs to say "1 of 2" here.
                    .selectable(selected = isSelected, onClick = { onSelect(option) })
                    .then(optionTestTag?.let { Modifier.testTag(it(option)) } ?: Modifier)
                    .padding(horizontal = OptionPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = AppTheme.typography.labelLarge,
                    color = if (isSelected) {
                        AppTheme.materialColors.onSurface
                    } else {
                        AppTheme.materialColors.onSurfaceVariant
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SurferPeriodSwitchPreview() {
    SurferComponentPreview {
        SurferPeriodSwitch(
            modifier = Modifier.padding(all = 16.dp),
            options = listOf("Week", "Month"),
            selected = "Month",
            label = { it },
            onSelect = {},
        )
    }
}
