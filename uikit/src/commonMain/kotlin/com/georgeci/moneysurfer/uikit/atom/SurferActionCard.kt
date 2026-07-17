package com.georgeci.moneysurfer.uikit.atom

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Dashed-outline "+ Add …" CTA. Optional [onClick] adds a ripple-bounded clickable area.
 * Distinct chrome from [SurferCard] on purpose — never confused with a selectable row.
 */
@Composable
fun SurferActionCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    SurferAddActionContainer(
        modifier = modifier,
        interactionModifier = rememberClickableInteraction(onClick),
        content = content,
    )
}

@Preview
@Composable
private fun SurferActionCardPreview() {
    SurferComponentPreview {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            SurferActionCard(modifier = Modifier.fillMaxWidth(), onClick = {}) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // decorative — preview only; label text is adjacent
                    Icon(imageVector = SurferIcons.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Add account", style = AppTheme.typography.labelLarge)
                }
            }
            SurferActionCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // decorative — preview only; label text is adjacent
                    Icon(imageVector = SurferIcons.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Read-only (no click)", style = AppTheme.typography.labelLarge)
                }
            }
        }
    }
}
