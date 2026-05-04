package com.georgeci.moneysurfer

import androidx.compose.ui.window.ComposeUIViewController
import com.georgeci.moneysurfer.di.initKoin

fun MainViewController() = ComposeUIViewController(
    configure = {
        DebugErrors.installKermitWriter()
        initKoin()
    },
) {
    DebugErrorOverlay {
        App()
    }
}
