package com.georgeci.moneysurfer.feature.settings.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.components.SurferBottomSheetContent
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRadio
import com.georgeci.moneysurfer.uikit.components.settings.SurferSettingsRow
import com.georgeci.moneysurfer.uikit.theme.AppTheme

/** One choice in a [PreferenceOptionsSheet]: the value to store and how to write it out. */
internal data class PreferenceOption<T>(
    val value: T,
    val label: String,
    /** Stable selector for the row, independent of the label — see [PreferencesTestTags]. */
    val tag: String,
)

private val SHEET_PADDING = 20.dp

/** Caps the list so a nine-region sheet scrolls instead of pushing the title off the screen. */
private val LIST_MAX_HEIGHT = 420.dp

/**
 * Single-choice chooser for one preference row, in the same modal shape the dashboard card-style
 * sheet uses. Radio rows rather than a dialog: the option sets are short but the sheet reaches the
 * thumb, and it matches the appearance screen's radio idiom.
 *
 * The currency row does not use this — it has its own searchable sheet in `uikit`, because that
 * list is data and keeps growing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> PreferenceOptionsSheet(
    title: String,
    options: List<PreferenceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = AppTheme.materialColors.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        SurferBottomSheetContent(title = title) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = LIST_MAX_HEIGHT)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SHEET_PADDING),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    SurferSettingsRow(
                        title = option.label,
                        onClick = { onSelect(option.value) },
                        trailing = { SurferSettingsRadio(selected = option.value == selected) },
                        modifier = Modifier.testTag(option.tag),
                    )
                }
            }
        }
    }
}
