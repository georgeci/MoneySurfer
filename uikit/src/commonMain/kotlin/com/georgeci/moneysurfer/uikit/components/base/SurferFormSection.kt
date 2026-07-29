package com.georgeci.moneysurfer.uikit.components.base

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/**
 * A [SurferSectionLabel] and the control it names, as one block.
 *
 * The gap between the two is the reason this exists. Creation screens emit the label and the
 * control as siblings of the form's own `Column`, so its `spacedBy` — 24dp on most screens — lands
 * *between the caption and its own field* on top of whatever padding the call site spelled. The
 * caption drifted that far from its control on some blocks and not others. Here the label always
 * sits [LabelGap] above its content, and the form's spacing only ever separates one block from the
 * next.
 */
@Composable
fun SurferFormSection(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LabelGap),
    ) {
        SurferSectionLabel(text = label)
        content()
    }
}

private val LabelGap = 8.dp

@Preview
@Composable
private fun SurferFormSectionPreview() {
    SurferComponentPreview {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.large),
        ) {
            SurferFormSection(label = "Type") {
                SurferSegmentedControl(
                    options = listOf("Cash", "Bank"),
                    selected = "Bank",
                    label = { it },
                    onSelect = {},
                )
            }
            SurferFormSection(label = "Currency") {
                SurferSegmentedControl(
                    options = listOf("EUR", "PLN"),
                    selected = "EUR",
                    label = { it },
                    onSelect = {},
                )
            }
        }
    }
}
