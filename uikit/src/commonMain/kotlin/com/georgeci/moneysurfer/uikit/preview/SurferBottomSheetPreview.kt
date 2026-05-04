package com.georgeci.moneysurfer.uikit.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import com.georgeci.moneysurfer.uikit.theme.SurferContainerStyle

/**
 * Preview harness for composables that ship inside a `ModalBottomSheet` at runtime.
 *
 * Mimics the live sheet's chrome (top-rounded surfaceContainerLow shell + 32×4 drag handle)
 * inside a normal `@Preview` canvas. Use this instead of wrapping previews in
 * `ModalBottomSheet` directly — that component is `Popup`-based and renders blank in some
 * preview tooling versions, which is exactly what we want to avoid.
 *
 * Doesn't change runtime behavior: the real screen still uses `ModalBottomSheet` via
 * `BottomSheetSceneStrategy`. This is preview-only.
 */
@Composable
fun SurferBottomSheetPreview(
    darkTheme: Boolean = false,
    containerStyle: SurferContainerStyle = SurferContainerStyle.Card,
    content: @Composable () -> Unit,
) {
    AppTheme(darkTheme = darkTheme, containerStyle = containerStyle) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.materialColors.scrim.copy(alpha = 0.32f))
                .padding(top = 48.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(AppTheme.materialColors.surfaceContainerLow),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 4.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(AppTheme.materialColors.onSurfaceVariant.copy(alpha = 0.4f)),
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    content()
                }
            }
        }
    }
}
