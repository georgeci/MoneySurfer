package com.georgeci.moneysurfer.offline

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.georgeci.moneysurfer.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // Both bars fully transparent. `enableEdgeToEdge()`'s default navigation-bar style is
        // `auto(lightScrim, darkScrim)`, which paints a translucent white scrim over the bar in
        // light mode — visible as a lighter band under the app's own background, most obviously
        // on the green pre-auth screens. Icon tints are per screen, via ConfigureSystemBars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            this.window.isNavigationBarContrastEnforced = false
        }
        setContent {
            App()
        }
    }
}
