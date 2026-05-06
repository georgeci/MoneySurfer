package com.georgeci.moneysurfer

import androidx.compose.ui.window.ComposeUIViewController
import com.georgeci.moneysurfer.di.initKoin
import com.georgeci.moneysurfer.di.onlineWiring

fun MainViewController() = ComposeUIViewController(
    configure = {
        DebugErrors.installKermitWriter()
        initKoin(extraModules = onlineWiring)
    },
) {
    DebugErrorOverlay {
        App()
    }
}
