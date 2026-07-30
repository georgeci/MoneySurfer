@file:Suppress("detekt.Filename")

package com.georgeci.moneysurfer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.georgeci.moneysurfer.data.remote.initializeDesktopFirebase
import com.georgeci.moneysurfer.di.initKoin
import com.georgeci.moneysurfer.di.onlineWiring

fun main() {
    // Android/iOS get the default FirebaseApp from their platform config files; the JVM host has
    // none, so it is built here. Must precede initKoin — the online graph resolves Firestore while
    // it is being built, and an uninitialized FirebaseApp fails the whole graph.
    initializeDesktopFirebase()
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
