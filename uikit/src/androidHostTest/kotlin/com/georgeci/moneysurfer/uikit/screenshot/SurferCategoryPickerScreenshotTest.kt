package com.georgeci.moneysurfer.uikit.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.screenshot.ScreenshotQualifiers
import com.georgeci.moneysurfer.screenshot.ScreenshotSdk
import com.georgeci.moneysurfer.screenshot.captureLightAndDark
import com.georgeci.moneysurfer.uikit.components.SurferCategoryPalette
import com.georgeci.moneysurfer.uikit.components.category.SurferCategoryManageCard
import com.georgeci.moneysurfer.uikit.components.category.SurferColorSwatchRow
import com.georgeci.moneysurfer.uikit.components.category.SurferIconPickerGrid
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The category editor's appearance pickers and the manage-list row.
 *
 * `SurferConfirmDialog` is deliberately absent: an `AlertDialog` renders into its own window,
 * which the compose capture does not include, so the reference would be an empty frame. Cover it
 * behaviourally instead.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class SurferCategoryPickerScreenshotTest {

    /**
     * Selection sits on index 1 rather than 0 so the frame shows both states of every cell — a
     * grid captured with nothing selected would pass even if the selected treatment vanished.
     */
    @Test
    fun surferCategoryAppearancePickers() = captureLightAndDark("surfer_category_appearance") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SurferIconPickerGrid(
                selectedKey = SurferCategoryPalette.iconKeys[1],
                tint = SurferCategoryPalette.tints[1],
                onSelect = {},
            )
            SurferColorSwatchRow(
                selectedHue = SurferCategoryPalette.hues[1],
                onSelect = {},
            )
        }
    }

    @Test
    fun surferCategoryManageCards() = captureLightAndDark("surfer_category_manage_cards") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SurferCategoryManageCard(
                name = "Food",
                typeLabel = "Expense",
                icon = SurferCategoryPalette.icons[0],
                tint = SurferCategoryPalette.tints[0],
                onClick = {},
            )
            // Indented the way the manage list indents a child, so the capture covers the
            // nesting the screen expresses purely through leading padding.
            SurferCategoryManageCard(
                name = "Groceries",
                typeLabel = "Expense",
                icon = SurferCategoryPalette.icons[3],
                tint = SurferCategoryPalette.tints[3],
                onClick = {},
                modifier = Modifier.padding(start = 24.dp),
            )
            SurferCategoryManageCard(
                name = "Salary",
                typeLabel = "Income",
                icon = SurferCategoryPalette.icons[5],
                tint = SurferCategoryPalette.tints[5],
                onClick = {},
            )
        }
    }
}
