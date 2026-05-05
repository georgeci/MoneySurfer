package com.georgeci.moneysurfer

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat

@Composable
actual fun StatusBarAppearance(isDark: Boolean) {
    val activity = LocalActivity.current ?: return
    SideEffect {
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightStatusBars = !isDark

        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            .isAppearanceLightNavigationBars = !isDark
    }
}
