package com.georgeci.moneysurfer.uikit.components.budget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Minus / value / plus stepper for the alert threshold, clamped to [range] in [step] increments.
 *
 * A stepper rather than a slider: the value is a coarse percentage the user picks once, and
 * discrete taps hit 80 % first time where a slider drags to 79 %.
 */
@Composable
fun SurferPercentStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = DEFAULT_RANGE,
    step: Int = DEFAULT_STEP,
    decreaseContentDescription: String? = null,
    increaseContentDescription: String? = null,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(
            onClick = { onValueChange((value - step).coerceIn(range)) },
            enabled = value > range.first,
        ) {
            Icon(
                imageVector = SurferIcons.ArrowDown,
                contentDescription = decreaseContentDescription,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = "$value%",
            style = AppTheme.typography.titleMedium,
            color = AppTheme.materialColors.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 56.dp),
        )
        IconButton(
            onClick = { onValueChange((value + step).coerceIn(range)) },
            enabled = value < range.last,
        ) {
            Icon(
                imageVector = SurferIcons.ArrowUp,
                contentDescription = increaseContentDescription,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 50..100 in steps of 5 — the mockup's range. */
private val DEFAULT_RANGE = 50..100
private const val DEFAULT_STEP = 5

@Preview
@Composable
private fun SurferPercentStepperPreview() {
    SurferComponentPreview {
        var percent by remember { mutableIntStateOf(80) }
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SurferPercentStepper(value = percent, onValueChange = { percent = it })
            SurferPercentStepper(value = 50, onValueChange = {})
            SurferPercentStepper(value = 100, onValueChange = {})
        }
    }
}
