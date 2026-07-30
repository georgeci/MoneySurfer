@file:Suppress("detekt.Filename")

package com.georgeci.moneysurfer

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.georgeci.moneysurfer.data.remote.initializeDesktopFirebase
import com.georgeci.moneysurfer.di.initKoin
import com.georgeci.moneysurfer.di.onlineWiring
import java.awt.Dimension

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
            state = rememberWindowState(size = DefaultWindowSize),
            title = "MoneySurfer",
        ) {
            // Swing has no minimum-size concept in Compose's window state, so it is set on the
            // AWT window itself. Below this the app is a phone layout in a desktop frame, which
            // is a valid thing to see but not a sane thing to open at, and the drawer plus two
            // panes stop fitting well before it.
            window.minimumSize = MinimumWindowSize
            App()
        }
    }
}

/**
 * The design's desktop canvas (`md/tablet-desktop-responsive.md`, issue #392) — also the width
 * `ScreenshotWidth.Large` captures at, so what the host opens at is what the wide references show.
 * Comfortably inside `SurferWindowSize.Large`, which is the only width the inline add panel gets a
 * third column at.
 */
private val DefaultWindowSize = DpSize(width = 1360.dp, height = 880.dp)

/**
 * Floor for a manual resize, in AWT pixels rather than dp: wide enough to stay in
 * `SurferWindowSize.Medium` (600 dp at 1x) with room for the window frame, so a drag cannot leave
 * the developer host in a layout no phone or tablet would ever produce.
 */
private val MinimumWindowSize = Dimension(720, 560)
