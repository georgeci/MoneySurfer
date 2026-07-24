package com.georgeci.moneysurfer.uikit.theme

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
        var context = view.context
        while (context is ContextWrapper && context !is Activity) {
            context = context.baseContext
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
