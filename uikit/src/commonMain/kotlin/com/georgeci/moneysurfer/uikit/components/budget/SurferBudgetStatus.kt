package com.georgeci.moneysurfer.uikit.components.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * How a budget is doing, as far as the design system is concerned. Mirrors the domain's
 * `BudgetStatus`; uikit does not depend on domain, so the mapping happens in the feature layer.
 */
enum class SurferBudgetStatus {
    Ok,
    Warn,
    Over,
}

/** The one place a budget status turns into a colour. */
val SurferBudgetStatus.color: Color
    @Composable
    @ReadOnlyComposable
    get() = when (this) {
        SurferBudgetStatus.Ok -> AppTheme.materialColors.primary
        SurferBudgetStatus.Warn -> AppTheme.semanticColors.warning
        SurferBudgetStatus.Over -> AppTheme.materialColors.error
    }

/** Small filled pill carrying a status word — "On track", "Near limit", "Over". */
@Composable
fun SurferBudgetStatusPill(
    label: String,
    status: SurferBudgetStatus,
    modifier: Modifier = Modifier,
) {
    val tint = status.color
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = AppTheme.typography.labelSmall,
            color = tint,
        )
    }
}

@Preview
@Composable
private fun SurferBudgetStatusPillPreview() {
    SurferComponentPreview {
        Box(modifier = Modifier.padding(16.dp)) {
            SurferBudgetStatusPill(label = "Near limit", status = SurferBudgetStatus.Warn)
        }
    }
}
