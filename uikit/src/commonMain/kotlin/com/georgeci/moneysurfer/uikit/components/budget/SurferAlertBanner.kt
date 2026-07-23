package com.georgeci.moneysurfer.uikit.components.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * Inline banner for the two states worth interrupting for: nearing the alert threshold and
 * already over. Tinted with the status colour at low alpha so it reads as part of the budget
 * rather than as a system error.
 */
@Composable
fun SurferAlertBanner(
    title: String,
    status: SurferBudgetStatus,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    val tint = status.color
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.medium)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = SurferIcons.Info,
            // decorative — the banner title states the same thing in words
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = AppTheme.typography.labelLarge,
                color = tint,
            )
            if (message != null) {
                Text(
                    text = message,
                    style = AppTheme.typography.bodySmall,
                    color = AppTheme.materialColors.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview
@Composable
private fun SurferAlertBannerPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SurferAlertBanner(
                title = "86% of the limit spent",
                status = SurferBudgetStatus.Warn,
                message = "You set an alert at 80%. €54.20 left for 12 days.",
            )
            SurferAlertBanner(
                title = "Over budget by €25.10",
                status = SurferBudgetStatus.Over,
                message = "Spending stops counting down — it counts up now.",
            )
        }
    }
}
