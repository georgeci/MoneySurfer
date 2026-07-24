package com.georgeci.moneysurfer.uikit.theme

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Deep enough for any real `ContextWrapper` chain; a cycle would otherwise spin forever. */
private const val MaxContextUnwrapDepth = 20

@Composable
actual fun ConfigureSystemBars(
    darkStatusBarBackground: Boolean,
    darkNavigationBarBackground: Boolean,
) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view, darkStatusBarBackground, darkNavigationBarBackground) {
        // `LocalView.current.context` is usually a `ContextThemeWrapper` around the activity, so
        // unwrap it rather than casting — a failed cast would silently leave the bars untouched.
        // The depth is bounded so a wrapper that returns itself as `baseContext` can't hang.
        var context = view.context
        var depth = 0
        while (context is ContextWrapper && context !is Activity && depth < MaxContextUnwrapDepth) {
            context = context.baseContext
            depth++
        }
        val window = (context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val previousStatusBars = controller.isAppearanceLightStatusBars
        val previousNavigationBars = controller.isAppearanceLightNavigationBars
        // Light icons over a dark backdrop, and vice versa.
        controller.isAppearanceLightStatusBars = !darkStatusBarBackground
        controller.isAppearanceLightNavigationBars = !darkNavigationBarBackground
        onDispose {
            controller.isAppearanceLightStatusBars = previousStatusBars
            controller.isAppearanceLightNavigationBars = previousNavigationBars
        }
    }
}
