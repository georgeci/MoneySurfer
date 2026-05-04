package com.georgeci.moneysurfer.uikit.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
actual fun ConfigureSystemBars(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        // Light icons when the underlying surface is dark, and vice versa.
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
//        window.statusBarColor = Color.White.toArgb()

        WindowInsetsControllerCompat(window, view).apply {
            isAppearanceLightStatusBars = true // 👈 обязательно
        }
    }
}
