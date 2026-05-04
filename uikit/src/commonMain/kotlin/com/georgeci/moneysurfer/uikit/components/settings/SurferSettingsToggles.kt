package com.georgeci.moneysurfer.uikit.components.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * iOS-style pill toggle: 51x31 track, 27dp white knob with subtle shadow. Used as the trailing
 * slot on [SurferSettingsRow]. Matches the design's preference for a clean toggle without M3's
 * inline icon clutter — fill + position carries the state.
 */
@Composable
fun SurferSettingsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.materialColors
    val trackColor = if (checked) colors.primary else colors.surfaceContainerHighest
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    )
    Box(
        modifier = modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset)
                .size(27.dp)
                .shadow(elevation = 2.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** Round radio dot — used in radio-list rows (e.g. theme picker). */
@Composable
fun SurferSettingsRadio(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = AppTheme.materialColors
    val ring = if (selected) colors.primary else colors.outline
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(CircleShape)
            .border(2.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
            )
        }
    }
}

@Preview
@Composable
private fun SurferSettingsSwitchPreview() {
    SurferComponentPreview {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SurferSettingsSwitch(checked = true, onCheckedChange = {})
            SurferSettingsSwitch(checked = false, onCheckedChange = {})
            SurferSettingsRadio(selected = true)
            SurferSettingsRadio(selected = false)
        }
    }
}
