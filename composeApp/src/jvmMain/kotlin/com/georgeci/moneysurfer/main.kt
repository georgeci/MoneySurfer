@file:Suppress("detekt.Filename")

package com.georgeci.moneysurfer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.georgeci.moneysurfer.di.initKoin
import com.georgeci.moneysurfer.di.onlineWiring

fun main() {
    // Desktop is a developer-only host (no shipped release), so keep full logging.
    initKoin(isDebug = true, extraModules = onlineWiring)
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "MoneySurfer",
        ) {
            App()
        }
    }
}
