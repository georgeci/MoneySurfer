package com.georgeci.moneysurfer.uikit.atom

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.theme.SurferContainerStyle

/**
 * Picks the right container variant based on [AppTheme.containerStyle] and [selected].
 * Optional [onClick] adds a ripple-bounded clickable area, spliced AFTER the atom's clip
 * so the shape bounds the ripple but doesn't clip the elevated variant's shadow.
 */
@Composable
fun SurferCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val interaction = rememberClickableInteraction(onClick)
    when (AppTheme.containerStyle) {
        SurferContainerStyle.Filled -> SurferFilledContainer(
            modifier = modifier,
            interactionModifier = interaction,
            tokens = if (selected) SurferFilledContainerDefaults.selected() else SurferFilledContainerDefaults.default(),
            content = content,
        )

        SurferContainerStyle.Outlined -> SurferOutlinedContainer(
            modifier = modifier,
            interactionModifier = interaction,
            tokens = if (selected) SurferOutlinedContainerDefaults.selected() else SurferOutlinedContainerDefaults.default(),
            content = content,
        )

        SurferContainerStyle.Card -> SurferContainer(
            modifier = modifier,
            interactionModifier = interaction,
            tokens = if (selected) SurferContainerDefaults.selected() else SurferContainerDefaults.default(),
            content = content,
        )
    }
}

@Composable
internal fun rememberClickableInteraction(onClick: (() -> Unit)?): Modifier =
    if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

@Preview
@Composable
private fun SurferCardFilledPreview() {
    SurferComponentPreview(containerStyle = SurferContainerStyle.Filled) {
        CardPreviewContent()
    }
}

@Preview
@Composable
private fun SurferCardOutlinedPreview() {
    SurferComponentPreview(containerStyle = SurferContainerStyle.Outlined) {
        CardPreviewContent()
    }
}

@Preview
@Composable
private fun SurferCardElevatedPreview() {
    SurferComponentPreview(containerStyle = SurferContainerStyle.Card) {
        CardPreviewContent()
    }
}

@Composable
private fun CardPreviewContent() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        SurferCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
            CardPreviewRow("Main account", "Checking · ••4218", "$4,210.55")
        }
        SurferCard(modifier = Modifier.fillMaxWidth(), selected = true, onClick = {}) {
            CardPreviewRow("Savings", "High-yield · 4.5%", "$12,840.00")
        }
        SurferCard(modifier = Modifier.fillMaxWidth()) {
            CardPreviewRow("Read-only", "No click handler", "$0.00")
        }
    }
}

@Composable
private fun CardPreviewRow(label: String, hint: String, amount: String) {
    Row {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = AppTheme.typography.titleSmall)
            Spacer(Modifier.padding(top = 2.dp))
            Text(
                text = hint,
                style = AppTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
        }
        Text(text = amount, style = AppTheme.typography.titleSmall)
    }
}
