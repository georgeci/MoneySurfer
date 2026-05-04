package com.georgeci.moneysurfer.uikit.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.theme.SurferContainerStyle

/**
 * Harness used ONLY by uikit @Preview composables. Wraps [content] in [AppTheme] and paints the
 * themed surface background so components preview on the same canvas the app uses at runtime.
 * Keep app-level screens out of this helper — those should preview inside their own Scaffold.
 */
@Composable
fun SurferComponentPreview(
    darkTheme: Boolean = false,
    containerStyle: SurferContainerStyle = SurferContainerStyle.Card,
    content: @Composable () -> Unit,
) {
    AppTheme(darkTheme = darkTheme, containerStyle = containerStyle) {
        Box(
            modifier = Modifier
                .background(AppTheme.materialColors.surface),
        ) {
            content()
        }
    }
}
