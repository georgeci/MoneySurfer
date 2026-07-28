package com.georgeci.moneysurfer.uikit.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.screenshot.ScreenshotQualifiers
import com.georgeci.moneysurfer.screenshot.ScreenshotSdk
import com.georgeci.moneysurfer.screenshot.captureLightAndDark
import com.georgeci.moneysurfer.uikit.components.SurferButton
import com.georgeci.moneysurfer.uikit.components.SurferButtonSize
import com.georgeci.moneysurfer.uikit.components.SurferButtonStyle
import com.georgeci.moneysurfer.uikit.icons.SurferIcons
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [ScreenshotSdk], qualifiers = ScreenshotQualifiers)
class SurferButtonScreenshotTest {

    @Test
    fun surferButtonStyles() = captureLightAndDark("surfer_button_styles") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SurferButtonStyle.entries.forEach { style ->
                SurferButton(
                    text = style.name,
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    style = style,
                )
            }
        }
    }

    @Test
    fun surferButtonSizesAndStates() = captureLightAndDark("surfer_button_sizes_and_states") {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SurferButtonSize.entries.forEach { size ->
                SurferButton(
                    text = size.name,
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    size = size,
                )
            }
            SurferButton(
                text = "With icons",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                startIcon = SurferIcons.Check,
                endIcon = SurferIcons.ChevronRight,
            )
            SurferButton(
                text = "Disabled",
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
            )
        }
    }
}
