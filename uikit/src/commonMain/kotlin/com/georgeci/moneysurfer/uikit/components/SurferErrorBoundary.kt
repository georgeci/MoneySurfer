package com.georgeci.moneysurfer.uikit.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import com.georgeci.moneysurfer.uikit.preview.SurferComponentPreview
import com.georgeci.moneysurfer.uikit.theme.AppTheme
import moneysurfer.uikit.generated.resources.Res
import moneysurfer.uikit.generated.resources.uikit_error_boundary_subtitle
import moneysurfer.uikit.generated.resources.uikit_error_boundary_title
import moneysurfer.uikit.generated.resources.uikit_retry
import org.jetbrains.compose.resources.stringResource

/**
 * App-level fallback for failures no screen handled itself: while [hasError] is true a
 * full-bleed [SurferErrorState] with a retry action covers [content] (issue #78).
 *
 * The content stays composed underneath rather than being torn down and rebuilt, so a
 * transient failure does not cost the user their navigation back stack — dismissing via
 * [onRetry] returns them exactly where they were, and the screen's own reload runs on the
 * data it already observes.
 */
@Composable
fun SurferErrorBoundary(
    hasError: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()

        if (hasError) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(SurferErrorBoundaryTestTags.Fallback),
                color = AppTheme.materialColors.surface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SurferErrorState(
                        title = stringResource(Res.string.uikit_error_boundary_title),
                        subtitle = stringResource(Res.string.uikit_error_boundary_subtitle),
                        retryLabel = stringResource(Res.string.uikit_retry),
                        onRetry = onRetry,
                    )
                }
            }
        }
    }
}

object SurferErrorBoundaryTestTags {
    const val Fallback = "errorBoundary:fallback"
}

@Preview
@Composable
private fun SurferErrorBoundaryPreview() {
    SurferComponentPreview {
        SurferErrorBoundary(hasError = true, onRetry = {}) {}
    }
}
